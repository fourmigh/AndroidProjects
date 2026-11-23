# OnDeviceTranslateHub · 实施文档

> **状态（2026-11-17）**：H1–H5 已实现并通过构建，release APK **19.4 MB**；
> 未在真机验证（当前环境 `adb devices` 为空），H6 的语种核实与真机性能数据待补。

> 目标：新增一个**约 20 MB 的翻译 provider App**，内置「可装进手机的翻译专用模型」目录，
> 模型由用户在首次使用时按需下载，可随时删除以回收空间。
>
> 与现有 `OnDeviceTranslate`（526 MB 内嵌 Marian，中英）**并存**：后者继续承担「零下载、极速、完全离线」，
> 本工程承担「多语种 / 高质量 / 按需下载」。

---

## 1. 为什么不是「一个模型一个 APK」

| 方案 | APK 体积 | 加新模型 | 安装成本 | 结论 |
|---|---|---|---|---|
| 每模型一个 APK（模型进 assets） | 0.53 ~ 1.15 GB | 新建工程 + 发版 | `adb install` 需 2.5 GB 空闲，首次还要复制到 filesDir | ❌ |
| **单 APK + 运行时下载** | **~15 MB** | **只改 catalog.json** | 下载一次，之后完全离线 | ✅ |

单 APK 还带来两个额外收益：

- 不需要引入 `onnxruntime`（只跑 GGUF），省掉 10+ MB native 库
- 用户能按机型只下 0.53 GB，而不是被迫装 1.1 GB

---

## 2. 架构总览

```
ShotOCR / AutoOcrTask
        │
        │ ① 枚举 provider（intent action）
        │ ② getCapability() 读语种
        │ ③ translate(text, from, to)
        ▼
OnDeviceTranslateHub/api（契约模块，包名 org.caojun.ai.translate.provider）
        │
        ├── OnDeviceTranslate          Marian     2 语种     0 下载（不动）
        └── OnDeviceTranslateHub       Hub        动态        按需下载
                     │
                     └── engine-llama（llama.cpp JNI，所有 GGUF 共用）
```

`OnDeviceTranslateHub` 同时是 **UI App** 和 **AIDL provider**：

| 角色 | 能力 |
|---|---|
| 模型管理器 | 内置目录、下载、校验、删除、切换、占用统计 |
| 翻译 provider | 已装模型的语种并集、按方向选模型、翻译 |

---

## 3. 工程结构

```
OnDeviceTranslateHub/
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── api/                        # 契约：AIDL + Client + Router + 语种目录
├── engine-core/                # 目录、下载、校验、存储、prompt 拼装、调度
├── engine-llama/               # llama.cpp JNI + 采样 + chat template
├── app/                        # Compose UI + Service   org.caojun.ai.translate.hub
├── tools/fetch-llama.ps1       # 拉取 llama.cpp 源码（不入库）
└── third_party/llama.cpp       # 由脚本生成，.gitignore 忽略
```

客户端通过 `projectDir` 直接引用 `api/`（与仓库现有 `:translate-api` 模式一致）：
`ShotOCR/settings.gradle.kts` 里 `include(":translate-provider-api")` 指向本工程 `api/`。

- 单 app 模块 → `install.bat` / `install OnDeviceTranslateHub` **零改动**
- 签名沿用 `keystore/shared.p12`（`../keystore/keystore.properties`）
- `minSdk 26 / targetSdk 36 / compileSdk 37`，只保留 `arm64-v8a`

APK 实测：release **19.4 MB**（debug 37.3 MB，未 strip），其中 `libhubllama.so` 压缩前 40.9 MB。
没有引入 `onnxruntime`。

---

## 4. 模型目录（catalog）

### 4.1 位置与发布方式

| 位置 | 说明 |
|---|---|
| `app/src/main/assets/catalog.json` | 随 APK 发布，只读，唯一运行时真相 |
| 可选远程 | 静态地址 + 签名校验，仅用于「不重新发版就加模型」，第一版不做 |

### 4.2 完整 schema

```json
{
  "catalogVersion": 3,
  "generatedAt": "2026-11-17",
  "models": [
    {
      "id": "plamo3-translate-0.8b-q4km",
      "name": "PLaMo 3 Translate 0.8B",
      "vendor": "Preferred Networks",
      "params": "0.89B",
      "summary": "53 语种，日语强，中端机首选",

      "langs": ["zh-Hans", "zh-Hant", "en", "ja", "ko"],
      "dialects": [],
      "langStatus": { "zh-Hant": "supported" },
      "textOnlyLangs": ["zh-Hant", "ja", "ko"],

      "sizeBytes": 566231040,
      "minRamMb": 2048,
      "quality": 78,
      "speedClass": "fast",
      "longText": true,
      "maxInputChars": 8000,
      "recommended": true,

      "license": {
        "name": "PLaMo Community License",
        "summary": "个人/学术免费；商用需登记，年营收 ≤ 10 亿日元免费",
        "requiresAccept": true,
        "url": "https://huggingface.co/pfnet/plamo-3-translate-2b-GGUF"
      },

      "files": [
        {
          "name": "model.gguf",
          "sizeBytes": 566231040,
          "sha256": "0000...",
          "urls": [
            "https://hf-mirror.com/pfnet/plamo-3-translate-2b-GGUF/resolve/main/plamo-3-translate-0.8b-Q4_K_M.gguf",
            "https://huggingface.co/pfnet/plamo-3-translate-2b-GGUF/resolve/main/plamo-3-translate-0.8b-Q4_K_M.gguf"
          ]
        }
      ],

      "prompt": {
        "style": "plamo_json",
        "params": { "form": "plain" }
      },
      "sampling": { "temp": 0.0, "topK": 1, "topP": 1.0, "repPenalty": 1.0 },
      "postprocess": ["stripQuotes"],
      "stopTokens": ["</s>"],

      "engineReq": {
        "llamaArch": ["llama"],
        "minEngine": 1
      },

      "engineCheck": {
        "architecture": "llama",
        "chatTemplateSha256": "0000..."
      },

      "langsSource": "https://hf-mirror.com/api/models/pfnet/plamo-3-translate-2b-GGUF",
      "verifiedAt": "2026-11-17",
      "available": true
    }
  ]
}
```

