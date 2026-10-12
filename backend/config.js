const path = require('node:path');
const fs = require('node:fs');
require('dotenv').config({ path: path.join(__dirname, '.env') });
const provider = process.env.CALENDAR_PROVIDER || 'demo';
if (!['demo', 'graph'].includes(provider)) throw new Error('CALENDAR_PROVIDER must be demo or graph.');
const isDemo = provider === 'demo';
if (isDemo) {
  process.env.API_KEY ||= 'local-demo-key';
  process.env.ROOMS ||= 'Atlas:atlas@example.invalid,Orion:orion@example.invalid';
} else {
  for (const key of ['API_KEY', 'TENANT_ID', 'CLIENT_ID', 'CLIENT_SECRET', 'ROOMS']) {
    if (!process.env[key]?.trim()) throw new Error(`Required configuration missing: ${key}`);
  }
  if (process.env.API_KEY === 'local-demo-key') throw new Error('Choose a private API_KEY for Graph mode.');
}
const dataDir = path.resolve(process.env.DATA_DIR || path.join(__dirname, '.data', provider));
fs.mkdirSync(dataDir, { recursive: true });
module.exports = { isDemo, provider, dataDir };
