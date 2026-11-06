# 本地 AI 能力拆分说明

ShotOCR 里的 OCR、翻译、LLM 三块能力已拆成三个独立 App，各自内嵌模型、
各自带设置与自测界面。ShotOCR 和 AutoOcrTask 只保留业务逻辑，通过 AIDL
跨进程调用，主 App 体积从 ~893 MB 降到 ~20 MB。

## 组成

| 工程 | 角色 | 包名 | 提供的能力 | APK 体积 |
|---|---|---|---|---|
| `OnDeviceOcr` | provider | `org.caojun.ai.ocr` | 图片 → 文本行 + 像素坐标 | 62.3 MB |
| `OnDeviceTranslate` | provider | `org.caojun.ai.translate` | 文本 → 文本（中英双向） | 526.2 MB |
| `OnDeviceLlm` | provider | `org.caojun.ai.llm` | system + user → 文本 | 353.0 MB |
| `ShotOCR` | client | `org.caojun.shotocr` | 截图记账主 App | 19.8 MB |
| `AutoOcrTask` | client | `org.caojun.autoocrtask` | 自动化任务 | 11.5 MB |

每个 provider 工程都是独立的 Gradle 构建，结构一致：

```
OnDeviceXxx/
├── app/      # Application 模块：Service 实现 + Compose 设置/自测界面
├── engine/   # 模型与推理代码（内部实现，不对外暴露）
├── api/      # AIDL 契约 + 客户端封装 + signature 权限声明
└── gradle/   # 独立的 libs.versions.toml
```

客户端工程（ShotOCR / AutoOcrTask）通过 `projectDir` 直接引用对应的 `api` 模块，
不需要把 provider 工程 include 进来。

## 安装顺序

三个 provider 必须先安装，客户端才能调用：

```powershell
install OnDeviceOcr
install OnDeviceTranslate
install OnDeviceLlm
install ShotOCR
install AutoOcrTask
```

`install.bat` 对单 app 模块的工程无需特殊处理，与现有工程用法一致；
支持 `release` / `clean` 参数。

## 签名（重要）

`signature` 级权限要求 **五个 App 使用同一证书**，否则绑定服务会被系统拒绝。
当前统一使用：

- keystore：`keystore/shared.p12`（alias `shared`，密码 `android`）
- 配置：`keystore/keystore.properties`
- 五个 App 的 `debug` / `release` 都指向它

证书 SHA-256：

```
0E:AE:42:CD:E1:6C:56:2F:66:97:17:C1:90:FA:16:32:16:78:5F:7C:24:B4:BE:CD:19:96:17:99:C3:5C:EB:51
```

`keystore/` 下的私钥与配置已加入 `.gitignore`，不要提交。
由于签名一致，debug 与 release 包可以互相覆盖升级。

## 权限

每个 provider 各自定义一条 `signature` 权限，服务通过 `android:permission` 保护：

| provider | 权限 |
|---|---|
| OCR | `org.caojun.ai.ocr.permission.USE_SERVICE` |
| 翻译 | `org.caojun.ai.translate.permission.USE_SERVICE` |
| LLM | `org.caojun.ai.llm.permission.USE_SERVICE` |

客户端的 `api` 模块 manifest 会自动合并 `uses-permission` 和 `<queries>`，
业务代码不需要额外声明。

## API 契约

当前 `API_VERSION = 1`。客户端会校验 provider 的版本，低于
`MIN_SUPPORTED_API_VERSION` 时抛 `*Exception(ERROR_UNSUPPORTED)`。

| 能力 | 接口 | 客户端封装 | 失败异常 |
|---|---|---|---|
| OCR | `IOcrService` / `IOcrCallback` | `OcrClient`（另有兼容门面 `OcrEngine`） | `OcrException` |
| 翻译 | `ITranslateService` / `ITranslateCallback` | `TranslateClient` | `TranslateException` |
| LLM | `ILlmService` / `ILlmCallback` | `LlmClient`（实现 `LlmEngineApi`） | `LlmException` |

约定：

- OCR 图片通过临时 PNG + `ParcelFileDescriptor` 传递，避开 Binder 1 MB 事务限制
- 所有回调都是 `oneway`，结果通过 JSON 或纯文本返回
- 错误码统一为 `UNAUTHORIZED / NOT_READY / INVALID_INPUT / BUSY / INFERENCE / UNSUPPORTED`

## 运行时行为

- provider 的模型在进程内单例；最后一次解绑后空闲 5 分钟自动释放
- OCR / 翻译客户端每次调用 bind → 调用 → unbind
- LLM 客户端保持连接直到 `close()`，推理请求在 provider 内串行排队
- 客户端超时：OCR 30 秒、翻译 60 秒、LLM 180 秒

## 构建与测试

各工程独立构建：

```powershell
cd OnDeviceOcr;       .\gradlew.bat assembleDebug
cd OnDeviceTranslate; .\gradlew.bat assembleDebug
cd OnDeviceLlm;       .\gradlew.bat assembleDebug
cd ShotOCR;           .\gradlew.bat :app:assembleDebug
cd AutoOcrTask;       .\gradlew.bat :app:assembleDebug
```

LLM 业务解析的单测：

```powershell
cd ShotOCR
.\gradlew.bat :llm-parser:testDebugUnitTest
```

> 三个 provider 的模型均未纳入 git，克隆后在仓库根目录执行 `download-models.bat <工程名>` 下载：
> OCR（RapidOCR PP-OCRv4，约 16 MB）、翻译（488 MB，内部转发 `OnDeviceTranslate/engine/download-models.ps1`）、
> LLM（Qwen3-0.6B int4 litertlm，347 MB）。OCR 与 LLM 的下载地址写在模型同目录的同名 `.url` 文件里；
> 翻译的文件清单与地址见 `OnDeviceTranslate/engine/download-models.ps1` 和 `engine/README.md` 的「模型来源」表。
> 国内网络可先执行 `set HF_MIRROR=https://hf-mirror.com` 走镜像。

## 已知限制

- 所有 APK 只带 `arm64-v8a`，x86 模拟器无法运行
- provider 未安装或版本过旧时，客户端会返回「服务未就绪」类错误，需要用户手动安装对应 App
- 首次调用会触发模型加载：OCR 约 1–2 秒、翻译约 3–8 秒、LLM 约 10 秒