字段约束：

| 字段 | 约束 | 校验时机 |
|---|---|---|
| `langs` | 每项必须在 `LanguageCatalog` 内 | 构建期单测 |
| `dialects` | ⊆ `langs` | 构建期单测 |
| `textOnlyLangs` | ⊆ `langs`，由语种矩阵 OCR 列**自动生成**，禁止手填 | 构建期单测 |
| `langCount` | 不手写，UI 用 `langs.length` | — |
| `engineReq.llamaArch` | 必须是引擎已编译支持的 arch | 运行时加载前 |
| `engineCheck` | 加载后比对，不符 → `NOT_READY` | 运行时 |
| `verifiedAt` | 超过 180 天构建期告警 | 构建期 |
| `available` | `false` 表示「目录里能看到但不能下」 | UI |

### 4.3 第一版收录的模型

| 顺序 | 模型 | 档位 | 体积 | 语种 | 定位 |
|---|---|---|---|---|---|
| 1 | PLaMo 3 Translate 0.8B Q4_K_M | 轻量 | 0.53 GB | 53 | 默认推荐，中端机 |
| 2 | Hy-MT2-1.8B Q4_K_M | 中英强 | ~1.1 GB | 38 | 中英质量线 + 粤语/民汉 |
| 3 | Index-Translate-2B Q2_K-QAT | 广覆盖 | 0.78 GB | 150 | 小语种兜底 |

**第一版不收录**：

- Hy-MT2 的 1.25bit（440 MB）/ 2bit（574 MB）：依赖未合并的 STQ kernel（llama.cpp PR #22836）。catalog 里先留条目、`available: false`
- TranslateGemma 4B/12B/27B：Q4 2.97 GB，实测 RSS ~5 GB，手机不可行；且需接受 Gemma Terms + HF token
- NLLB-200 / SeamlessM4T：**CC-BY-NC**，对外分发有许可风险；且需要 CTranslate2/ORT 第二个推理引擎
- 一切 7B+（Hunyuan-MT-7B、Seed-X-7B、Tower-7B、ALMA-7B）：4–5 GB
- Marian ONNX（235 MB）：与现有 `OnDeviceTranslate` 功能重叠，不提供

---

## 5. 语种覆盖矩阵（catalog 的权威来源）

### 5.1 标记说明

- `✅` 官方语种表明确列出
- `◐` 大语种，大概率覆盖，**H6 阶段核实**
- `？` 需核实
- `❌` 官方确认不含

Hy-MT2 与 Hy-MT1.5 语种表一致：**33 个标准语种（含繁体中文）+ 5 个民汉/方言 = 38 项**。

### 5.2 主表

