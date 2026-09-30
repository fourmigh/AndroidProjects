# translate-engine

ShotOCR 的离线中英双向翻译引擎。基于 ONNX 版的 OPUS-MT，模型随 APK 打包，**完全离线**（App 无 `INTERNET` 权限）。

## 目录结构

```
translate-engine/
├── build.gradle.kts
├── download-models.ps1          # 下载模型到 assets（488MB）
└── src/main/
    ├── assets/translate/        # 未纳入 git（见 .gitignore），需先跑下载脚本
    │   ├── zh-en/               # 中 -> 英
    │   └── en-zh/               # 英 -> 中
    └── java/org/caojun/shotocr/translate/
        ├── TranslateEngine.kt   # 对外入口（mmap 加载 + 单方向缓存）
        ├── MarianTranslator.kt  # beam search 解码 + 输入/输出加工
        ├── TranslationDictionary.kt # 内置分类名中英词典（直译）
        └── TranslateTokenizer.kt# DJL HuggingFaceTokenizer 封装 + tokenizer.json 修补
```

## 首次准备

模型资产未提交（488MB）。克隆后必须先下载：

```powershell
cd translate-engine
.\download-models.ps1     # 走 hf-mirror.com 镜像；约 90 秒
```

**构建不会因缺模型而失败**，但运行时会因找不到 assets 而报错。

## 模型来源

| 方向 | 仓库 | 文件 |
|---|---|---|
| zh→en | `onnx-community/opus-mt-zh-en` | `onnx/encoder_model_int8.onnx`、`onnx/decoder_model_merged_int8.onnx` |
| en→zh | `onnx-community/opus-mt-en-zh` | 同上 |

- 量化：int8；每方向约 235MB（encoder 50MB + decoder 184MB）
- `decoder_model_merged` 变体同时支持带/不带 KV cache，用 `use_cache_branch` 布尔输入切换
- 参数：`d_model=512`、6 层、8 头、`vocab_size=65001`、`decoder_start_token_id=65000`（= pad）、`eos_token_id=0`

## 实现要点

### 1. 模型加载：mmap 零拷贝

235MB 的模型**不能**用 `readBytes()`（会 OOM）。做法：

```kotlin
assets.openFd(path)                     // 要求该扩展名已 noCompress
  → FileInputStream(...).channel.map(...)   // MappedByteBuffer，不占 Java 堆
  → OrtSession.createSession(buffer, options)  // ORT 内部零拷贝
```

配套要求：
- `translate-ui`/`translate-engine` 的 `androidResources.noCompress` 加 `onnx`（**且必须同时配在 `:app`**，因为压缩与否由打 APK 的模块决定）
- 必须持有 `MappedByteBuffer` 强引用到 session 关闭，否则 GC 会 unmap 导致 native 崩溃

### 2. 解码：不使用 KV cache

实测 `use_cache_branch=true`（复用 past）在长序列上会触发
`encoder_attn/Reshape_4` 的 Reshape 错误，故**每步都用 `use_cache_branch=false` + 完整已生成序列**。
分类名很短，性能足够。

注意 merged decoder 的 24 个 `past_key_values.*` 是**必填** graph input（无默认初值），
即使不用 cache 也必须传入零长度张量：

```
past_key_values.N.decoder.key/value  → [1, 8, 0, 64]
past_key_values.N.encoder.key/value  → [1, 8, 0, 64]
```

### 3. beam search

自行实现（ONNX Runtime Android 没有 BeamSearch contrib op）：

- 搜索窗口 = **整个词表**（每步对全部 65001 个 logits 取全局 top-`2*num_beams`）
- 生成中统一用**原始累加对数概率**比较；**只有最终挑选答案时才用长度归一化**（`score / tokens.size`）与 transformers `BeamSearchScorer` 一致
- early-stop：`finished` 满 `num_beams` 且「最差已完成 ≥ 最好活跃」即收敛
- `max_length` 上限截断到 24 步（分类名足够），另有 15 秒墙钟兜底抛 `TranslateException`
- 屏蔽 `bad_words_ids`（pad 65000）

### 4. tokenizer.json 需要打补丁

onnx-community 导出的 `tokenizer.json` 中：

```json
"normalizer": { "type": "Precompiled", "precompiled_charsmap": null }
```

`precompiled_charsmap` 被删成 `null`，任何版本的新 tokenizers 加载它都会直接 panic。
`TranslateTokenizer` 在加载前用正则把这个节点替换成 `"normalizer": null`。

### 5. ⚠️ 单个英文单词会退化重复（重要）

**`opus-mt-en-zh` 对单个短英文词输入存在固有退化**，会无限重复输出：

```
Learning -> 学习学习学习学习
Coffee   -> 咖啡咖啡咖啡 咖啡咖啡咖啡咖啡
Fuel     -> 燃料燃料燃料燃料燃料燃料燃料燃料燃料燃料
```

**这不是本项目的 bug**：用官方 PyTorch 权重 + HF 官方 `generate()` 跑同样输入，
得到完全相同的输出。原因是该模型为**句级翻译**训练，没见过"单词 + `</s>`"这种模式。

同时 zh→en 方向正常（中文源词是常见训练模式），只有 en→zh 受影响。

**解决方案：自适应「冒号诱导」**

实测（38 条真实分类名）：

| 策略 | 非退化率 | 备注 |
|---|---|---|
| 原样输入 | 26/30 | 4 条退化 |
| 全部加冒号 | 30/30 | 多词短语混入噪声，如 `互联网电话( P)` |
| **仅单词加冒号（采用）** | **38/38** | 零退化、零噪声 |

`Word:` 是词典/词条形态，模型会走"术语翻译"通路而非自由造句。实现见
`MarianTranslator.translate()`：

