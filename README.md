# AndroidProjects

Android 工作区仓库，核心思路：把本地 AI 能力（OCR、翻译、LLM）从业务 App 中拆出，
做成三个**独立安装、内嵌模型**的 provider App；业务 App 只保留逻辑，通过 AIDL 跨进程调用。
这样主 APK 体积从 ~893 MB 降到 ~20 MB，且各能力可独立更新、按需安装。

## 术语与核心概念

- **provider 工程**：自带模型和推理引擎的独立 Android App，自身不面向普通用户，
  它的职责是「装在本机、后台待命，别人来请求就去干活」。它把能力通过 AIDL Service 暴露出去，
  Service 由自定义权限（`*.permission.USE_SERVICE`，`protectionLevel="normal"`）保护，
  客户端声明该权限后即可绑定。
- **client（客户端）工程**：面向用户的业务 App，不携带模型，体积小；
  运行时通过 bind 到 provider 的 Service 来「借用」能力，类似客户端调 API。
- **api 模块**：provider 与 client 之间的契约（AIDL 接口 + 回调 + 异常 + 权限声明）。
  client 通过 `projectDir` 直接引用 provider 的 `api` 模块，无需把整个 provider 工程拉进自己的 Gradle 构建。

```
ShotOCR / AutoOcrTask（client，业务 App）
        │  bind AIDL Service（受自定义权限 *.permission.USE_SERVICE 保护）
        ▼
OnDeviceOcr / OnDeviceTranslate / OnDeviceLlm（provider，模型与推理，独立 APK）
```

> 「provider / client」这组词是服务提供方 / 调用方的一般叫法，
> 与 Android SDK 的 `ContentProvider` 类无关。

## 目录总览

| 目录 | 角色 | 说明 |
|---|---|---|
| `OnDeviceOcr/` | provider | 图片 → 文本行 + 像素坐标（RapidOCR PP-OCRv4，~16 MB 模型，APK 62 MB） |
| `OnDeviceTranslate/` | provider | 文本 → 文本，中英双向（~488 MB 模型，APK 526 MB） |
| `OnDeviceTranslateHub/` | provider | 模型商店 + 多语种翻译：APK 19 MB，模型按需下载（GGUF，PLaMo 3 / Hy-MT2 / Index-Translate） |
| `OnDeviceLlm/` | provider | system + user → 文本（Qwen3-0.6B int4，~347 MB 模型，APK 353 MB） |
| `ShotOCR/` | client | 截图记账主 App（多模块：app、accounting、classification、database、llm-parser、shizuku 等，APK 20 MB） |
| `AutoOcrTask/` | client | 自动化任务（app 单模块，APK 12 MB） |
| `WalletLoginDemo/` | 独立 Demo | Google Wallet 动态二维码 / NFC HCE 碰一碰替代登录，含 Node 后端；详见其 [README.md](WalletLoginDemo/README.md) |
| `HowManyHanzi/` | 独立 App | 汉字识字量自测（Kotlin + Compose，完全离线；内置 8105 字资料、6866 字笔顺字模，含写字动画与写信彩蛋）；详见其 [README.md](HowManyHanzi/README.md) |
| `QuotaMonitor/` | 独立 App | 套餐限额监控（Kotlin + Compose；内置 WebView 复用登录态，精准提取 OpenCode / 火山方舟 Coding Plan 额度，原生仪表盘展示）；详见其 [README.md](QuotaMonitor/README.md) |
| `keystore/` | 签名资产 | `shared.p12` + `keystore.properties`，五个 App 共用同一证书（便于 debug / release 互相覆盖升级）；已 gitignore，不要提交 |
| `install.bat` | 脚本 | 对工作区里的工程执行 gradle 安装到设备，支持 `release` / `clean` 参数 |
| `download-models.bat` | 脚本 | `download-models <工程名>`：按工程内 `.url` 文件下载模型到对应位置，已存在则跳过，支持断点续传；`HF_MIRROR` 可指定 HuggingFace 镜像 |
| `start-backend.bat` | 脚本 | `start-backend <工程名> [port:xxx]`：启动工程内的后端服务（现在用于 WalletLoginDemo） |
| `ON_DEVICE_AI.md` | 文档 | 拆分架构的详细说明：签名、权限、API 契约、运行时行为、构建与测试 |