| 规范码 | 名称 | 文字 | OCR 直读 | Hy-MT2/1.5 | PLaMo 3 | Index-Translate | TranslateGemma |
|---|---|---|---|:--:|:--:|:--:|:--:|
| `zh-Hans` | 简体中文 | Hans | ✅ | ✅ | ◐ | ✅ | ◐ |
| `zh-Hant` | 繁体中文 | Hant | ✅ | ✅ | ◐ | ？ | ◐ |
| `yue` | 粤语（书面） | Hans/Hant | ✅ | ✅ | ❌ | ？ | ❌ |
| `en` | 英语 | Latn | ✅ | ✅ | ✅ | ✅ | ✅ |
| `ja` | 日语 | Jpan | ⚠️ | ✅ | ✅ | ✅ | ✅ |
| `ko` | 韩语 | Kore | ❌ | ✅ | ✅ | ✅ | ✅ |
| `fr` | 法语 | Latn | ❌ | ✅ | ✅ | ✅ | ✅ |
| `de` | 德语 | Latn | ❌ | ✅ | ✅ | ✅ | ✅ |
| `es` | 西班牙语 | Latn | ❌ | ✅ | ✅ | ✅ | ✅ |
| `pt` | 葡萄牙语 | Latn | ❌ | ✅ | ✅ | ✅ | ✅ |
| `it` | 意大利语 | Latn | ❌ | ✅ | ✅ | ✅ | ✅ |
| `ru` | 俄语 | Cyrl | ❌ | ✅ | ✅ | ✅ | ✅ |
| `uk` | 乌克兰语 | Cyrl | ❌ | ✅ | ◐ | ？ | ◐ |
| `pl` | 波兰语 | Latn | ❌ | ✅ | ◐ | ✅ | ✅ |
| `cs` | 捷克语 | Latn | ❌ | ✅ | ◐ | ？ | ◐ |
| `nl` | 荷兰语 | Latn | ❌ | ✅ | ◐ | ✅ | ◐ |
| `tr` | 土耳其语 | Latn | ❌ | ✅ | ◐ | ✅ | ✅ |
| `ar` | 阿拉伯语 | Arab | ❌ | ✅ | ✅ | ✅ | ✅ |
| `fa` | 波斯语 | Arab | ❌ | ✅ | ◐ | ？ | ◐ |
| `ur` | 乌尔都语 | Arab | ❌ | ✅ | ？ | ？ | ◐ |
| `he` | 希伯来语 | Hebr | ❌ | ✅ | ◐ | ？ | ◐ |
| `hi` | 印地语 | Deva | ❌ | ✅ | ◐ | ✅ | ✅ |
| `bn` | 孟加拉语 | Beng | ❌ | ✅ | ？ | ？ | ◐ |
| `gu` | 古吉拉特语 | Gujr | ❌ | ✅ | ？ | ？ | ？ |
| `mr` | 马拉地语 | Deva | ❌ | ✅ | ？ | ？ | ？ |
| `te` | 泰卢固语 | Telu | ❌ | ✅ | ？ | ？ | ？ |
| `ta` | 泰米尔语 | Taml | ❌ | ✅ | ？ | ？ | ◐ |
| `th` | 泰语 | Thai | ❌ | ✅ | ✅ | ✅ | ✅ |
| `vi` | 越南语 | Latn | ❌ | ✅ | ✅ | ✅ | ✅ |
| `id` | 印尼语 | Latn | ❌ | ✅ | ✅ | ✅ | ✅ |
| `ms` | 马来语 | Latn | ❌ | ✅ | ◐ | ✅ | ◐ |
| `fil` | 菲律宾语 | Latn | ❌ | ✅ | ？ | ✅ | ？ |
| `ceb` | 宿务语 | Latn | ❌ | ❌ | ✅ | ？ | ❌ |
| `jv` | 爪哇语 | Latn | ❌ | ❌ | ✅ | ？ | ❌ |
| `uz` | 乌兹别克语 | Latn | ❌ | ❌ | ✅ | ？ | ？ |
| `km` | 高棉语 | Khmr | ❌ | ✅ | ？ | ？ | ◐ |
| `my` | 缅甸语 | Mymr | ❌ | ✅ | ？ | ？ | ◐ |
| `bo` | 藏语 | Tibt | ❌ | ✅ | ❌ | ？ | ❌ |
| `kk` | 哈萨克语 | Cyrl | ❌ | ✅ | ✅ | ？ | ◐ |
| `mn` | 蒙古语 | Cyrl/Mong | ❌ | ✅ | ？ | ？ | ❌ |
| `ug` | 维吾尔语 | Arab | ❌ | ✅ | ❌ | ？ | ❌ |

> **OCR 列的结论**：现有 `OnDeviceOcr` 是 RapidOCR PP-OCRv4 中英模型，38 项里只有中文（简/繁）和英文能截图直读。
> 其余语种（含粤语以外的全部）**只能手动输入/粘贴**，UI 必须如实标注。
> `ja` 标 ⚠️：能认出部分汉字但认不出假名，结果残缺，建议同样按「仅手动输入」处理。

### 5.3 方言专表

| 变体 | 规范码 | 类型 | Hy-MT2 | 其他模型 | 端到端可行性 |
|---|---|---|---|:--:|---|
| 普通话 | `zh-Hans` | 标准语 | ✅ | 全部 | ✅ OCR + 翻译 |
| 繁体 | `zh-Hant` | 文字变体 | ✅ | OpenCC 本地转 | ✅ 建议 OpenCC |
| 粤语 | `yue` | 方言 | ✅ | ❌ | ✅ 汉字可 OCR |
| 藏语 | `bo` | 民汉 | ✅ | ❌ | ❌ 仅手动输入 |
| 维吾尔语 | `ug` | 民汉 | ✅ | ❌ | ❌ 仅手动输入 |
| 蒙古语 | `mn` | 民汉 | ✅ | ❌ | ❌ 仅手动输入 |
| 哈萨克语 | `kk` | 民汉 | ✅ | 待核实 | ❌ 仅手动输入 |
| 上海话/吴语 | — | 方言 | ❌ | ❌ | ❌ 无公开文本 MT |
| 闽南语/客家话 | — | 方言 | ❌ | ❌ | ❌ 无公开文本 MT |
| 四川话/东北话 | — | 方言 | ❌ | ❌ | ❌ 仅口语，无书面 MT |

**结论**：方言支持 = 粤语 + 4 个民汉语种，且只有 Hy-MT2 一家提供。

### 5.4 超出 38 项的增量

