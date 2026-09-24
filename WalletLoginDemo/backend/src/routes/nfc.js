// NFC 登录（HCE）：
//   POST /api/nfc/bind            绑定 / 追加 NFC 凭证（按当前配置）
//   POST /api/nfc/challenge       申请 challenge（可选；readerAuth 开启时校验读卡器）
//   POST /api/nfc/verify          校验 HMAC / 签名，签发会话
//   GET  /api/nfc/bindings        已绑定用户列表
//   POST /api/nfc/unbind          解绑（清该用户全部凭证）
//   GET  /api/session/validate    会话校验
//   GET/PUT /api/nfc/config       安全开关配置
//
// 兼容策略：verify 按【用户记录的模式】逐一尝试；读卡器相关开关「能过就过」。
const crypto = require('node:crypto');
const express = require('express');
const { hmacHex, safeEqualHex } = require('../hmac');
const { verifyEcdsa } = require('../ecdsa');
const totp = require('../totp');
const users = require('../users');
const { getConfig, setConfig } = require('../nfcConfig');

const router = express.Router();

// 内存态：challenge 与 session（重启即失效）
const challenges = new Map();     // 未使用的 challenge -> { expiresAt, readerToken }
const usedChallenges = new Map(); // 已使用的 challenge -> expiresAt（防重放）
const sessions = new Map();       // token -> { userId, expiresAt }

// 读卡器认证：宽容策略——配置开启时才强制校验
function readerAllowed(req) {
  const cfg = getConfig();
  if (!cfg.readerAuth) return true;
  const expected = process.env.NFC_READER_TOKEN || '';
  const provided = req.get('X-Reader-Token') || '';
  return Boolean(expected) && provided === expected;
}

// 收集候选凭证：id 可能是 userId，也可能是某个凭证的 opaqueId
function collectCandidates(id) {
  const all = users.readAll();
  const out = [];
  const byUser = all[id];
  if (byUser) {
    for (const cred of byUser.nfcCredentials || []) out.push({ user: byUser, cred });
  }
  for (const u of Object.values(all)) {
    if (u.userId === id) continue;
    for (const cred of u.nfcCredentials || []) {
      if (cred.opaqueId === id) out.push({ user: u, cred });
    }
  }
  return out;
}

