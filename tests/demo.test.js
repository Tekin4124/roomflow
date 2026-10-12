const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const { mkdtemp, rm } = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const net = require('node:net');
let backend, web, directory;
const children = [];
async function freePort() { const s = net.createServer(); await new Promise(r=>s.listen(0,'127.0.0.1',r)); const p=s.address().port; await new Promise(r=>s.close(r));return p; }
async function wait(url) {for(let i=0;i<100;i++){try{if((await fetch(url)).ok)return;}catch(_){}await new Promise(r=>setTimeout(r,50));}throw new Error(`Startup failed: ${url}`);}
const root = path.resolve(__dirname,'..');
function start(dir, env) { const child=spawn(process.execPath,['server.js'],{cwd:path.join(root,dir),env:{...process.env,...env},stdio:'pipe'});children.push(child); return child; }
before(async()=>{
 directory=await mkdtemp(path.join(os.tmpdir(),'roomflow-test-'));
 const backendPort=await freePort(); const webPort=await freePort();
 backend=`http://127.0.0.1:${backendPort}`;web=`http://127.0.0.1:${webPort}`;
 start('backend',{CALENDAR_PROVIDER:'demo',API_KEY:'local-demo-key',ROOMS:'Atlas:atlas@example.invalid,Orion:orion@example.invalid',DATA_DIR:directory,PORT:String(backendPort),HOST:'127.0.0.1'});
 await wait(`${backend}/health`);
 start('web-display',{PORT:String(webPort),HOST:'127.0.0.1',BACKEND_URL:backend});await wait(`${web}/api/health`);
});
after(async()=>{await Promise.all(children.map(child=>new Promise(resolve=>{if(child.exitCode!==null)return resolve();child.once('exit',resolve);child.kill();})));await rm(directory,{recursive:true,force:true});});
function auth(method='GET',body){return {method,headers:{'x-api-key':'local-demo-key','Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})};}
function webBody(extra={}){return {backendUrl:backend,apiKey:'local-demo-key',roomEmail:'atlas@example.invalid',...extra};}
function post(body){return {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)};}
test('clean install provides two synthetic rooms and automatic local configuration',async()=>{
 const config=await fetch(`${web}/api/config`).then(r=>r.json());assert.equal(config.mode,'demo');assert.equal(config.rooms.length,2);assert.ok(config.rooms.every(r=>r.email.endsWith('@example.invalid')));assert.equal(config.apiKey,'local-demo-key');
});
test('unauthorized callers and unknown rooms are rejected',async()=>{
 assert.equal((await fetch(`${backend}/rooms`)).status,401);
 assert.equal((await fetch(`${backend}/rooms/missing%40example.invalid/events`,auth())).status,404);
});
test('busy, available and empty scenarios work end to end',async()=>{
 for(const [scenario,occupied,count] of [['busy',true,3],['available',false,2],['empty',false,0]]){
  assert.equal((await fetch(`${web}/api/scenario`,post(webBody({scenario})))).status,200);
  const status=await fetch(`${backend}/rooms/atlas%40example.invalid/status`,auth()).then(r=>r.json());assert.equal(status.isOccupied,occupied);
  const result=await fetch(`${web}/api/events`,post(webBody())).then(r=>r.json());assert.equal(result.events.length,count);
 }
 assert.equal((await fetch(`${web}/api/scenario`,post(webBody({scenario:'invalid'})))).status,400);
});
test('device registration blocks deletion of an assigned room',async()=>{
 const added=await fetch(`${backend}/admin/rooms`,auth('POST',{name:'Test Room',email:'test@example.invalid'}));assert.equal(added.status,201);
 const registered=await fetch(`${backend}/devices/test-device/register`,auth('POST',{roomEmail:'test@example.invalid',deviceLabel:'Test display'}));assert.equal(registered.status,200);
 assert.equal((await fetch(`${backend}/admin/rooms/test%40example.invalid`,auth('DELETE'))).status,409);
 assert.equal((await fetch(`${backend}/devices/test-device`,auth('DELETE'))).status,200);
 assert.equal((await fetch(`${backend}/admin/rooms/test%40example.invalid`,auth('DELETE'))).status,200);
});
test('web proxy cannot be used to request arbitrary destinations',async()=>{
 assert.equal((await fetch(`${web}/api/events`,post(webBody({backendUrl:'http://169.254.169.254'})))).status,400);
 const options=post(webBody());options.headers.Origin='https://untrusted.example';assert.equal((await fetch(`${web}/api/events`,options)).status,403);
});
test('web server refuses private source paths and oversized requests',async()=>{
 for(const url of ['/server.js','/package.json','/assets/../server.js','/%E0%A4%A'])assert.equal((await fetch(web+url)).status,404);
 assert.equal((await fetch(`${web}/api/events`,post(webBody({padding:'x'.repeat(9000)})))).status,413);
});
test('Graph mode fails before listening when credentials are absent',async()=>{
 const child=spawn(process.execPath,['server.js'],{cwd:path.join(root,'backend'),env:{...process.env,CALENDAR_PROVIDER:'graph',API_KEY:'',TENANT_ID:'',CLIENT_ID:'',CLIENT_SECRET:'',ROOMS:''},stdio:'pipe'});
 let output='';child.stderr.on('data',chunk=>output+=chunk);const code=await new Promise(resolve=>child.once('exit',resolve));assert.notEqual(code,0);assert.match(output,/Required configuration missing/);
});