| 模型 | 语种数 | 已知增量 | 处理 |
|---|---|---|---|
| PLaMo 3 | 53 | 已确认含 `ceb` 宿务语、`jv` 爪哇语、`uz` 乌兹别克语、`kk` 哈萨克语，不含 `zh-Hant`/`yue`/`bo`/`ug`/`mn` | 已导入 catalog |
| TranslateGemma | 55 | WMT24++ 集合（北欧、东欧、部分非洲） | 不收录，仅记录 |
| Index-Translate | 150 | 已确认核心 22 项含 `ro` 罗马尼亚语、`sv` 瑞典语（Hy-MT 没有） | 已导入核心 22 项，其余待补 |
| Hy-MT2 | 38 | 无 | 主表即全集 |

### 5.5 语种码归一化

| 概念 | Hy-MT | PLaMo | Index | 规范码 |
|---|---|---|---|---|
| 简体中文 | `zh` | `zh` | `zh` / `zho_Hans` | `zh-Hans` |
| 繁体中文 | `zh-Hant` | ? | `zh_Hant` | `zh-Hant` |
| 粤语 | `yue` | — | ? | `yue` |
| 菲律宾语 | `tl` | `fil`? | `fil` | `fil` |
| 波斯语 | `fa` | `fa` | `pes` | `fa` |
| 希伯来语 | `he` | `he` | `heb` | `he` |
| 缅甸语 | `my` | `my` | `mya` | `my` |

规则：内部一律用 BCP-47 规范码；provider 差异由 `LanguageCatalog` 双向映射；映射失败**跳过该语种并记日志**（fail-closed，不能 fail-open）。

---

## 6. 语种表从哪来：三层设计

**不能运行时询问模型。** 语种表是跨进程契约，必须确定性、零延迟、可溯源。

```
L1 构建期生成   HF API cardData.language + 模型卡 README 语言表 + 技术报告附录
                   ↓ 脚本抽取 + 人工 diff
L2 随 APK 发布  assets/catalog.json         ← 唯一运行时真相（只读）
                   ↓
L3 运行时校验   GGUF metadata：architecture / chat_template hash / sha256
                不匹配 → ERROR_NOT_READY（宁可拒绝，不可瞎翻）
```

### 6.1 为什么不能问模型

1. 自回归模型没有「查询事实」的通道，只有续写；自述是生成结果，不是内部状态
2. 它会「宁多勿少」，把语料里出现过几万 token 的语言全列上，同时漏掉藏语/维吾尔语
3. 不可复现：采样参数、量化档不同，答案不同；而它要参与 Router 决策
4. 成本荒谬：客户端查语种本来是为了**避免**加载模型，自述却必须先加载 1.1 GB
5. 行业惯例：llama.cpp / vLLM / ORT GenAI 都读显式字段，没有一家读自述

### 6.2 模型文件里有什么

| GGUF metadata | 存在 | 用途 |
|---|:--:|---|
| `general.architecture` | ✅ | 校验：`hunyuan-dense` / `qwen35` / `llama` 是否与 catalog 一致 |
| `tokenizer.chat_template` | ✅ | 校验：模板变了说明模型升级，prompt 拼法可能失效 |
| `tokenizer.ggml.tokens` | ✅ | 只对 NLLB 那种带 `__zho_Hans__` 语言 token 的模型有用 |
| 支持语言列表 | ❌ | **不存在这个字段** |

结论：metadata 只能当**防伪校验**，不能当数据源。

### 6.3 生成脚本（H1 产出）

```
tools/gen-catalog.py
  ├── 读 models.json（人工维护的 repo 列表）
  ├── GET https://hf-mirror.com/api/models/<repo> → cardData.language
  ├── 解析 README 语种表（Hy-MT / Index 都有现成 markdown 表格）
  ├── 与技术报告附录交叉核对（Index Table 32/33）
  ├── 归一化到 BCP-47
  └── 输出 assets/catalog.json + docs/langs-matrix.md
```

**「问模型」的唯一合法用法**：`tools/probe-langs.py`，仅开发期诊断、用于发现漏项，结果不进 catalog.json。

---

## 7. 下载与存储

### 7.1 落盘

```
getExternalFilesDir("models")/
├── plamo3-translate-0.8b-q4km/
│   ├── model.gguf
│   └── manifest.json          # 安装完成标记 + 实际 sha256 + 安装时间
├── hymt2-1.8b-q4km.part/      # 下载中
└── ...
```

- 外部私有目录：不占 `/data` 配额，卸载自动清理，`adb pull/push` 可用
- `.part/` → 校验通过后原子 `rename` 到正式目录，半成品永远不会被当成可用模型
- 用户也可用 SAF（`ACTION_OPEN_DOCUMENT`）导入本地 gguf：复制 → 算 sha256 → 写 manifest → 标记为 `custom`

### 7.2 下载器：系统 `DownloadManager`

不自己写断点续传。`DownloadManager` 已有：断点续传、失败重试、系统通知、随进程存活、Wi-Fi 约束。

```kotlin
DownloadManager.Request(uri)
    .setDestinationInExternalFilesDir(ctx, null, "models/$id.part/$fileName")
    .setAllowedOverMetered(allowMetered)   // 默认 false
    .setNotificationVisibility(VISIBILITY_VISIBLE)
```