每个 provider 工程结构固定：

```
OnDeviceXxx/
├── app/      # Application 模块：AIDL Service 实现 + Compose 设置/自测界面
├── engine/   # 模型与推理代码（内部实现，不对外暴露）
├── api/      # AIDL 契约 + 客户端封装 + USE_SERVICE 权限声明
└── gradle/   # 独立的 libs.versions.toml
```

## 快速开始

1. **下载模型**（模型不进 git）：

   ```powershell
   .\download-models.bat OnDeviceOcr
   .\download-models.bat OnDeviceTranslate
   .\download-models.bat OnDeviceLlm
   ```

2. **按顺序安装**（三个 provider 必须先装，client 才能绑定）：

   ```powershell
   .\install.bat OnDeviceOcr
   .\install.bat OnDeviceTranslate
   .\install.bat OnDeviceTranslateHub release
   .\install.bat OnDeviceLlm
   .\install.bat ShotOCR
   .\install.bat AutoOcrTask
   .\install.bat HowManyHanzi
   .\install.bat QuotaMonitor
   ```

3. **独立构建**（每个工程是独立 Gradle 构建）：

   ```powershell
   cd OnDeviceOcr;       .\gradlew.bat assembleDebug
   cd ShotOCR;           .\gradlew.bat :app:assembleDebug
   ```

## 给 AI 助手的提示

在这个工作区做开发时，请遵守以下约定：

- **结构约定**：provider 工程固定为 `app/ engine/ api/ gradle/` 四个模块。
  新增 provider 时按同样结构创建，`engine/` 代码不要被 `api/` 之外的任何模块引用。
- **改契约要两侧同步**：改 `api/` 下的 AIDL 接口时，`app/`（Service 实现）和
  调用它的 client 侧（`OcrClient` / `TranslateClient` / `LlmClient` 封装）都要同步更新；
  `API_VERSION` 递增，并保持 `MIN_SUPPORTED_API_VERSION` 兼容策略。
- **统一证书签名**：所有 App（provider 与 client）都用 `keystore/shared.p12`
  （alias `shared`，配置在 `keystore/keystore.properties`)签名，debug 与 release 都指向它，
  这样 debug / release 包可以互相覆盖升级。绑定服务不再要求同证书
  （USE_SERVICE 权限是 `normal` 级），但签名仍必须与 keystore 保持统一。
- **模型是外置的**：provider 里模型文件不入库，通过 `.url` 清单下载，
  不要把模型文件提交到 git，也不要在代码里写死模型缓存路径之外的模型期望。
- **各工程独立构建**：跨工程改动时，每个工程的 `gradle/libs.versions.toml` 是独立的，
  依赖与版本号不会自动同步，需要单独检查。
- **离线数据是生成物**：`HowManyHanzi` 的 `app/src/main/assets/strokes/`、`assets/data/`
  由 `tools/` 下的 Node 脚本从小程序源码和 `hanzi-writer-data` 生成，不进 git；
  改字库或笔顺逻辑时重跑脚本，不要手改生成文件。
- **APK 只带 arm64-v8a**：x86 模拟器上无法运行这些 App。

## 相关文档

- [ON_DEVICE_AI.md](ON_DEVICE_AI.md) — 拆分架构详细说明（签名、权限、API 契约、运行时行为、构建与测试）
- [WalletLoginDemo/README.md](WalletLoginDemo/README.md) — Wallet / NFC 登录 Demo 的架构与运行方式
- [HowManyHanzi/README.md](HowManyHanzi/README.md) — 汉字识字量自测 App 的功能、离线数据管线与构建方式
