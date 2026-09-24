# Wallet Login Demo

用 **Google Wallet 卡片（动态二维码）** 或 **手机 NFC 碰一碰（HCE）** 替代传统登录操作的 Demo。

- **方案一：Google Wallet 动态码** — 用户把「登录卡」保存进 Google Wallet，登录时出示卡片上的动态二维码，扫码端验证通过即完成登录；后续可平滑升级为 Smart Tap（需资质）。
- **方案二：NFC 碰一碰（HCE）** — 手机用 HCE 模拟一张卡，读卡器碰一下完成「挑战-响应」登录，**无需 Google Wallet 与 Smart Tap 资质**。

## 架构

### 方案一：Google Wallet 动态码

```
[绑定阶段]
Android App ──POST /api/bind-pass──> Node 后端
                                       ├─ 生成用户 TOTP 密钥（Base16）
                                       ├─ genericobject.insert 到 Google Wallet（含密钥）
                                       └─ 返回 savetowallet JWT（只引用 objectId）
Android App ──savePassesJwt──> Google Wallet 保存卡片

[登录阶段]
Google Wallet 显示动态二维码（本地按 TOTP 生成）
        │
扫码端 App ──POST /api/login/scan {code}──> Node 后端
                                       ├─ 解析 userId / timestamp / totp
                                       ├─ 用该用户密钥重算 TOTP 校验（±1 窗口）
                                       ├─ 防重放校验
                                       └─ 返回 sessionToken + user
```

二维码内容格式：`WLOGIN-<userId>-<timestampSeconds>-<totp>`。

### 方案二：NFC 碰一碰（HCE）

```
[绑定阶段]（一次性）
Android App ──读配置──> 后端 /api/nfc/config
Android App ──POST /api/nfc/bind {userId, publicKey?}──> 后端
                                       ├─ 按当前配置生成凭证（HMAC 密钥 或 ECDSA 公钥）
                                       └─ 返回 { mode, idType, opaqueId, nfcKey }
Android App ──存本机──> HMAC 密钥存 EncryptedSharedPreferences；ECDSA 私钥存 Android Keystore

[登录阶段]（每次碰触）
读卡器 ──SELECT AID + challenge──> 手机（HCE）
手机  ──id ‖ 0x00 ‖ proof（HMAC 或 ECDSA 签名）──> 读卡器
读卡器 ──POST /api/nfc/verify {id, challenge, proof}──> 后端
                                       ├─ 按【用户记录的 mode】逐一验签（HMAC / ECDSA）
                                       ├─ challenge 防重放 / 读卡器认证（可选）
                                       └─ 返回 sessionToken + user
```

## 目录

- `backend/` — Node.js + Express：签发 Pass、校验动态码、NFC 绑定与验证
- `android/` — Kotlin + Jetpack Compose：绑定卡片、扫码登录、NFC 碰一碰

## 快速体验（模拟模式，无需 Google 凭据）

暂时没有 Google 账号时，可直接启动后端——脚本会自动检测到缺少凭据并进入**模拟模式**，
不调用 Google Wallet API，登录闭环改由 App 直接渲染动态码完成：

1. 启动后端：`start-backend.bat WalletLoginDemo`（日志显示 `Wallet mode: MOCK`）
2. App 点「3. 模拟钱包（无需 Google 凭据）」→ 输入用户 ID → 显示动态二维码
3. 用另一台设备的「2. 扫码登录」扫该二维码，或直接点「一键模拟登录」

> 模拟模式不会把卡片加入 Google Wallet（签发有效 JWT 必须使用真实服务账号）。
> 配置好凭据后会自动切换为真实模式，其余逻辑不变。
> NFC 方案（4/5）不依赖 Google 凭据，任何时候都可用。

## 前置配置（一次性，仅方案一需要）

1. **Google Cloud**
   - 新建项目，启用 **Google Wallet API**
   - 创建**服务账号**，创建并下载 JSON 密钥，重命名为 `service-account.json` 放到 `backend/`
2. **Google Pay & Wallet Console**（https://pay.google.com/business/console）
   - 创建 **Issuer 账户**（可选择 Demo 模式），记下 **Issuer ID**
   - 在用户/权限里把上面的服务账号邮箱加入
   - Demo 模式下，把测试用的 Google 账号加入测试人员白名单（否则无法把卡片添加到钱包）