自己负责三件事：

1. **多源回退**：`hf-mirror.com` → `huggingface.co` → `modelscope`，一条失败换下一条（国内直连 HF 基本不通，镜像必须第一顺位）
2. **进度**：1 秒轮询 `Cursor`，写入 DataStore，UI 读
3. **校验**：完成后本地 SHA-256（1.1 GB 约 2–4 秒，IO 线程），不符删除重下

### 7.3 状态机

```
NOT_INSTALLED
    │ enqueue（检查空间 + 许可确认）
    ▼
QUEUED ──► DOWNLOADING ──► VERIFYING ──► READY
              │  ▲              │
    失败换源   │  └── 重试       │ sha256 不符
              ▼                 ▼
           FAILED ───────────► 删除并回 QUEUED（最多 3 次）

READY ──► DELETING ──► NOT_INSTALLED
           │（若正在加载 → 先 unload）
```

### 7.4 空间与删除

- 下载前：`可用空间 ≥ sizeBytes × 1.15`，不足时提示「还需 X MB」
- 删除：先 `unload`（若正在使用），再删目录；正在下载的模型禁止删除
- 首页显示「已占用 X GB / 可用 Y GB」

---

## 8. 运行时

### 8.1 单模型常驻

手机装不下两个 1.8B，所以**同一时刻只加载一个 GGUF**，切换 = `unload → load`。

| 项 | 值 |
|---|---|
| 1.8B Q4 常驻内存 | ~1.3–1.5 GB |
| 加载耗时 | 2–5 秒 |
| 空闲释放 | 10–15 分钟（比 Marian 的 5 分钟宽） |
| `onTrimMemory(RUNNING_LOW)` | 立即 unload |
| 内存门槛 | `minRamMb` 与 `ActivityManager.memoryClass` 比对，不足时禁用下载按钮 |

### 8.2 engine-llama JNI 接口

```c
typedef struct { float temp; int32_t top_k; float top_p; float rep_penalty; int32_t max_tokens; } hub_sampling;
typedef void (*hub_token_cb)(const char* piece, int32_t len, void* user);

int64_t hub_load(const char* path, int32_t n_ctx, int32_t n_threads, char** err);
int32_t hub_arch(const char* path, char* out, int32_t cap);              // 读 general.architecture
int32_t hub_chat_template(const char* path, char* out, int32_t cap);     // 读 tokenizer.chat_template
void    hub_reset(int64_t h);
int32_t hub_prefill(int64_t h, const int32_t* tokens, int32_t n);
int32_t hub_decode(int64_t h, const hub_sampling* s,
                   const int32_t* stop_tokens, int32_t n_stop,
                   hub_token_cb cb, void* user);
void    hub_unload(int64_t h);
```

编译：`third_party/llama.cpp` 固定 release tag，只编 `llama + ggml + ggml-base + ggml-cpu`；
`GGML_OPENMP=OFF`、`GGML_NATIVE=OFF`、关闭 CUDA/Vulkan/Metal、`abiFilters arm64-v8a`。

### 8.3 prompt 分派（catalog 的 `prompt.style`）

| style | 模型 | 实际拼法 |
|---|---|---|
| `plamo_json` | PLaMo 3 | chat template 变量 `{"target_lang":"ja","form":"plain"}` |
| `hymt` | Hy-MT2 / 1.5 | `Translate the following segment into {EnglishName}, without additional explanation.\n\n{text}` |
| `index_zh` | Index-Translate | `请将以下{源语言}文本翻译为{目标语言}，直接输出翻译结果，不要进行任何解释。\n\n{text}` |

`EnglishName` 来自 `LanguageCatalog`（如 `zh-Hans → Chinese`、`yue → Cantonese`），**不能直接用 code 传给模型**。

### 8.4 采样与后处理

| 模型 | 采样 | 后处理 |
|---|---|---|
| PLaMo 3 | `temp 0 / top_k 1` | `stripQuotes` |
| Hy-MT2 | `temp 0.7 / top_k 20 / top_p 0.6 / rep 1.05`（官方配方） | `stripPrefix` `stripQuotes` |
| Index-Translate | 贪心 | `stripFence`（去掉 markdown 围栏） |

短文本（分类名级别）建议对 Hy-MT2 做贪心 A/B，稳定性通常更好 —— 这是 H4 的验证项。

### 8.5 文字变体用 OpenCC，不用模型

- `zh-Hans ↔ zh-Hant` 走 OpenCC 本地转换（毫秒级、准确率高于模型）
- 这样 PLaMo / Index 也"支持繁体"，只是不能"翻译粤语"

---

## 9. 契约

### 9.1 AIDL（`TranslateProviderApi`）

```aidl
// org/caojun/ai/translate/provider/ITranslateProvider.aidl
package org.caojun.ai.translate.provider;

import org.caojun.ai.translate.api.ITranslateCallback;

interface ITranslateProvider {
    int    getApiVersion();
    String getCapability();     // JSON
    void   preload(String from, String to);
    void   translate(String text, String from, String to,
                     String optionsJson, ITranslateCallback callback);
}
```

旧的 `ITranslateService` 保持不动，Marian provider 继续只实现旧接口。

