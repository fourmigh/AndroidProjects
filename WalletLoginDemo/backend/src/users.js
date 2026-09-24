// Demo 用的极简用户存储：JSON 文件持久化。
// 生产环境请替换为真实用户系统数据库。
const fs = require('node:fs');
const path = require('node:path');

const DATA_FILE = path.join(__dirname, '..', 'data', 'users.json');

function ensureFile() {
  const dir = path.dirname(DATA_FILE);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  if (!fs.existsSync(DATA_FILE)) fs.writeFileSync(DATA_FILE, '{}', 'utf-8');
}

function readAll() {
  ensureFile();
  try {
    return JSON.parse(fs.readFileSync(DATA_FILE, 'utf-8'));
  } catch {
    return {};
  }
}

function saveAll(data) {
  ensureFile();
  fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2), 'utf-8');
}

function getUser(userId) {
  return readAll()[userId] || null;
}

function upsertUser(user) {
  const all = readAll();
  all[user.userId] = user;
  saveAll(all);
  return user;
}

module.exports = { getUser, upsertUser, readAll, DATA_FILE };
