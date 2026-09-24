// 纯 node:crypto 实现的 TOTP（RFC 6238 / HOTP RFC 4226）
// 与 Google Wallet rotatingBarcode 的 TOTP_SHA1 算法保持一致。
const crypto = require('node:crypto');

/**
 * 生成一把 Base16（hex）编码的随机密钥。
 * @param {number} bytes 密钥字节数
 * @returns {string} 32 字符（20 字节）的十六进制字符串
 */
function generateKey(bytes = 20) {
  return crypto.randomBytes(bytes).toString('hex');
}

/**
 * HOTP：对计数器做 HMAC-SHA1，再做动态截断。
 * @param {string} keyHex Base16 编码密钥
 * @param {number|bigint} counter 计数器
 * @param {number} digits 输出位数
 * @returns {string} 定长数字字符串
 */
function hotp(keyHex, counter, digits = 8) {
  const key = Buffer.from(keyHex, 'hex');
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(counter));

  const hmac = crypto.createHmac('sha1', key).update(msg).digest();
  const offset = hmac[hmac.length - 1] & 0x0f;
  const binary =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);

  const otp = binary % 10 ** digits;
  return otp.toString().padStart(digits, '0');
}

/**
 * TOTP：根据时间戳推导计数器后计算一次性码。
 * @param {string} keyHex Base16 编码密钥
 * @param {number} timestampSeconds Unix 时间戳（秒）
 * @param {number} periodSeconds 周期（秒）
 * @param {number} digits 位数
 */
function totp(keyHex, timestampSeconds, periodSeconds = 30, digits = 8) {
  const counter = Math.floor(timestampSeconds / periodSeconds);
  return hotp(keyHex, counter, digits);
}

/**
 * 校验动态码，允许前后 window 个时间窗口容错。
 */
function verify(keyHex, timestampSeconds, code, periodSeconds = 30, digits = 8, window = 1) {
  const base = Math.floor(timestampSeconds / periodSeconds);
  for (let i = -window; i <= window; i++) {
    if (hotp(keyHex, base + i, digits) === code) return true;
  }
  return false;
}

module.exports = { generateKey, hotp, totp, verify };