3. 后端配置：复制 `backend/.env.example` 为 `backend/.env`，填写 `ISSUER_ID`、`CLASS_SUFFIX` 等

> Demo 模式限制：卡片只能添加到白名单测试账号的 Google Wallet，卡片会带 DEMO 标识，无法公开发行。

## 运行后端

Windows 快捷方式（在仓库根目录 `AndroidProjects\` 下执行）：

```bat
start-backend.bat WalletLoginDemo
```

脚本会自动进入 `WalletLoginDemo\backend`、按需安装依赖、提示缺失的 `.env` / `service-account.json`，
并打印本机 IPv4（与 `install.bat` 自动写入的地址一致，可用来核对）。

首次运行若缺少防火墙规则，脚本会请求管理员权限（UAC）自动放行后端端口，之后启动会静默跳过。
端口默认取 `.env` 的 `PORT`（否则 3000），也可显式指定：

```bat
start-backend.bat WalletLoginDemo port:8080
```

或手动：

```powershell
cd backend
npm install
npm run dev
```

自测（方案一）：

```powershell
# 绑定一个用户（模拟已登录用户发起绑定）
curl -X POST http://localhost:3000/api/bind-pass -H "Content-Type: application/json" -d "{\"userId\":\"user001\"}"

# 用返回的 saveUrl 在浏览器打开，可预览卡片
# 离线造一个当前时刻的动态码，然后校验登录
node scripts/makeCode.js user001
curl -X POST http://localhost:3000/api/login/scan -H "Content-Type: application/json" -d "{\"code\":\"<上一步输出>\"}"
```

自测（方案二，NFC）：

```powershell
# 绑定 NFC（自动创建用户，默认 HMAC 模式）
curl -X POST http://localhost:3000/api/nfc/bind -H "Content-Type: application/json" -d "{\"userId\":\"user001\"}"

# 查看已绑定用户
curl http://localhost:3000/api/nfc/bindings

# 解绑
curl -X POST http://localhost:3000/api/nfc/unbind -H "Content-Type: application/json" -d "{\"userId\":\"user001\"}"
```

## 运行 Android

Windows 快捷方式（在仓库根目录 `AndroidProjects\` 下执行，自动编译并安装到已连接设备）：

```bat
install.bat WalletLoginDemo
```

`install.bat` 会自动探测本机 IPv4，并把后端地址写入 `android\local.properties`：

```properties
backend.url=http://<本机IP>:3000
nfc.reader.token=dev-reader-token
```

该值在打包时注入 `BuildConfig.BASE_URL` / `BuildConfig.NFC_READER_TOKEN`，源码中不含任何硬编码 IP；
换 IP 只需重新执行一次 `install.bat`。若后端端口不是 3000：

```bat
install.bat WalletLoginDemo port:8080
```

或手动：

1. 用 Android Studio 打开 `android/` 目录（后端地址读取自 `local.properties` 的 `backend.url`，缺省 `http://127.0.0.1:3000`）
2. 连接真机运行（模拟器无法添加 Google Wallet 卡片、也无法用 NFC）

App 主界面提供 5 个入口：

| 入口 | 作用 |
|---|---|
| 1. 绑定登录卡到 Google Wallet | 方案一：签发并保存 Google Wallet 卡片 |
| 2. 扫码登录 | 方案一：扫描 Google Wallet 上的动态二维码 |
| 3. 模拟钱包（无需 Google 凭据）| 方案一：无凭据时的模拟动态码 |
| 4. 绑定 NFC 登录 | 方案二：领取 NFC 凭证（HCE 出示方）|
| 5. NFC 读取（测试读卡器）| 方案二：Reader Mode 读卡器 + 服务端绑定列表/解绑 + 「设置」入口 |

> 手机需与电脑处于同一 Wi-Fi，且电脑防火墙放行后端端口（默认 3000）。

## 验证要点

- 方案一：`/api/bind-pass` 返回的 `saveUrl` 能在浏览器预览卡片；动态码正确时 `/api/login/scan` 返回 `ok:true`，错误/过期返回 401，重复返回 409
- 方案二：`/api/nfc/bind` 成功返回 `nfcKey`/`opaqueId`；`/api/nfc/verify` 正确 proof 返回 `ok:true`，错误 401，challenge 重放 409，读卡器未授权 403