```kotlin
val needsHint = SINGLE_ENGLISH_WORD.matches(trimmed)        // 纯单个英文单词
val modelInput = if (needsHint) "$trimmed:" else trimmed    // 仅内部加工
...
return if (needsHint) decoded.stripHintSuffix() else decoded // 剥离尾部冒号
```

**用户完全感知不到这层加工**：输入框内容不变，返回结果也不含冒号。

判定正则 `[A-Za-z][A-Za-z'\-]*` 全匹配，因此多词短语（`Internet & Phone`）、
含 `&`/空格/中文的输入都不会被改写。

### 6. 本地词典优先（直译）

内置分类名是固定的，`TranslationDictionary` 里维护了 30 条中英对照。
`TranslateEngine.translate()` **先查词典，命中则直接返回、完全不加载模型**：

- 更快：省掉首次 3~10 秒的模型加载
- 更准：避免模型给出「公用事业」这类不贴合分类语义的结果
- 未命中才回退到 ONNX 翻译

`translate-ui` 同样先同步查表，命中时不会出现「翻译中…」闪烁。

> ⚠️ 维护提示：新增/修改内置分类名时，需同步更新 `TranslationDictionary.dictionary`
> （对应 `database/src/main/res/values{,-en}/strings.xml` 的 `category_*`）。

### 7. 已知质量限制

模型词汇选择能力有限（与 HF 官方一致），以下属**模型问题，非 bug**：

| 输入 | 输出 | 期望 |
|---|---|---|
| `Groceries` | 杂草 | 生鲜/杂货 |
| `Fitness` | 适合度 | 健身 |
| `Bonus` | 回报 | 奖金 |
| `Utility` | 工具 | 水电 |

若要改善需换更大模型（如 `Helsinki-NLP/opus-mt-tc-big-en-zh`），代价是重新量化与打包。

## 依赖

| 坐标 | 版本 | 说明 |
|---|---|---|
| `com.microsoft.onnxruntime:onnxruntime-android` | 1.30.0 | 推理 |
| `ai.djl.huggingface:tokenizers` | **0.33.0** | 分词（**不可升级**） |
| `ai.djl.android:tokenizer-native` | **0.33.0** | Android native（**不可升级**） |

**为什么钉在 0.33.0**：`tokenizer-native` 只发布过 0.33.0 这一个版本。DJL 的
`LibUtils` 在 Android 上走 `System.loadLibrary("djl_tokenizer")` 分支，
必须搭配该 AAR 提供的 `libdjl_tokenizer.so`（含 arm64-v8a / armeabi-v7a，minSdk 21）。

`tokenizers` jar 里还带 ~17MB 桌面 native（linux/osx/win），已通过
`packaging.resources.excludes += "native/**"` 排除，避免白塞进 APK。

DJL 加载时会向 `publish.djl.ai` 发遥测，但 App **无 `INTERNET` 权限**，请求必然失败且不外传数据。

## 诊断日志

`MarianTranslator` 与 `TranslateEngine` 都带 `TAG = "TranslateEngine"` 的日志
（`VERBOSE = true`）。包含：

- 加载各阶段耗时：`load[en-zh] tokenizer=?ms mmap=?ms encoderSession=?ms decoderSession=?ms total=?ms`
- 解码过程：`step=N active=? finished=? bestActive=? stepMs=? totalMs=?`
- 首步 logits 体检：`step0 logits: nan=? top3=...`
- 收敛与结果：`converged at step=N` / `decode done: steps=? tokens=? totalMs=? ids=...`

抓取：

```
adb logcat -c
adb logcat -s TranslateEngine:d
```

**注意**：部分定制 ROM 会关闭 logcat 输出（`logcat -d` 返回 0 行），
此时需在开发者选项里打开日志开关。

调试完毕后可把 `VERBOSE` 改为 `false`。

## 供其他模块使用

纯引擎，不含 UI（可翻译输入框在 **`translate-ui`** 模块）。

```kotlin
TranslateEngine.initialize(context)                 // 一次即可
val zh = TranslateEngine.translate("Learning", TranslateEngine.LANG_EN, TranslateEngine.LANG_ZH)
```

- 内部自动切 `Dispatchers.IO`
- 单方向缓存（切换方向会释放上一个模型）
- 失败抛 `TranslateException`

## 踩坑记录

1. **direct buffer 字节序**：ORT 的 `createTensor(Buffer, shape)` 对 direct buffer 零拷贝，
   要求 `ByteOrder.nativeOrder()`。用默认的 BIG_ENDIAN 会让所有输入张量数值被字节翻转
   （症状：`Gather` 报 `idx=504403158265495312` 这类 7×2⁵⁶ 的离谱数值）。
   堆 buffer（如 `FloatBuffer.wrap`）走复制路径，不受影响 —— 所以这个坑只在优化后出现。
2. **AGP 9 严格校验**：声明了 `consumerProguardFiles(...)` 但文件不存在会直接构建失败
   （旧版 AGP 静默忽略）。
3. **`:app:assembleDebug` ≠ 全项目构建**：前者不触发各 library 自己的
   `mergeDebugConsumerProguardFiles` 等任务，必须先跑全项目 `assembleDebug` 才能发现这类问题。
4. **`.bat` 脚本不要写中文注释**：GBK 控制台下 UTF-8 多字节序列会被误解析、吞掉行尾，
   导致脚本在最开头就语法崩溃（无任何输出、退出码 255）。
5. **APK 体积与 ABI**：`:app` 必须显式设置 `abiFilters += listOf("arm64-v8a", "armeabi-v7a")`，
   否则 DJL 会带上 x86/x86_64 的 `libdjl_tokenizer.so`。加上后 APK 从 651MB 降到 570MB。
   代价：**x86 模拟器无法运行**（arm64 真机不受影响）。
