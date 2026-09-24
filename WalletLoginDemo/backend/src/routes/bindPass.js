// 绑定登录卡：为指定用户创建 Pass 并返回可添加到 Google Wallet 的 JWT。
const express = require('express');
const totp = require('../totp');
const users = require('../users');
const wallet = require('../wallet');

const router = express.Router();

router.post('/bind-pass', async (req, res) => {
  try {
    const userId = String(req.body?.userId || '').trim();
    const name = String(req.body?.name || '').trim() || `用户${userId}`;
    if (!userId) {
      return res.status(400).json({ ok: false, error: 'userId 不能为空' });
    }

    const periodMillis = Number(process.env.TOTP_PERIOD_MILLIS || 30000);

    let user = users.getUser(userId);
    if (!user) {
      user = {
        userId,
        name,
        totpKey: totp.generateKey(),
        createdAt: new Date().toISOString(),
      };
      users.upsertUser(user);
    }

    if (wallet.isMock()) {
      // 模拟模式：不调用 Google Wallet API，仅登记用户，动态码由 /api/mock/code 提供
      return res.json({ ok: true, mock: true, userId: user.userId, name: user.name });
    }

    await wallet.ensureClass();
    const objectId = `${process.env.ISSUER_ID}.${userId}`;
    await wallet.upsertObject(objectId, {
      userId,
      totpKey: user.totpKey,
      periodMillis,
    });

    const { jwt, saveUrl } = wallet.signSaveUrl(objectId);
    res.json({ ok: true, mock: false, objectId, jwt, saveUrl });
  } catch (err) {
    console.error('[bind-pass]', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

module.exports = router;
