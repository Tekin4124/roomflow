// Public preview: bounded, temporary, cookie-isolated synthetic data. No Graph or disk stores.
const crypto = require('node:crypto');
const fs = require('node:fs/promises');
const path = require('node:path');
const { createDemoCalendar } = require('../backend/demoService');
const { buildRoomStatus } = require('../backend/calendarStatus');
const COOKIE = 'roomflow_preview';
const DEFAULT_KEY = 'local-demo-key';
const JSON_LIMIT = 8192;
function problem(status, message) { return Object.assign(new Error(message), { status }); }
function createPublicSandbox({ origin, secureCookies = true, ttlMs = 3600000, maxSessions = 500, now = Date.now } = {}) {
  const site = new URL(origin);
  if (!['http:', 'https:'].includes(site.protocol) || site.username || site.password || site.pathname !== '/') throw new Error('Invalid PUBLIC_ORIGIN');
  const sessions = new Map();
  const clients = new Map();
  function cleanup() {
    const time = now();
    for (const [id, state] of sessions) if (time - state.touched > ttlMs) sessions.delete(id);
    for (const [ip, entry] of clients) if (entry.until <= time) clients.delete(ip);
  }
  const timer = setInterval(cleanup, 60000); timer.unref();
  function send(res, status, data) {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' });
    res.end(JSON.stringify(data));
  }
  function session(req, res) {
    cleanup();
    // Only deploy behind the loopback-bound service and trusted Caddy proxy.
    const ip = String(req.headers['x-forwarded-for'] || req.socket.remoteAddress || '').split(',')[0].trim();
    let rate = clients.get(ip);
    if (!rate) {
      if (clients.size >= 2000) throw problem(429, 'Biraz sonra tekrar deneyin.');
      rate = { count: 0, until: now() + 60000 }; clients.set(ip, rate);
    }
    if (++rate.count > 600) throw problem(429, 'Çok fazla istek; biraz sonra tekrar deneyin.');
    const candidate = String(req.headers.cookie || '').split(';').map(v => v.trim()).find(v => v.startsWith(COOKIE + '='))?.slice(COOKIE.length + 1);
    let state = /^[a-f0-9]{64}$/.test(candidate || '') ? sessions.get(candidate) : null;
    if (!state) {
      if (sessions.size >= maxSessions) throw problem(503, 'Deneme ortamı dolu; biraz sonra tekrar deneyin.');
      const id = crypto.randomBytes(32).toString('hex');
      state = { touched: now(), rooms: [{ name: 'Atlas', email: 'atlas@example.invalid' }, { name: 'Orion', email: 'orion@example.invalid' }], devices: new Map(), calendar: createDemoCalendar(), logs: [] };
      sessions.set(id, state);
      res.setHeader('Set-Cookie', `${COOKIE}=${id}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${Math.ceil(ttlMs / 1000)}${secureCookies ? '; Secure' : ''}`);
    }
    state.touched = now(); return state;
  }
  function log(state, message) {
    state.logs.push({ timestamp: new Date(now()).toISOString(), level: 'info', message });
    if (state.logs.length > 100) state.logs.shift();
  }
  function room(state, email) {
    const found = state.rooms.find(r => r.email === String(email || '').trim().toLowerCase());
    if (!found) throw problem(404, 'Oda bulunamadı.'); return found;
  }
  function deviceId(value) {
    if (!/^[A-Za-z0-9._:-]{1,128}$/.test(value) || ['__proto__', 'constructor', 'prototype'].includes(value)) throw problem(400, 'Geçersiz cihaz kimliği.');
    return value;
  }
  async function body(req) {
    let length = 0; const parts = [];
    for await (const part of req) {
      length += part.length; if (length > JSON_LIMIT) throw problem(413, 'İstek çok büyük.'); parts.push(part);
    }
    let value;
    try { value = JSON.parse(Buffer.concat(parts).toString() || '{}'); } catch { throw problem(400, 'Geçersiz JSON.'); }
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw problem(400, 'Geçersiz JSON nesnesi.');
    return value;
  }
  async function handle(req, res) {
    const pathname = new URL(req.url, site).pathname;
    const supported = pathname.startsWith('/api/') || pathname.startsWith('/admin') || pathname.startsWith('/rooms') || pathname.startsWith('/devices') || ['/demo/config', '/health'].includes(pathname);
    if (!supported) return false;
    try {
      if (req.headers.origin && req.headers.origin !== site.origin) throw problem(403, 'Origin reddedildi.');
      const adminNavigation = pathname === '/admin' && ['GET', 'HEAD'].includes(req.method) && !req.headers.origin && [undefined, 'navigate'].includes(req.headers['sec-fetch-mode']);
      if (!adminNavigation && req.headers['sec-fetch-site'] === 'cross-site') throw problem(403, 'Siteler arası istek reddedildi.');
      if (['/health', '/api/health'].includes(pathname) && req.method === 'GET') { send(res, 200, { ok: true, mode: 'demo', sandbox: true, status: 'ok', roomCount: 2, warnings: [], clientSecret: null }); return true; }
      const state = session(req, res);
      if (pathname === '/admin' && ['GET', 'HEAD'].includes(req.method)) {
        let html = await fs.readFile(path.join(__dirname, '../backend/admin.html'), 'utf8');
        html = html.replace('<div class="auth-bar">', '<div class="auth-bar" style="display:none">').replace('id="newRoomEmail" placeholder="E-posta"', 'id="newRoomEmail" placeholder="room@example.invalid"');
        html = html.replace('<body>', '<body><div style="padding:12px 20px;background:#153e3a;color:#d7fff7">Bu oturumdaki değişiklikler yalnız size görünür. Gerçek kişi veya kurum bilgisi girmeyin. · <a href="/" style="color:inherit">Takvime dön</a> · <a href="/apk" style="color:inherit">APK indir</a></div>');
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Content-Security-Policy': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'" });
        res.end(req.method === 'HEAD' ? undefined : html); return true;
      }
      if (['/api/config', '/demo/config'].includes(pathname) && req.method === 'GET') { send(res, 200, { mode: 'demo', sandbox: true, backendUrl: site.origin, apiKey: DEFAULT_KEY, rooms: state.rooms }); return true; }
      if (pathname === '/rooms' && req.method === 'GET') { send(res, 200, state.rooms); return true; }
      if (pathname === '/devices' && req.method === 'GET') { send(res, 200, [...state.devices.values()]); return true; }
      if (pathname === '/admin/logs' && req.method === 'GET') { send(res, 200, { entries: state.logs, capacity: 100 }); return true; }
      if (pathname === '/admin/logs' && req.method === 'DELETE') { state.logs = []; send(res, 200, { ok: true }); return true; }
      if (pathname === '/admin/rooms' && req.method === 'POST') {
        const data = await body(req); const name = String(data.name || '').trim(); const email = String(data.email || '').trim().toLowerCase();
        if (!name || name.length > 64 || !/^[a-z0-9._+-]{1,64}@[a-z0-9.-]{1,128}\.invalid$/.test(email)) throw problem(400, 'Kısa bir oda adı ve room@example.invalid gibi örnek e-posta kullanın.');
        if (state.rooms.length >= 20) throw problem(409, 'En fazla 20 örnek oda oluşturabilirsiniz.');
        if (state.rooms.some(r => r.email === email)) throw problem(409, 'Oda zaten mevcut.');
        state.rooms.push({ name, email }); log(state, 'Örnek oda eklendi.'); send(res, 201, { name, email }); return true;
      }
      let match = pathname.match(/^\/admin\/rooms\/([^/]+)$/);
      if (match && req.method === 'DELETE') {
        const target = room(state, decodeURIComponent(match[1]));
        if ([...state.devices.values()].some(d => d.roomEmail === target.email)) throw problem(409, 'Önce odaya bağlı örnek cihazları kaldırın.');
        state.rooms = state.rooms.filter(r => r !== target); log(state, 'Örnek oda silindi.'); send(res, 200, { ok: true }); return true;
      }
      match = pathname.match(/^\/rooms\/([^/]+)\/(events|status)$/);
      if (match && req.method === 'GET') {
        const target = room(state, decodeURIComponent(match[1])); const events = await state.calendar.getRoomEvents(target.email);
        send(res, 200, match[2] === 'events' ? { roomEmail: target.email, roomName: target.name, events, lastUpdated: new Date(now()).toISOString() } : buildRoomStatus(target.email, events, now())); return true;
      }
      if (['/api/events', '/api/register', '/api/scenario'].includes(pathname) && req.method === 'POST') {
        const data = await body(req);
        if (data.backendUrl !== site.origin) throw problem(400, 'Bu ortam yalnız kendi örnek kayıtlarına bağlanır.');
        const target = room(state, data.roomEmail);
        if (pathname === '/api/events') { send(res, 200, { roomEmail: target.email, roomName: target.name, events: await state.calendar.getRoomEvents(target.email), lastUpdated: new Date(now()).toISOString() }); return true; }
        if (pathname === '/api/scenario') { state.calendar.setScenario(target.email, data.scenario); log(state, 'Takvim senaryosu değiştirildi.'); send(res, 200, { ok: true }); return true; }
        const id = deviceId(String(data.deviceId || 'web-display'));
        if (!state.devices.has(id) && state.devices.size >= 50) throw problem(409, 'Örnek cihaz sınırına ulaşıldı.');
        const record = { deviceId: id, roomEmail: target.email, label: String(data.deviceLabel || 'Web ekranı').slice(0, 64), lastSeenAt: new Date(now()).toISOString() };
        state.devices.set(id, record); send(res, 200, { ok: true, ...record }); return true;
      }
      match = pathname.match(/^\/devices\/([^/]+)(?:\/(assign-room|register))?$/);
      if (match) {
        const id = deviceId(decodeURIComponent(match[1]));
        if (req.method === 'DELETE' && !match[2]) { if (!state.devices.delete(id)) throw problem(404, 'Cihaz bulunamadı.'); send(res, 200, { ok: true }); return true; }
        if (req.method === 'POST' && match[2]) {
          const data = await body(req); const target = room(state, data.roomEmail);
          if (match[2] === 'assign-room' && !state.devices.has(id)) throw problem(404, 'Cihaz bulunamadı.');
          if (!state.devices.has(id) && state.devices.size >= 50) throw problem(409, 'Örnek cihaz sınırına ulaşıldı.');
          const old = state.devices.get(id);
          const record = { deviceId: id, roomEmail: target.email, label: String(data.deviceLabel || old?.label || 'Web ekranı').slice(0, 64), lastSeenAt: new Date(now()).toISOString() };
          state.devices.set(id, record); send(res, 200, { ok: true, ...record }); return true;
        }
      }
      throw problem(404, 'Bu deneme ortamında böyle bir uç yok.');
    } catch (error) { send(res, error.status || error.statusCode || (error instanceof URIError ? 400 : 500), { error: (error.status || error.statusCode) ? error.message : 'İstek işlenemedi.' }); }
    return true;
  }
  return { handle, close() { clearInterval(timer); }, sessionCount() { cleanup(); return sessions.size; } };
}
module.exports = { createPublicSandbox };
