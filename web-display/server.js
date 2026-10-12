const fs = require('node:fs/promises');
const http = require('node:http');
const path = require('node:path');
const port = Number(process.env.PORT || 8088);
const host = process.env.HOST || '127.0.0.1';
const backend = new URL(process.env.BACKEND_URL || 'http://127.0.0.1:3000');
if (!['http:', 'https:'].includes(backend.protocol) || backend.username || backend.password) throw new Error('Invalid BACKEND_URL');
const staticFiles = new Map([
  ['/', ['index.html', 'text/html']], ['/display', ['index.html', 'text/html']], ['/setup', ['index.html', 'text/html']],
  ['/app.js', ['app.js', 'text/javascript']], ['/styles.css', ['styles.css', 'text/css']],
  ['/assets/favicon.svg', ['assets/favicon.svg', 'image/svg+xml']]
]);
function json(res, status, value) {
  res.writeHead(status, {'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
  res.end(JSON.stringify(value));
}
async function readJson(req) {
  const chunks = []; let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > 8192) { const error = new Error('Request too large'); error.status = 413; throw error; }
    chunks.push(chunk);
  }
  const data = JSON.parse(Buffer.concat(chunks).toString() || '{}');
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('Invalid object');
  return data;
}
async function proxy(route, options = {}) {
  return fetch(new URL(route, backend), {...options, redirect:'error', signal:AbortSignal.timeout(15000)});
}
const server = http.createServer(async (req, res) => {
  try {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    if (pathname.startsWith('/api/')) {
      if (req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) return json(res,403,{error:'Origin rejected'});
      if (req.headers['sec-fetch-site'] === 'cross-site') return json(res,403,{error:'Cross-site request rejected'});
      if (pathname === '/api/health' && req.method === 'GET') return json(res,200,{ok:true});
      if (pathname === '/api/config' && req.method === 'GET') {
        const upstream = await proxy('/demo/config');
        if (!upstream.ok) return json(res,200,{mode:'graph',backendUrl:backend.href.replace(/\/$/,'')});
        const config = await upstream.json();
        return json(res,200,{...config,backendUrl:backend.href.replace(/\/$/,'')});
      }
      if (!['/api/events','/api/register','/api/scenario'].includes(pathname)) return json(res,404,{error:'Not found'});
      if (req.method !== 'POST') return json(res,405,{error:'Method not allowed'});
      let body;
      try { body = await readJson(req); } catch(error) { return json(res,error.status || 400,{error:'Invalid JSON request'}); }
      if (String(body.backendUrl || '').replace(/\/$/,'') !== backend.href.replace(/\/$/,'')) return json(res,400,{error:'Backend URL must match server BACKEND_URL'});
      if (!body.apiKey || typeof body.apiKey !== 'string' || !body.roomEmail || typeof body.roomEmail !== 'string') return json(res,400,{error:'API key and room email required'});
      const headers = {'x-api-key':body.apiKey,'Content-Type':'application/json'};
      let route, options;
      if (pathname === '/api/events') {
        route = `/rooms/${encodeURIComponent(body.roomEmail)}/events`; options = {headers};
      } else if (pathname === '/api/register') {
        route = `/devices/${encodeURIComponent(body.deviceId || 'web-display')}/register`;
        options = {method:'POST',headers,body:JSON.stringify({roomEmail:body.roomEmail,deviceLabel:body.deviceLabel || 'Web Demo'})};
      } else {
        route = '/demo/scenario'; options = {method:'POST',headers,body:JSON.stringify({roomEmail:body.roomEmail,scenario:body.scenario})};
      }
      const upstream = await proxy(route, options);
      return json(res,upstream.status,await upstream.json());
    }
    if (!['GET','HEAD'].includes(req.method)) return json(res,405,{error:'Method not allowed'});
    const entry = staticFiles.get(pathname);
    if (!entry) return json(res,404,{error:'Not found'});
    const bytes = await fs.readFile(path.join(__dirname,entry[0]));
    res.writeHead(200,{'Content-Type':`${entry[1]}; charset=utf-8`,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff',
      'Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"});
    res.end(req.method === 'HEAD' ? undefined : bytes);
  } catch(error) {
    json(res,502,{error:'Backend unavailable. Start the demo backend and try again.'});
  }
});
server.listen(port, host, () => console.log(`RoomFlow web demo: http://${host}:${port}`));
