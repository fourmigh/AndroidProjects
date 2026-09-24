require('dotenv').config();
const express = require('express');
const bindPass = require('./routes/bindPass');
const login = require('./routes/login');
const mock = require('./routes/mock');
const nfc = require('./routes/nfc');
const wallet = require('./wallet');

const app = express();
app.use(express.json());

app.get('/health', (req, res) => res.json({ ok: true }));
app.use('/api', bindPass);
app.use('/api', login);
app.use('/api', mock);
app.use('/api', nfc);

const port = Number(process.env.PORT || 3000);
app.listen(port, '0.0.0.0', () => {
  console.log(`Wallet Login Demo backend listening on http://0.0.0.0:${port}`);
  console.log(`Wallet mode: ${wallet.isMock() ? 'MOCK (无需 Google 凭据)' : 'REAL (Google Wallet)'}`);
});
