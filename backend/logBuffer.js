// In-memory ring buffer of recent warn/error logs. Server restart sifirlar;
// kalici log icin pm2 logs meetingroom kullanin. Amac: SSH atmadan
// admin panelden son hatalari gormek.

const MAX_ENTRIES = 200;
const entries = [];
let installed = false;

function record(level, message) {
  entries.push({
    timestamp: new Date().toISOString(),
    level,
    message,
  });

  if (entries.length > MAX_ENTRIES) {
    entries.splice(0, entries.length - MAX_ENTRIES);
  }
}

function getEntries() {
  // En yeni en ustte goruluyor
  return entries.slice().reverse();
}

function clear() {
  entries.length = 0;
}

function formatArgs(args) {
  return args.map((arg) => {
    if (arg instanceof Error) {
      return arg.stack || arg.message;
    }
    if (typeof arg === 'object' && arg !== null) {
      try {
        return JSON.stringify(arg);
      } catch (_) {
        return String(arg);
      }
    }
    return String(arg);
  }).join(' ');
}

function install() {
  if (installed) return;
  installed = true;

  const originalWarn = console.warn.bind(console);
  const originalError = console.error.bind(console);

  console.warn = (...args) => {
    try { record('warn', formatArgs(args)); } catch (_) { /* asla yutmasak da log icin sessiz */ }
    originalWarn(...args);
  };

  console.error = (...args) => {
    try { record('error', formatArgs(args)); } catch (_) { /* ignore */ }
    originalError(...args);
  };
}

module.exports = { install, record, getEntries, clear, MAX_ENTRIES };
