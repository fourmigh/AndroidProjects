// ECDSA 验签工具：用于 NFC 非对称签名模式。
const crypto = require('node:crypto');

/**
 * 用公钥（Base64 编码的 X.509/SPKI DER）验证 ECDSA-SHA256 签名。
 * @param {string} publicKeyBase64
 * @param {Buffer} message
 * @param {Buffer} signature DER 编码签名
 * @returns {boolean}
 */
function verifyEcdsa(publicKeyBase64, message, signature) {
  try {
    const publicKey = crypto.createPublicKey({
      key: Buffer.from(publicKeyBase64, 'base64'),
      format: 'der',
      type: 'spki',
    });
    return crypto.verify('sha256', message, publicKey, signature);
  } catch {
    return false;
  }
}

module.exports = { verifyEcdsa };
