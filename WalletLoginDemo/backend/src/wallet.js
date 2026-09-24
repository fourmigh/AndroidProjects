// Google Wallet API 封装：创建 Pass Class / Object，并签发 "Add to Google Wallet" JWT。
const fs = require('node:fs');
const path = require('node:path');
const { google } = require('googleapis');
const jwt = require('jsonwebtoken');

const SCOPE = 'https://www.googleapis.com/auth/wallet_object.issuer';

function config() {
  const issuerId = process.env.ISSUER_ID;
  const classSuffix = process.env.CLASS_SUFFIX;
  const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || './service-account.json';
  if (!issuerId) throw new Error('缺少环境变量 ISSUER_ID');
  if (!classSuffix) throw new Error('缺少环境变量 CLASS_SUFFIX');
  const resolved = path.isAbsolute(credPath) ? credPath : path.resolve(process.cwd(), credPath);
  if (!fs.existsSync(resolved)) {
    throw new Error(`找不到服务账号密钥文件：${resolved}`);
  }
  return { issuerId, classSuffix, credentialPath: resolved };
}

/**
 * 是否处于模拟模式（无需 Google 凭据）：
 * - 显式设置 MOCK_WALLET=true，或
 * - 缺少 ISSUER_ID / 服务账号密钥文件
 */
function isMock() {
  if (String(process.env.MOCK_WALLET).toLowerCase() === 'true') return true;
  if (!process.env.ISSUER_ID) return true;
  const credPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || './service-account.json';
  const resolved = path.isAbsolute(credPath) ? credPath : path.resolve(process.cwd(), credPath);
  return !fs.existsSync(resolved);
}

function readServiceAccount(credentialPath) {
  return JSON.parse(fs.readFileSync(credentialPath, 'utf-8'));
}

function getService() {
  const { credentialPath } = config();
  const key = readServiceAccount(credentialPath);
  const auth = new google.auth.JWT({
    email: key.client_email,
    key: key.private_key,
    scopes: [SCOPE],
  });
  return google.walletobjects({ version: 'v1', auth });
}

async function ensureClass() {
  const { issuerId, classSuffix } = config();
  const service = getService();
  const classId = `${issuerId}.${classSuffix}`;

  try {
    await service.genericclass.get({ resourceId: classId });
    return classId;
  } catch (err) {
    const status = err?.code || err?.response?.status;
    if (status !== 404) throw err;
  }

  await service.genericclass.insert({
    requestBody: {
      id: classId,
      issuerName: 'Wallet Login Demo',
      reviewStatus: 'UNDER_REVIEW',
      hexBackgroundColor: '#1a73e8',
      cardTitle: {
        defaultValue: { language: 'zh-CN', value: '登录卡' },
      },
    },
  });
  return classId;
}

/**
 * 创建或更新登录卡 Pass Object。
 * 密钥写入 object 并 insert 到钱包，JWT 里只引用 objectId，避免密钥随 JWT 泄漏。
 */
async function upsertObject(objectId, { userId, totpKey, periodMillis }) {
  const { issuerId, classSuffix } = config();
  const service = getService();
  const classId = `${issuerId}.${classSuffix}`;

  const requestBody = {
    id: objectId,
    classId,
    state: 'ACTIVE',
    cardTitle: {
      defaultValue: { language: 'zh-CN', value: '登录卡' },
    },
    header: {
      defaultValue: { language: 'zh-CN', value: '扫码登录' },
    },
    subheader: {
      defaultValue: { language: 'zh-CN', value: `用户 ${userId}` },
    },
    rotatingBarcode: {
      type: 'QR_CODE',
      valuePattern: `WLOGIN-${userId}-{totp_timestamp_seconds}-{totp_value_0}`,
      alternateText: `用户 ${userId}`,
      totpDetails: {
        algorithm: 'TOTP_SHA1',
        periodMillis: String(periodMillis),
        parameters: [{ key: totpKey, valueLength: 8 }],
      },
    },
  };

  try {
    await service.genericobject.get({ resourceId: objectId });
    await service.genericobject.patch({ resourceId: objectId, requestBody });
  } catch (err) {
    const status = err?.code || err?.response?.status;
    if (status !== 404) throw err;
    await service.genericobject.insert({ requestBody });
  }

  return objectId;
}

/**
 * 用服务账号私钥签发 savetowallet JWT。
 * @returns {{ jwt: string, saveUrl: string }}
 */
function signSaveUrl(objectId) {
  const { credentialPath } = config();
  const key = readServiceAccount(credentialPath);
  const claims = {
    iss: key.client_email,
    aud: 'google',
    typ: 'savetowallet',
    payload: { genericObjects: [{ id: objectId }] },
  };
  const token = jwt.sign(claims, key.private_key, { algorithm: 'RS256' });
  return {
    jwt: token,
    saveUrl: `https://pay.google.com/gp/v/save/${token}`,
  };
}

module.exports = { isMock, ensureClass, upsertObject, signSaveUrl };
