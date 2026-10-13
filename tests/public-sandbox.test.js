const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { createPublicSandbox } = require('../web-display/public-sandbox');
let server, sandbox, origin, clock = Date.now();
before(async () => {
  server = http.createServer((req, res) => sandbox.handle(req, res));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  origin = `http://127.0.0.1:${server.address().port}`;
  sandbox = createPublicSandbox({ origin, secureCookies: false, now: () => clock, ttlMs: 1000, maxSessions: 5 });
});
after(async () => { sandbox.close(); await new Promise(resolve => server.close(resolve)); });
async function request(path, cookie, method = 'GET', data, headers = {}) {
  const response = await fetch(origin + path, { method, headers: { ...(cookie ? { Cookie: cookie } : {}), ...headers, ...(data ? { 'Content-Type': 'application/json' } : {}) }, ...(data ? { body: JSON.stringify(data) } : {}) });
  return { response, data: await response.json(), cookie: response.headers.get('set-cookie')?.split(';')[0] || cookie };
}
let a, b;
test('separate visitors receive opaque HttpOnly cookies and independent calendar scenarios', async () => {
  const one = await request('/api/config'); const two = await request('/api/config'); a = one.cookie; b = two.cookie;
  assert.notEqual(a, b); assert.match(one.response.headers.get('set-cookie'), /HttpOnly; SameSite=Lax/);
  assert.equal(one.data.backendUrl, origin);
  const payload = { backendUrl: origin, roomEmail: 'atlas@example.invalid', scenario: 'empty' };
  assert.equal((await request('/api/scenario', a, 'POST', { ...payload, scenario: 'invalid' })).response.status, 400);
  assert.equal((await request('/api/scenario', a, 'POST', payload)).response.status, 200);
  assert.equal((await request('/api/events', a, 'POST', payload)).data.events.length, 0);
  assert.equal((await request('/api/events', b, 'POST', payload)).data.events.length, 3);
});
test('room creation, device reassignment, deletion and audit logs stay inside the visitor session', async () => {
  assert.equal((await request('/admin/rooms', a, 'POST', { name: 'Yeni oda', email: 'new@example.invalid' })).response.status, 201);
  assert.equal((await request('/rooms', b)).data.some(r => r.email === 'new@example.invalid'), false);
  assert.equal((await request('/api/register', a, 'POST', { backendUrl: origin, roomEmail: 'new@example.invalid', deviceId: 'tablet' })).response.status, 200);
  assert.equal((await request('/devices', b)).data.length, 0);
  assert.equal((await request('/admin/rooms/new%40example.invalid', a, 'DELETE')).response.status, 409);
  assert.equal((await request('/devices/tablet/assign-room', a, 'POST', { roomEmail: 'atlas@example.invalid' })).response.status, 200);
  assert.equal((await request('/admin/rooms/new%40example.invalid', a, 'DELETE')).response.status, 200);
  assert.ok((await request('/admin/logs', a)).data.entries.length > 0);
  assert.equal((await request('/admin/logs', b)).data.entries.length, 0);
});
test('real email addresses, malformed payloads, unsafe device IDs and arbitrary backends are rejected', async () => {
  assert.equal((await request('/admin/rooms', a, 'POST', { name: 'Test', email: 'person@gmail.com' })).response.status, 400);
  assert.equal((await request('/api/events', a, 'POST', { backendUrl: 'http://169.254.169.254', roomEmail: 'atlas@example.invalid' })).response.status, 400);
  assert.equal((await request('/devices/__proto__/register', a, 'POST', { roomEmail: 'atlas@example.invalid' })).response.status, 400);
  assert.equal((await request('/api/events', a, 'POST', { padding: 'x'.repeat(9000) })).response.status, 413);
  const response = await fetch(origin + '/api/events', { method: 'POST', headers: { Cookie: a }, body: '{' }); assert.equal(response.status, 400);
});
test('cross-site requests cannot mutate a visitor session', async () => {
  assert.equal((await request('/api/scenario', a, 'POST', { backendUrl: origin, roomEmail: 'atlas@example.invalid', scenario: 'busy' }, { Origin: 'https://untrusted.example' })).response.status, 403);
  assert.equal((await request('/rooms', a, 'GET', null, { 'Sec-Fetch-Site': 'cross-site' })).response.status, 403);
});
// Use node:http: recent fetch implementations overwrite Sec-Fetch-Mode with cors.
function navigationRequest(path, cookie) {
  return new Promise((resolve, reject) => {
    const req = http.get(origin + path, { headers: { Cookie: cookie, 'Sec-Fetch-Site': 'cross-site', 'Sec-Fetch-Mode': 'navigate' } }, res => {
      let body = ''; res.setEncoding('utf8'); res.on('data', chunk => body += chunk);
      res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body }));
      res.on('error', reject);
    });
    req.on('error', reject);
  });
}
test('admin navigation from an external link works without exposing cross-site API access', async () => {
  const response = await navigationRequest('/admin', a);
  assert.equal(response.status, 200); assert.match(response.headers['content-type'], /text\/html/);
  assert.match(response.body, /Bu oturumdaki değişiklikler/); assert.match(response.headers['content-security-policy'], /frame-ancestors 'none'/);
  assert.equal((await navigationRequest('/rooms', a)).status, 403);
});
test('unknown cookies cannot fixate a session and idle sessions expire', async () => {
  const unknown = 'roomflow_preview=' + 'a'.repeat(64);
  const fresh = await request('/api/config', unknown); assert.notEqual(fresh.cookie, unknown);
  clock += 1001;
  const reset = await request('/api/config', a); assert.notEqual(reset.cookie, a); assert.equal(reset.data.rooms.length, 2);
});
test('session allocation is bounded and request bursts are rate limited', async () => {
  for (let i = 0; i < 4; i++) assert.equal((await request('/api/config')).response.status, 200);
  assert.equal((await request('/api/config')).response.status, 503);
  let last;
  for (let i = 0; i < 610; i++) last = await request('/rooms', b);
  assert.equal(last.response.status, 429);
});
