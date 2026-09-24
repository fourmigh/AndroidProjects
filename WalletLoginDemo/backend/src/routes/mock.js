// 模拟模式辅助接口：在没有 Google 凭据时提供当前动态码，用于演示登录闭环。
const express = require('express');
const totp = require('../totp');
const users = require('../users');
const wallet = require('../wallet');

const router = express.Router();

// 当前是否处于模拟模式
router.get('/mock/status', (req, res) => {
  res.json({ ok: true, mock: wallet.isMock() });
});

// 返回指定用户当前时刻的动态码（与 Google Wallet 用同一 TOTP 算法）
router.get('/mock/code', (req, res) => {
  const userId = String(req.query.userId || '').trim();
  if (!userId) {
    return res.status(400).json({ ok: false, error: 'userId 不能为空' });
  }

  const user = users.getUser(userId);
  if (!user) {
    return res.status(404).json({ ok: false, error: '该用户尚未绑定登录卡' });
  }

  const periodSeconds = Math.floor(Number(process.env.TOTP_PERIOD_MILLIS || 30000) / 1000);
  const timestamp = Math.floor(Date.now() / 1000);
  const otp = totp.totp(user.totpKey, timestamp, periodSeconds, 8);
  const expiresInSeconds = periodSeconds - (timestamp % periodSeconds);

  res.json({
    ok: true,
    code: `WLOGIN-${userId}-${timestamp}-${otp}`,
    expiresInSeconds,
  });
});

module.exports = router;
