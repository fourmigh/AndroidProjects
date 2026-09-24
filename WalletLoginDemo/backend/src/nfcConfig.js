// NFC 安全开关配置：持久化到 data/nfc-config.json，默认全部关闭（保持基础行为）。
const fs = require('node:fs');
const path = require('node:path');

const CONFIG_FILE = path.join(__dirname, '..', 'data', 'nfc-config.json');

const DEFAULTS = {
  serverChallenge: false, // 后端下发 challenge
  readerAuth: false,      // 读卡器认证（X-Reader-Token）
  sessionTtlMs: 0,        // 会话有效期，0 = 不管理会话
  useEcdsa: false,        // 非对称签名（否则对称 HMAC）
  useOpaqueId: false,     // 不透明 ID（否则明文 userId）
  shortTimeout: false,    // 读卡器短超时（防中继）
  shortTimeoutMs: 2000,   // 短超时具体值（毫秒）
  challengeTtlMs: 10000,  // 后端下发 challenge 的有效期
};

function ensureFile() {
  const dir = path.dirname(CONFIG_FILE);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  if (!fs.existsSync(CONFIG_FILE)) {
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(DEFAULTS, null, 2), 'utf-8');
  }
}

function getConfig() {
  ensureFile();
  try {
    return { ...DEFAULTS, ...JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8')) };
  } catch {
    return { ...DEFAULTS };
  }
}

function setConfig(patch) {
  const next = { ...getConfig() };
  for (const key of Object.keys(DEFAULTS)) {
    if (patch && patch[key] !== undefined) {
      next[key] = patch[key];
    }
  }
  ensureFile();
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(next, null, 2), 'utf-8');
  return next;
}

module.exports = { getConfig, setConfig, DEFAULTS, CONFIG_FILE };
