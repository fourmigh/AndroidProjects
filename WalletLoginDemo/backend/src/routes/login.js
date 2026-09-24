// 扫码登录：校验 Google Wallet 动态二维码，通过后签发会话。
const crypto = require('node:crypto');
const express = require('express');
const totp = require('../totp');
const users = require('../users');

const router = express.Router();

// 防重放：记录已使用的 (userId, 时间步)，过期自动视为可用。
const usedCodes = new Map();

const CODE_PATTERN = /^WLOGIN-(.+)-(\d+)-(\d+)$/;

router.post('/login/scan', (req, res) => {
  const code = String(req.body?.code || '').trim();
  const match = code.match(CODE_PATTERN);
  if (!match) {
    return res.status(400).json({ ok: false, error: '二维码格式不正确' });
  }

  const [, userId, timestampStr, otp] = match;
  const user = users.getUser(userId);
  if (!user) {
    return res.status(404).json({ ok: false, error: '该用户尚未绑定登录卡' });
  }

  const periodSeconds = Math.floor(Number(process.env.TOTP_PERIOD_MILLIS || 30000) / 1000);
  const timestamp = Number(timestampStr);

  if (!totp.verify(user.totpKey, timestamp, otp, periodSeconds, otp.length)) {
    return res.status(401).json({ ok: false, error: '动态码无效或已过期' });
  }

  const counter = Math.floor(timestamp / periodSeconds);
  const usedKey = `${userId}:${counter}`;
  const now = Date.now();
  const expiresAt = usedCodes.get(usedKey);
  if (expiresAt && expiresAt > now) {
    return res.status(409).json({ ok: false, error: '该动态码已被使用，请刷新二维码' });
  }
  usedCodes.set(usedKey, now + periodSeconds * 1000 * 3);

  const sessionToken = crypto.randomBytes(24).toString('hex');
  res.json({
    ok: true,
    sessionToken,
    user: { userId: user.userId, name: user.name },
  });
});

module.exports = router;
