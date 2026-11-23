# OnDeviceTranslateHub

「离线翻译模型」App：**APK 里只带模型清单，不带模型**。用户在首次使用时按需下载
（0.53 GB ~ 1.1 GB），下载后完全离线，可随时删除回收空间。

它是 `OnDeviceTranslate`（526 MB，内嵌 Marian，中英）的补充，不是替代：

| | OnDeviceTranslate | OnDeviceTranslateHub |
|---|---|---|
| 定位 | 零下载、极速、中英 | 多语种、高质量、按需下载 |
| 模型 | OPUS-MT（ONNX） | PLaMo 3 / Hy-MT2 / Index-Translate（GGUF） |
| 语种 | zh ↔ en | 22 ~ 53 语种，含粤语/民汉 |
| APK | 526 MB | 19.4 MB |

两者都实现翻译 provider 契约，由客户端 Router 选择；`ShotOCR` 的中英仍走 Marian，
其他语种走 Hub。

## 结构

```
api/           AIDL 契约 + 客户端封装 + Router + 语种目录（LanguageCatalog）
engine-core/   目录清单、下载、校验、存储、prompt 拼装、模型调度
engine-llama/  llama.cpp JNI（GGUF 推理、chat template、采样、后处理）
app/           Compose UI（模型商店 / 已安装 / 翻译测试）+ AIDL Service
tools/         fetch-llama.ps1 / gen-catalog.py / probe-langs.py
```

依赖方向：`app → engine-llama → engine-core → api`。

## 构建

```powershell
# 1. 准备 llama.cpp 源码（约 22 MB，不进 git）
.\tools\fetch-llama.ps1

# 2. 校验 catalog 数据
.\gradlew.bat :engine-core:testDebugUnitTest

# 3. 出包
.\gradlew.bat :app:assembleDebug      # 37 MB（未 strip）
.\gradlew.bat :app:assembleRelease    # 19.4 MB

# 4. 安装（根目录脚本，零改动）
.\install.bat OnDeviceTranslateHub release
```

需要 NDK `27.0.12077973` 与 CMake `3.31.1`，只编 `arm64-v8a`。

> ⚠️ `engine-llama/build.gradle.kts` 里写死了 `GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+i8mm+fp16`，
> 面向骁龙 8 Elite（ARMv9）。给更老的设备用时要改成 `armv8.2-a+dotprod`，否则可能 SIGILL。

## 添加 / 更新模型

只改 `app/src/main/assets/catalog.json`，不用发新代码：

```json
{
  "id": "hymt2-1.8b-q4km",
  "name": "混元翻译 Hy-MT2-1.8B",
  "langs": ["zh-Hans", "zh-Hant", "yue", "en", "..."],
  "dialects": ["yue", "bo", "kk", "mn", "ug"],
  "textOnlyLangs": ["yue", "ja", "..."],
  "sizeBytes": 1133080448,
  "minRamMb": 3072,
  "files": [{ "name": "model.gguf", "sizeBytes": 1133080448, "sha256": "",
              "urls": ["https://hf-mirror.com/...", "https://huggingface.co/..."] }],
  "prompt": { "style": "hymt" },
  "sampling": { "temp": 0.7, "topK": 20, "topP": 0.6, "repPenalty": 1.05 },
  "postprocess": ["stripPrefix", "stripQuotes"],
  "engineReq": { "llamaArch": ["hunyuan-dense"], "minEngine": 1 },
  "langsSource": "https://huggingface.co/tencent/Hy-MT2-1.8B#supported-languages",
  "verifiedAt": "2026-11-17",
  "available": true
}
```

约定：

- `langs` 必须是 `LanguageCatalog` 里的规范码（BCP-47 子集），否则单测失败
- `dialects`、`textOnlyLangs` 必须是 `langs` 的子集
- `prompt.style` 取 `plain` / `hymt` / `index_zh`（见 `PromptBuilder`）
- `engineReq.llamaArch` 是**软校验**：模型加载后读 `general.architecture`，不在列表里只打告警，
  不阻断（白名单会随模型迭代过期，能否运行由 llama.cpp 决定）。已确认的实际值：
  PLaMo 3 = `plamo3`、Hy-MT2 = `hunyuan-dense`、Index-Translate = `qwen35`
- `sha256` 留空 = 只校验大小；填了就严格校验

**语种表不要问模型。** 数据来源优先级：

1. `https://hf-mirror.com/api/models/<repo>` 的 `cardData.language`
2. 模型卡 README 的语言表（比 cardData 更全，例如 Hy-MT2 的 `zh-Hant`、`yue` 只在 README 里）
3. 技术报告附录（Index-Translate 的 Table 32/33）

`tools/gen-catalog.py` 做交叉核对；`tools/probe-langs.py` 只用于开发期发现漏项，
结果不写回 catalog。

## 运行时行为

- 同一时刻只加载一个模型；切换模型 = 卸载 + 重新加载（2–5 秒）
- 空闲 15 分钟或 `onTrimMemory` 时释放
- 下载走系统 `DownloadManager`：断点续传、镜像回退、失败重试、通知
- 存储位置：`/sdcard/Android/data/org.caojun.ai.translate.hub/files/models/<id>/`
- 下载中写 `<id>.part/`，校验通过后原子改名，半成品不会被当成可用模型

## 已知限制

- 只带 `arm64-v8a`，x86 模拟器无法运行
- 除粤语外的汉语方言（吴语/上海话、闽南语、客家话、四川话）没有可用的开源文本 MT
- 现有 OCR 只支持中英，其他语种只能手动输入
- Index-Translate 目前只声明了核心 22 语种（150 语种清单待导入）
- PLaMo 3 的 `plamo3` tokenizer 依赖 `tools/patch-llama.ps1` 打的本地补丁；升级 llama.cpp
  源码后请先确认主线是否已原生支持（若已支持，脚本会自动跳过）
