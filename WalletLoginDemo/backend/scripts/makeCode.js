// 离线造码：为已绑定用户生成当前时刻的动态二维码内容，便于命令行自测登录。
// 用法：node scripts/makeCode.js <userId>
require('dotenv').config();
const totp = require('../src/totp');
const users = require('../src/users');

const userId = process.argv[2];
if (!userId) {
  console.error('用法: node scripts/makeCode.js <userId>');
  process.exit(1);
}

const user = users.getUser(userId);
if (!user) {
  console.error(`用户 ${userId} 不存在，请先调用 POST /api/bind-pass 绑定`);
  process.exit(1);
}

const periodSeconds = Math.floor(Number(process.env.TOTP_PERIOD_MILLIS || 30000) / 1000);
const timestamp = Math.floor(Date.now() / 1000);
const otp = totp.totp(user.totpKey, timestamp, periodSeconds, 8);

console.log(`WLOGIN-${userId}-${timestamp}-${otp}`);
