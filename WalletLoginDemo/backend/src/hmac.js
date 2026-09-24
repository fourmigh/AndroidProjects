// HMAC-SHA256 工具：用于 NFC 挑战-响应登录。
const crypto = require('node:crypto');

/**
 * 计算 HMAC-SHA256，返回小写 hex。
 * @param {string} keyHex Base16 编码密钥
 * @param {Buffer|string} message 待签名消息
 * @returns {string}
 */
function hmacHex(keyHex, message) {
  return crypto
    .createHmac('sha256', Buffer.from(keyHex, 'hex'))
    .update(message)
    .digest('hex');
}

/**
 * 恒定时间比较两个 hex 字符串是否相等。
 */
function safeEqualHex(aHex, bHex) {
  if (typeof aHex !== 'string' || typeof bHex !== 'string') return false;
  const a = Buffer.from(aHex, 'hex');
  const b = Buffer.from(bHex, 'hex');
  if (a.length !== b.length) return false;
  return crypto.timingSafeEqual(a, b);
}

module.exports = { hmacHex, safeEqualHex };