## NFC 碰一碰登录（HCE，无需 Google Wallet）

不依赖 Google Wallet 和 Smart Tap 资质，用手机自身 NFC 的 **HCE（卡模拟）+ 读卡器** 实现碰一碰登录。

**协议**（AID = `F04C4F47494E`）：

| 步骤 | 读卡器 → 手机 | 手机 → 读卡器 |
|---|---|---|
| 选择应用 | `00 A4 04 00 06 F04C4F47494E 00` | `90 00` |
| 挑战 | `80 10 00 00 10 <16字节 challenge>` | `id ‖ 0x00 ‖ proof ‖ 90 00` |

- `id` = `userId`（默认）或 `opaqueId`（不透明 ID 模式）
- `proof` = `HMAC-SHA256(nfcKey, challenge)` 或 `ECDSA-SHA256(私钥, challenge)`

**使用步骤**：

1. App 点「4. 绑定 NFC 登录」→ 输入用户 ID → 按当前配置生成并保存凭证（HMAC 密钥 / ECDSA 私钥）
2. 手机靠近 NFC 读卡器（或另一台手机打开「5. NFC 读取（测试读卡器）」）
3. 读卡器 SELECT AID → 下发 challenge → 手机返回 proof → 读卡器调后端验证

**服务端接口**：

| 接口 | 作用 |
|---|---|
| `POST /api/nfc/bind` | 绑定（**自动创建用户**；按当前配置生成 HMAC/ECDSA 凭证，返回 `mode/idType/opaqueId/nfcKey`）|
| `POST /api/nfc/challenge` | 申请 challenge（可选；`readerAuth` 开启时校验 `X-Reader-Token`）|
| `POST /api/nfc/verify` | 校验 `proof`（按用户记录的模式逐一尝试）→ 签发会话 |
| `GET /api/nfc/bindings` | 已绑定 NFC 的用户列表 |
| `POST /api/nfc/unbind` | 解绑（清该用户全部凭证）|
| `GET` / `PUT /api/nfc/config` | 安全开关配置 |
| `GET /api/session/validate` | 会话校验 |

**绑定 / 解绑**：App「4. 绑定 NFC 登录」可绑定；已绑定时该页提供「解绑」按钮（清本机 + 服务端）。
App「5. NFC 读取」页下方还会列出**服务端所有已绑定用户**，可直接解绑。

**读卡器端**：需一台可编程 NFC 读卡器（支持 ISO-DEP / 自定义 AID，如 ACR122U / PN532），
按上述协议实现「SELECT AID → 下发 challenge → 读取 proof → 调 `/api/nfc/verify`」。本次未实现。

## NFC 安全设置

App「5. NFC 读取」页右上角「设置」可打开。**配置保存在服务端**（`data/nfc-config.json`），客户端与服务端共用同一份。

| 开关 | 说明 |
|---|---|
| 后端下发 challenge | 由服务端生成随机数并要求读卡器先申请；后端校验其合法、未用、未过期，防自造/重放 |
| 读卡器认证 | 读卡器调用后端须携带共享口令 `X-Reader-Token`（配在 `.env` 的 `NFC_READER_TOKEN`）|
| 非对称签名 (ECDSA) | 手机用 Keystore 私钥签名、服务端用公钥验签；私钥不可导出。关闭则用对称 HMAC |
| 不透明 ID | 手机响应传随机 `opaqueId` 而非明文 `userId` |
| 防中继短超时 | 读卡器通信超时缩短，中继攻击因延迟超时而失败 |
| 短超时 (ms) | 启用「防中继短超时」时使用的超时值（默认 2000ms）|
| 会话有效期 (秒) | >0 时签发带过期的会话 token；0 表示不管理会话 |
| challenge 有效期 (ms) | 后端下发 challenge 的有效期（默认 10000ms）|

**兼容策略**：`useEcdsa` / `useOpaqueId` 是**协议级**开关，切换后**只影响新绑定**；
已绑定用户按**绑定时记录的模式**验证，仍可正常使用（服务端逐一尝试 HMAC/ECDSA）。
若要让某台已绑定手机切换到新模式，需**解绑后重新绑定**。

> 绑定时客户端会先读一次配置（用于决定生成公钥还是领密钥），但**最终模式以服务端返回的 `mode` 为准**；
> 出示（碰触）时手机端**不再读配置**，用本机存储的模式计算 proof。