### 9.2 capability JSON

```json
{
  "apiVersion": 1,
  "providerId": "hub",
  "displayName": "离线翻译模型",
  "modelReady": true,
  "directionMode": "any",
  "languages": ["zh-Hans", "zh-Hant", "yue", "en", "ja"],
  "dialects": ["yue", "bo", "ug", "mn", "kk"],
  "textOnlyLangs": ["yue", "ja"],
  "installedModels": [
    {
      "modelId": "hymt2-1.8b-q4km",
      "displayName": "混元翻译 Hy-MT2-1.8B (Q4)",
      "languages": ["zh-Hans", "zh-Hant", "yue", "en"],
      "dialects": ["yue", "bo", "ug", "mn", "kk"],
      "quality": 92,
      "speedClass": "slow",
      "longText": true,
      "maxInputChars": 8000
    }
  ]
}
```

### 9.3 Router 规则

```
translate(from, to, policy)
  ├── 候选 = 已装模型 ∩ 覆盖 (from,to)
  ├── 排序：policy QUALITY → quality desc；SPEED → speedClass=fast 优先
  │         AUTO → 用户默认模型 > quality > 已预加载
  ├── 无候选 → ERROR_UNSUPPORTED（附「可安装的模型建议」）
  └── 有候选 → 选一个 → translate
```

**下载/删除不走 AIDL。** 客户端缺模型时用 Intent 打开 Hub 详情页：

```kotlin
Intent().setClassName("org.caojun.ai.translate.hub", "org.caojun.ai.translate.hub.ModelDetailActivity")
    .putExtra("modelId", "hymt2-1.8b-q4km")
```

---

## 10. UI（3 个页面，单 Activity + Compose）

| 页面 | 内容 |
|---|---|
| **模型商店**（首启即是） | 模型卡片：名称、体积、语种数、标签（`含方言/民汉`）、许可、下载/取消、进度；已安装时只显示「已安装」与「设为默认」 |
| **已安装** | 占用总量 / 可用空间、默认模型（RadioButton）、允许移动网络、删除（**二次确认**）、加载状态 |
| **翻译测试** | 源/目标语种（**同一行左右对齐，中间 `⇄` 交换**）、输入、**可编辑的译文框**、耗时；交换时译文会自动带回源文本（单向），方便直接回译 |

实现要点：

1. 首次启动**没有任何模型**，只有引导文案 + 3 张卡片（PLaMo 标「推荐」）
2. 语种选择器只列**当前已装模型支持的语种**（PLaMo 用户看不到粤语）
3. 选中非中英语种时提示「当前 OCR 不支持该文字，请手动输入」
4. 需登记的许可（PLaMo）下载前弹确认框
5. 下载完成自动进入「已安装」，可直接在测试页翻译
6. 客户端可 deep link 到某个模型：`caojunhub://model/<modelId>`，或
   `Intent().setClassName("org.caojun.ai.translate.hub", "...MainActivity").putExtra("modelId", id)`

---

## 11. 客户端改动

| 位置 | 改动 | 状态 |
|---|---|---|
| `ShotOCR/settings.gradle.kts` | 新增 `:translate-provider-api` → `../OnDeviceTranslateHub/api` | ✅ |
| `ShotOCR/translate-ui/build.gradle.kts` | `api(project(":translate-provider-api"))` | ✅ |
| `ShotOCR/translate-ui/TranslateEngine.kt` | 路由：中英优先 Marian，其余走 Router；Hub 不可用时中英回退 | ✅ |
| `AutoOcrTask` | 不改 | — |

路由规则（实现在 `TranslateEngine`）：

```
中英（zh-Hans / en）
  ├── 内置 OnDeviceTranslate 可用 → 走 Marian（几十毫秒）
  ├── 否则 → Hub
  └── Hub 也失败 → 再试 Marian（抛原始错误）
其他语种（含 zh-Hant / yue / bo / ug / mn / kk）
  └── Hub（没有模型则抛 UNSUPPORTED，UI 可引导下载）
```

注意：`zh-Hant` 有意**不走** Marian —— Marian 只有简体能力，否则用户会拿到简体结果却不自知。

---

## 12. 里程碑

| 阶段 | 内容 | 状态 |
|---|---|---|
| **H1** | 工程骨架 + `catalog.json` + 语种矩阵 + 空模型引导 UI | ✅ 完成 |
| **H2** | 下载（DownloadManager + 多源回退 + 进度 + SHA-256 + 删除 + 空间检查） | ✅ 完成（待真机验证） |
| **H3** | `engine-llama` 接 GGUF，跑通 **PLaMo 0.8B** | ⚠️ 代码与编译完成，模型推理未在真机验证 |
| **H4** | 接 **Hy-MT2-1.8B**（38 语种）+ 模型切换 + 贪心 A/B | ⚠️ 目录与引擎已支持，待真机 + 质量对照 |
| **H5** | 实现 `ITranslateProvider` + 客户端 Router 接入 | ✅ 完成（ShotOCR `:translate-ui` 已构建通过） |
| **H6** | Index-Translate（`qwen35`）+ 语种矩阵核实 + 收尾 | ⚠️ 目录已收录核心 22 语种，其余待核实 |