router.post('/nfc/bind', (req, res) => {
  try {
    const cfg = getConfig();
    const userId = String(req.body?.userId || '').trim();
    const name = String(req.body?.name || '').trim() || `用户${userId}`;
    const publicKey = String(req.body?.publicKey || '').trim();
    if (!userId) {
      return res.status(400).json({ ok: false, error: 'userId 不能为空' });
    }

    let user = users.getUser(userId);
    if (!user) {
      user = {
        userId,
        name,
        totpKey: totp.generateKey(),
        createdAt: new Date().toISOString(),
        nfcCredentials: [],
      };
    }
    if (!Array.isArray(user.nfcCredentials)) user.nfcCredentials = [];

    const mode = cfg.useEcdsa ? 'ecdsa' : 'hmac';
    const idType = cfg.useOpaqueId ? 'opaqueId' : 'userId';
    if (mode === 'ecdsa' && !publicKey) {
      return res.status(400).json({ ok: false, error: 'ECDSA 模式需要 publicKey' });
    }

    const credential = { mode, idType, createdAt: new Date().toISOString() };
    if (mode === 'hmac') {
      credential.key = crypto.randomBytes(32).toString('hex');
    } else {
      credential.publicKey = publicKey;
    }
    if (idType === 'opaqueId') {
      credential.opaqueId = crypto.randomBytes(16).toString('hex');
    }

    user.nfcCredentials.push(credential);
    users.upsertUser(user);

    res.json({
      ok: true,
      userId,
      mode,
      idType,
      opaqueId: credential.opaqueId,
      nfcKey: credential.key,
    });
  } catch (err) {
    console.error('[nfc/bind]', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

router.post('/nfc/challenge', (req, res) => {
  try {
    if (!readerAllowed(req)) {
      return res.status(403).json({ ok: false, error: '读卡器未授权' });
    }
    const cfg = getConfig();
    const ttl = Number(cfg.challengeTtlMs) || 10000;
    const challenge = crypto.randomBytes(16).toString('hex');
    challenges.set(challenge, {
      expiresAt: Date.now() + ttl,
      readerToken: req.get('X-Reader-Token') || '',
    });
    res.json({ ok: true, challenge, expiresInMs: ttl });
  } catch (err) {
    console.error('[nfc/challenge]', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

router.post('/nfc/verify', (req, res) => {
  try {
    const cfg = getConfig();
    const id = String(req.body?.id || '').trim();
    const challenge = String(req.body?.challenge || '').trim().toLowerCase();
    const proof = String(req.body?.proof || '').trim().toLowerCase();
    if (!id || !challenge || !proof) {
      return res.status(400).json({ ok: false, error: '参数不完整（需要 id/challenge/proof）' });
    }
    if (!readerAllowed(req)) {
      return res.status(403).json({ ok: false, error: '读卡器未授权' });
    }

    // challenge 宽容校验：未用过则校验并标记已用；已用过拒重放；从未下发则按配置决定
    const now = Date.now();
    const usedAt = usedChallenges.get(challenge);
    if (usedAt && usedAt > now) {
      return res.status(409).json({ ok: false, error: 'challenge 已被使用' });
    }
    const rec = challenges.get(challenge);
    if (rec) {
      if (rec.expiresAt < now) {
        challenges.delete(challenge);
        return res.status(409).json({ ok: false, error: 'challenge 已过期' });
      }
      challenges.delete(challenge);
      usedChallenges.set(challenge, now + (Number(cfg.challengeTtlMs) || 10000));
    } else if (cfg.serverChallenge) {
      return res.status(400).json({ ok: false, error: 'challenge 非服务端下发' });
    }

    const candidates = collectCandidates(id);
    if (candidates.length === 0) {
      return res.status(404).json({ ok: false, error: '未找到绑定凭证' });
    }

    const challengeBuf = Buffer.from(challenge, 'hex');
    const proofBuf = Buffer.from(proof, 'hex');
    let matched = null;
    for (const { user, cred } of candidates) {
      if (cred.mode === 'ecdsa') {
        if (verifyEcdsa(cred.publicKey, challengeBuf, proofBuf)) {
          matched = user;
          break;
        }
      } else if (safeEqualHex(hmacHex(cred.key, challengeBuf), proof)) {
        matched = user;
        break;
      }
    }
    if (!matched) {
      return res.status(401).json({ ok: false, error: '验证失败（HMAC/签名不匹配）' });
    }

    const result = { ok: true, user: { userId: matched.userId, name: matched.name } };
    if (Number(cfg.sessionTtlMs) > 0) {
      const token = crypto.randomBytes(24).toString('hex');
      const expiresAt = now + Number(cfg.sessionTtlMs);
      sessions.set(token, { userId: matched.userId, expiresAt });
      result.sessionToken = token;
      result.expiresAt = expiresAt;
    }
    res.json(result);
  } catch (err) {
    console.error('[nfc/verify]', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

router.get('/nfc/bindings', (req, res) => {
  const all = users.readAll();
  const bindings = Object.values(all)
    .filter((u) => Array.isArray(u.nfcCredentials) && u.nfcCredentials.length > 0)
    .map((u) => ({
      userId: u.userId,
      name: u.name || `用户${u.userId}`,
      credentials: u.nfcCredentials.map((c) => ({
        mode: c.mode,
        idType: c.idType,
        opaqueId: c.opaqueId,
      })),
    }));
  res.json({ ok: true, bindings });
});

router.post('/nfc/unbind', (req, res) => {
  try {
    const userId = String(req.body?.userId || '').trim();
    if (!userId) {
      return res.status(400).json({ ok: false, error: 'userId 不能为空' });
    }
    const user = users.getUser(userId);
    if (!user) {
      return res.status(404).json({ ok: false, error: '用户不存在' });
    }
    user.nfcCredentials = [];
    users.upsertUser(user);
    res.json({ ok: true, userId });
  } catch (err) {
    console.error('[nfc/unbind]', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

router.get('/session/validate', (req, res) => {
  const token = String(req.query.token || '').trim();
  const rec = sessions.get(token);
  if (!rec || rec.expiresAt < Date.now()) {
    return res.status(401).json({ ok: false, error: '会话无效或已过期' });
  }
  res.json({ ok: true, userId: rec.userId, expiresAt: rec.expiresAt });
});

router.get('/nfc/config', (req, res) => {
  res.json({ ok: true, config: getConfig() });
});

router.put('/nfc/config', (req, res) => {
  try {
    const next = setConfig(req.body || {});
    res.json({ ok: true, config: next });
  } catch (err) {
    console.error('[nfc/config]', err);
    res.status(500).json({ ok: false, error: err.message });
  }
});

module.exports = router;