## 排查：手机无法被读卡器读取

### 1. 「检测到标签但不是 ISO-DEP」

通常是手机端 HCE 被 **Reader Mode** 占用了。用 `adb` 检查：

```bash
adb shell dumpsys nfc | findstr /i "mEnableReader mEnableHostRouting"
```

- 正常（HCE 可用）：`mEnableReader: false`、`mEnableHostRouting: true`
- 异常（HCE 被禁）：`mEnableReader: true`、`mEnableHostRouting: false`

**Reader Mode 与 HCE 互斥**：只要有 App 启用读卡模式，卡模拟就会被禁用。

**常见元凶**：部分国产 ROM（如红魔 / 努比亚）的「卡包 / 钱包」在作为**默认付款应用**时会占用 Reader Mode。
解决办法：进入「设置 → 连接 → NFC → 默认付款应用」，把它**切换成 Google Pay**（或其它非厂商卡包项），
再验证上面两条命令是否变为 `false` / `true`。

> 实测：红魔 10 Pro+（Android 16）把默认付款应用从「卡包」换成 **Google Pay** 后，
> `mEnableReader` 变 `false`、`mEnableHostRouting` 变 `true`，NFC 登录立即正常。
> 海外手机默认就是 Google Wallet（标准实现），通常不会占用 Reader Mode。

> 注意：eSE 卡模拟（手机钱包里的门禁卡 / 公交卡）走**安全元件**，不受 Reader Mode 影响，
> 所以「门禁能刷手机」**不能**说明 HCE 可用；且 eSE 通道普通 App 无法使用。

### 2. 「tag was lost」/ `TagLostException`

常见原因：

- **手机（出示方）熄屏**：HCE 默认要求屏幕亮起（`requireDeviceScreenOn`）。本项目已在 `apduservice.xml`
  设 `requireDeviceScreenOn="false"` 允许熄屏，但**最终取决于设备 NFC 控制器是否在熄屏时关闭**（硬件/ROM 限制）
- **Secure NFC（Android 10+）**：开启时熄屏下所有卡模拟不可用；可在「设置 → NFC → 需要解锁才能使用 NFC」检查
- **贴合不牢 / 移动**：对准两机 NFC 天线（背面中上部），保持贴合
- **通信超时**：若开启了「防中继短超时」且值过小，ECDSA 签名较慢时可能误超时 → 调大「短超时 (ms)」

## 升级到 NFC 碰一碰（Smart Tap）

当拿到 Google 的 Smart Tap 资质并使用认证读卡器后：

1. Pass Class 增加 `enableSmartTap: true` 和 `redemptionIssuers: [<Redemption Issuer ID>]`
2. Pass Object 增加 `smartTapRedemptionValue`（放登录凭证）
3. 登录端由「扫码」换成「NFC 读取」，后端校验逻辑复用

> 注意：Smart Tap 是 Google 专有协议，读卡终端必须通过 Google 认证，普通 Android App 不能充当读卡器。

## 安全说明

**方案一（动态码）**

- Demo 使用内存防重放 + TOTP 时间窗校验；生产环境应改为持久化存储并缩短有效期

**方案二（NFC HCE）**

- **挑战-响应**：每次 challenge 随机，手机用 HMAC 或 ECDSA 签名，服务端重算/验签
- **ECDSA 模式**：私钥生成于 Android Keystore（不可导出），服务端仅存公钥，即使手机被 root 也难以提取
- **不透明 ID**：可选，用随机 `opaqueId` 替代明文 `userId`
- **防重放**：challenge 一次性 + 有效期；已用 challenge 拒绝
- **读卡器认证**：可选，读卡器需携带 `X-Reader-Token`
- **会话有效期**：可配置的 token 过期
- **防中继**：可选短超时（默认 2000ms）
- **传输**：Demo 使用 HTTP，生产环境**必须改用 HTTPS**（否则局域网可嗅探到 challenge/proof）
- **残留风险**：中继攻击（挑战-响应无法根除）、熄屏 HCE 受硬件限制

**通用**

- `service-account.json`、`.env`、`data/*.json` 已被 `.gitignore` 忽略，切勿提交
- `data/nfc-config.json`、`data/users.json` 为运行时数据，非源码