顺序理由：PLaMo 的 prompt/量化最简单，先打通链路；Hy-MT2 决定中英质量上限；Index 验证「引擎架构兼容」这个最易出事的点。

---

## 13. 已知风险

| 风险 | 影响 | 对策 |
|---|---|---|
| llama.cpp 架构支持矩阵 | PLaMo 3 的架构是 `plamo3`（不是 `llama`），白名单写错会直接拒绝加载 | **软校验**：架构不在白名单只告警，能否运行由 `llama_model_load_from_file` 决定；catalog 里的值仅用于展示与门控下载 |
| 1.25bit / 2bit 依赖 STQ PR #22836 | 440 MB 档位不可用 | 第一版只上 Q4，catalog 标 `available: false` |
| 国内网络 | 直连 HF 失败 | 镜像优先 + 多源回退（必做，非优化项） |
| 大文件校验耗时 | 1.1 GB SHA-256 2–4 秒 | 放在「校验中」状态，IO 线程，不阻塞 UI |
| 内存 | 1.8B 常驻 1.3–1.5 GB，低端机被 LMK 杀 | `minRamMb` 门控 + `onTrimMemory` 主动释放 + 退回 Marian |
| OCR 限制 | 除中英外全部只能手动输入 | UI 如实标注，不承诺截图翻译 |
| 许可 | PLaMo 商用需登记；Gemma 需接受条款 | 下载前许可确认；Gated 模型不收录 |
| 不可复现的自述 | 若误用模型自述生成语种表 | 明确禁止；仅 `tools/probe-langs.py` 开发期旁证 |

---

## 14. 与现有文档的关系

- `ON_DEVICE_AI.md`：未来需要补一行 Hub（provider 表）+ 说明「两个翻译 provider 并存」
- `README.md`：目录总览加 `OnDeviceTranslateHub/`
- `install.bat` / `download-models.bat`：**零改动**（单 app 模块、模型由 App 内下载）
- `OnDeviceTranslate/`：**本轮完全不改**

---

## 15. 已定方案

| 项 | 决定 |
|---|---|
| 默认安装模型 | **一个也不装**，APK 最小化；首启只有 3 张卡片，PLaMo 标「推荐」 |
| 中英极速档（Marian ONNX 235 MB） | **不提供**，由 `OnDeviceTranslate` 承担 |
| PC 侧预下载脚本 | 第一版不做（App 内 `DownloadManager` 已支持断点续传与镜像回退） |
| 契约位置 | 放在本工程 `api/` 模块，客户端用 `projectDir` 引用（不新建第三个顶层工程） |
| 模型目录来源 | 配置文件为唯一真相；`tools/probe-langs.py` 仅开发期旁证 |

## 16. 构建与验证

```powershell
cd OnDeviceTranslateHub
.\tools\fetch-llama.ps1          # 从本地 llama.cpp 复制源码（或 git clone）
.\gradlew.bat :engine-core:testDebugUnitTest   # catalog 数据校验
.\gradlew.bat :app:assembleRelease             # 产出 19.4 MB APK
```

本机已通过：

| 检查 | 结果 |
|---|---|
| `:engine-core:testDebugUnitTest` | ✅ 9 个测试全绿（语种码不丢失、dialects 子集、prompt style、采样参数…） |
| `:engine-llama:buildCMakeRelease[arm64-v8a]` | ✅ llama.cpp + ggml 静态链接成单个 `libhubllama.so` |
| `:app:assembleDebug` / `assembleRelease` | ✅ release 19.4 MB |
| `ShotOCR :translate-ui:assembleDebug` | ✅ |
| `ShotOCR :app:assembleDebug` | ✅（374 tasks） |
| `install.bat OnDeviceTranslateHub` | ⚠️ 未执行（无设备） |
| 真机安装与推理 | ❌ 当前无 adb 设备，未验证 |

## 17. 已知限制

1. **未在真机跑过推理**：JNI 与 CMake 只验证到「编译通过」。首次上机需要关注的顺序是
   `PLaMo 0.8B → 模型加载 → 一条中译英 → 内存/耗时`。
2. **debug 包 37.3 MB**：debug 变体不 strip 符号（`libhubllama.so` 72 MB）。日常安装建议用
   `install OnDeviceTranslateHub release`。
3. **Index-Translate 只列了核心 22 语种**：150 语种的完整清单需要从技术报告附录导入，否则
   capability 会低估它的覆盖范围（不影响正确性，只是路由少了一个候选）。
4. **PLaMo 的 prompt 用的是通用指令**：官方推荐的 `{"target_lang": ...}` 需要 Jinja 渲染，
   当前走 `llama_chat_apply_template` 的预置模板 + ChatML 兜底；如果实测质量不佳，再补 Jinja。
5. **不实现 OpenCC 简繁转换**：`zh-Hant` 直接交给模型（Hy-MT2 原生支持）；PLaMo 不含繁体，所以
   PLaMo 用户只能选 `zh-Hans`。
6. **模型 SHA-256 暂为空**：catalog 里 `sha256` 留空时，安装时只校验文件大小并把实际哈希写进
   `manifest.json`。补齐 catalog 的哈希即可启用严格校验。

