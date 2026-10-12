const { spawn } = require('node:child_process');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const children = [];
let stopping = false;
function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  for (const child of children) child.kill('SIGTERM');
  process.exitCode = code;
}
for (const [directory, port] of [['backend', process.env.BACKEND_PORT || '3000'], ['web-display', process.env.WEB_PORT || '8088']]) {
  const child = spawn(process.execPath, ['server.js'], {
    cwd: path.join(root, directory), stdio: 'inherit',
    env: { ...process.env, PORT: port, HOST: process.env.HOST || '127.0.0.1', BACKEND_URL: process.env.BACKEND_URL || `http://127.0.0.1:${process.env.BACKEND_PORT || '3000'}` }
  });
  children.push(child);
  child.on('error', error => { console.error(error.message); stop(1); });
  child.on('exit', code => { if (!stopping) stop(code || 1); });
}
process.on('SIGINT', () => stop());
process.on('SIGTERM', () => stop());