## 18. 2026-11-17 修复记录

| 问题 | 根因 | 修复 |
|---|---|---|
| 下载 PLaMo 后翻译报「模型加载失败」 | `catalog.json` 把 PLaMo 的架构写成 `llama`，实际 GGUF 的 `general.architecture` 是 **`plamo3`**，被白名单硬拒绝 | ① catalog 改为 `["plamo3","plamo2","plamo"]`；② 架构校验降级为**告警不阻断**；③ `nativeLoad` 返回真实失败原因（含 llama.cpp 日志）；④ mmap 失败自动退化重试 |
| 下载完成后模型页仍显示「下载」 | UI 只读内存里的 `states`；下载期间 App 被系统回收后状态丢失，磁盘上的安装结果没被读取 | ① 新增 `resolveState()`：内存状态优先，缺失时查磁盘；② 启动时同步初始化 `installedIds`；③ 下载任务 ID 持久化到 SharedPreferences，重启后自动恢复轮询 |
| 删除后重新下载「没反应」，许可提示也不再出现 | ① `DownloadManager` 对「相同 URL + 相同目标路径」的请求会复用旧任务，直接返回已完成的 id，导致瞬时成功但文件不存在；② 许可接受状态被持久化，删除后不会重置 | ① 下载文件名加时间戳前缀，安装时按后缀匹配最新文件，彻底避开任务去重；② 许可确认**不做持久化**，每次点「下载」都重新弹出（`catalog.license.requiresAccept` 只表示是否需要提示） |
| PLaMo 加载报 `unknown tokenizer: 'plamo3'` | llama.cpp b9113 的词表分派只认 `plamo2`，而 PLaMo 3 的 GGUF 写的是 `tokenizer.ggml.model = plamo3`；两者词表结构同源（Aho-Corasick + 分数表，特殊 token id 完全一致：unk=0/bos=1/eos=2/pad=3） | 本地 patch `llama-vocab.cpp`：新增 `plamo3` 分支，复用 `LLAMA_VOCAB_TYPE_PLAMO2`。新增 `tools/patch-llama.ps1`（幂等），`fetch-llama.ps1` 会自动调用；升级 llama.cpp 后若已原生支持 `plamo3`，脚本检测到同名分支会跳过 |
| PLaMo 加载成功但翻译结果为「没有有效结果」 | PLaMo 3 使用专有的 **PFML3** 对话格式（`<\|plamo:tag\|>dataset…`），我们回退到了 ChatML；模型不认识 `<\|im_start\|>`，第一步就输出 EOS（其 `eos_token_id=1`），得到空串 | `PromptBuilder` 增加 `plamo3` 风格，直接拼完整的 PFML3 字符串（含显式 BOS；PLAMO2 的 tokenize 分支不会自动加 BOS）；catalog 的 `prompt.style` 改为 `plamo3`；输出侧统一清理 `<\|xxx\|>` 特殊标记；新增 `PromptBuilderTest` 钉死格式 |
| 所有模型 prefill 都失败，`llama_decode` 返回 `-1` | `llama_batch_init()` 只分配缓冲区，`batch.n_tokens` 初始为 0，必须由调用方设置；漏设后 `llama_decode` 直接以 `n_tokens == 0` 返回 -1 | `decode_tokens()` 里补上 `batch.n_tokens = n`。这是从第一版 JNI 就存在的 bug，影响所有 GGUF 模型 |
| 失败信息只有一句「模型没有给出有效结果」 | 诊断信息不完整：`llama_decode` 的返回码没记录，多条候选诊断互相覆盖 | ① `decode_tokens` 返回 `int32_t`，prefill/单步 decode 都记录 `rc` 与 llama.cpp 日志；② `nativeLastError()` 回传；③ Kotlin 侧诊断改为拼接而非覆盖；④ 新增 `nativeTokenize/nativeDetokenize` 做分词往返校验 |
| 推理极慢（单句 22 秒） | ① catalog 没配 stop tokens，模型一路生成到 `maxTokens=64`；② 交叉编译未指定 ARM 扩展（`armv8-a`，无 dotprod/i8mm）；③ 线程固定 4 | ① PLaMo 配 `stopTokens`（`<\|plamo:msg\|>` 等），命中即停；② `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+i8mm+fp16`；③ 线程 `min(cores, 8)`；④ `maxTokens` 收紧为 `char*2+16`；⑤ native 输出 `prefill_ms/decode_ms/steps` 统计 |

性能实测（NX789J / 骁龙 8 Elite / PLaMo 0.8B Q4_K_M / en→zh）：

| 场景 | 耗时 |
|---|---|
| 优化前 | 22,163 ms |
| 优化后（首次，含加载） | 4,660 ms |
| 优化后（第二次） | 313 ms |
| 优化后（第三次） | 592 ms |

三个模型的实际架构（从 GGUF 头部验证）：

| 模型 | `general.architecture` |
|---|---|
| PLaMo 3 Translate 0.8B | `plamo3` |
| Hy-MT2-1.8B | `hunyuan-dense` |
| Index-Translate 2B | `qwen35` |

验证方法：对 GGUF 发起 `Range: bytes=0-65535`，解析头部的第一个 KV 对。
