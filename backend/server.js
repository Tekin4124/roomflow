const { isDemo } = require('./config');

const logBuffer = require('./logBuffer');
logBuffer.install();

const crypto = require('crypto');
const express = require('express');
const fs = require('fs');
const http = require('http');
const https = require('https');
const path = require('path');
const {
  getRoomStatus,
  getRoomEvents,
  invalidateRoomEventsCache,
  getClientSecretExpiryInfo,
} = require(isDemo ? './demoService' : './graphService');
const {
  assignRoom,
  getRoom,
  hasDevice,
  isSafeDeviceId,
  listDevicesByRoom,
  removeDevice,
  listDevices,
} = require('./deviceStore');
const {
  getRooms,
  getRoomByEmail,
  addRoom: addRoomToStore,
  removeRoom: removeRoomFromStore,
  normalizeRoomEmail: normalizeRoomEmailStore,
} = require('./roomStore');

const app = express();

app.disable('x-powered-by');
// Reverse proxy (nginx/PM2) arkasinda calisirsa req.ip dogru gelsin diye loopback'e guven.
// Internet'e dogrudan acik degilse zararsiz.
app.set('trust proxy', 'loopback');
app.use(express.json({ limit: '8kb' }));

const parseCsvEnv = (value) => String(value ?? '')
  .split(',')
  .map((item) => item.trim())
  .filter(Boolean);
const allowedCorsOrigins = new Set(parseCsvEnv(process.env.CORS_ORIGINS));
const REQUEST_RATE_LIMIT_WINDOW_MS = 60000;
const REQUEST_RATE_LIMIT_MAX = Math.max(60, Number(process.env.REQUEST_RATE_LIMIT_MAX ?? 240));
const AUTH_RATE_LIMIT_WINDOW_MS = 300000;
const AUTH_RATE_LIMIT_MAX = Math.max(5, Number(process.env.AUTH_RATE_LIMIT_MAX ?? 20));
const requestRateEntries = new Map();
const authRateEntries = new Map();

// Süresi dolmuş rate limit kayıtlarını temizle (her 5 dakikada bir)
setInterval(() => {
  const now = Date.now();
  [requestRateEntries, authRateEntries].forEach((store) => {
    for (const [key, entry] of store) {
      if (entry.resetAt <= now) {
        store.delete(key);
      }
    }
  });
}, 300000);

const getServerConfigErrors = () => {
  const errors = [];

  if (!process.env.API_KEY?.trim()) {
    errors.push('API_KEY eksik.');
  }

  if (getRooms().length === 0) {
    errors.push('ROOMS listesi bos.');
  }

  return errors;
};

// --- Async error wrapper ---
const asyncHandler = (fn) => (req, res, next) => {
  Promise.resolve(fn(req, res, next)).catch(next);
};

const buildHttpError = (statusCode, message) => {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
};

const getAllowedRoom = (roomEmail) => getRoomByEmail(roomEmail);

const requireAllowedRoom = (roomEmail) => {
  const room = getAllowedRoom(roomEmail);

  if (!room) {
    throw buildHttpError(404, 'Oda izinli listede bulunamadi.');
  }

  return room;
};

const ensureValidDeviceId = (deviceId) => {
  if (!isSafeDeviceId(deviceId)) {
    throw buildHttpError(400, 'Gecersiz cihaz kimligi.');
  }
};

const apiKeyMatches = (providedApiKey, expectedApiKey) => {
  const providedBuffer = Buffer.from(String(providedApiKey ?? ''), 'utf8');
  const expectedBuffer = Buffer.from(String(expectedApiKey ?? ''), 'utf8');

  if (providedBuffer.length !== expectedBuffer.length) {
    return false;
  }

  return crypto.timingSafeEqual(providedBuffer, expectedBuffer);
};

const getClientKey = (req) => req.ip || req.socket?.remoteAddress || 'unknown';

const consumeRateLimit = (store, key, windowMs, maxRequests) => {
  const now = Date.now();
  const currentEntry = store.get(key);
  const activeEntry = (!currentEntry || currentEntry.resetAt <= now)
    ? { count: 0, resetAt: now + windowMs }
    : currentEntry;

  activeEntry.count += 1;
  store.set(key, activeEntry);

  if (activeEntry.resetAt <= now) {
    activeEntry.resetAt = now + windowMs;
    activeEntry.count = 1;
  }

  if (activeEntry.count > maxRequests) {
    return Math.ceil((activeEntry.resetAt - now) / 1000);
  }

  return 0;
};

const setCorsHeaders = (res, origin) => {
  res.setHeader('Access-Control-Allow-Origin', origin);
  res.setHeader('Vary', 'Origin');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, x-api-key');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,DELETE,OPTIONS');
};

const isSameOriginRequest = (req, origin) => {
  try {
    return new URL(origin).origin === `${req.protocol}://${req.get('host')}`;
  } catch {
    return false;
  }
};

const browserOriginGuard = (req, res, next) => {
  const origin = req.headers.origin;

  if (!origin) {
    return next();
  }

  if (isSameOriginRequest(req, origin)) {
    setCorsHeaders(res, origin);

    if (req.method === 'OPTIONS') {
      return res.status(204).end();
    }

    return next();
  }

  if (!allowedCorsOrigins.has(origin)) {
    return res.status(403).json({
      error: 'Tarayici erisimi bu origin icin kapali.',
    });
  }

  setCorsHeaders(res, origin);

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  next();
};

const requestRateLimiter = (req, res, next) => {
  if (req.path === '/health') {
    return next();
  }

  const retryAfterSeconds = consumeRateLimit(
    requestRateEntries,
    getClientKey(req),
    REQUEST_RATE_LIMIT_WINDOW_MS,
    REQUEST_RATE_LIMIT_MAX
  );

  if (retryAfterSeconds > 0) {
    res.setHeader('Retry-After', retryAfterSeconds);
    return res.status(429).json({ error: 'Cok fazla istek gonderildi.' });
  }

  next();
};

// --- API Key dogrulamasi ---
const authenticateApiKey = (req, res, next) => {
  if (req.path === '/health') return next();

  const apiKey = req.headers['x-api-key'];
  const expectedApiKey = process.env.API_KEY?.trim();

  if (!expectedApiKey) {
    return res.status(503).json({
      error: 'Sunucu guvenli sekilde yapilandirilmamis. API_KEY eksik.',
    });
  }

  if (typeof apiKey !== 'string' || !apiKeyMatches(apiKey, expectedApiKey)) {
    const retryAfterSeconds = consumeRateLimit(
      authRateEntries,
      getClientKey(req),
      AUTH_RATE_LIMIT_WINDOW_MS,
      AUTH_RATE_LIMIT_MAX
    );

    if (retryAfterSeconds > 0) {
      res.setHeader('Retry-After', retryAfterSeconds);
      return res.status(429).json({ error: 'Gecici olarak cok fazla hatali deneme yapildi.' });
    }

    return res.status(401).json({ error: 'Yetkisiz erisim' });
  }

  next();
};

// Admin paneli yalnizca sayfayi sunar; API istekleri x-api-key ile korunur
app.get('/admin', (req, res) => {
  res.sendFile(path.join(__dirname, 'admin.html'));
});

app.get('/demo/config', (req, res) => {
  if (!isDemo) return res.status(404).json({ error: 'Not found' });
  res.json({ mode: 'demo', apiKey: process.env.API_KEY === 'local-demo-key' ? 'local-demo-key' : '', rooms: getRooms() });
});
app.use(browserOriginGuard);
app.use(requestRateLimiter);
app.use(authenticateApiKey);

// --- Endpointler ---

app.get('/health', (req, res) => {
  const configErrors = getServerConfigErrors();
  const secretExpiry = getClientSecretExpiryInfo();

  // CLIENT_SECRET expire olduysa veya 30 günden az kaldıysa health sinyalini düşür
  const secretIsProblem = secretExpiry?.isExpired === true;
  const isHealthy = configErrors.length === 0 && !secretIsProblem;

  res.status(isHealthy ? 200 : 503).json({
    mode: isDemo ? 'demo' : 'graph',
    status: isHealthy ? 'ok' : 'degraded',
    timestamp: new Date().toISOString(),
    roomCount: getRooms().length,
    isSecurelyConfigured: configErrors.length === 0,
    clientSecret: secretExpiry,
    warnings: [
      ...configErrors,
      ...(secretExpiry?.isExpired ? ['CLIENT_SECRET suresi dolmus.'] : []),
      ...(secretExpiry?.isExpiringSoon && !secretExpiry.isExpired
        ? [`CLIENT_SECRET ${secretExpiry.daysRemaining} gun icinde dolacak.`]
        : []),
    ],
  });
});

app.post('/demo/scenario', (req, res, next) => {
  if (!isDemo) return res.status(404).json({ error: 'Not found' });
  try {
    const room = requireAllowedRoom(req.body.roomEmail);
    require('./demoService').setScenario(room.email, req.body.scenario);
    res.json({ ok: true });
  } catch (error) { next(error); }
});
app.get('/rooms', (req, res) => {
  res.json(getRooms());
});

// --- Oda Yonetimi (Admin) ---
app.post('/admin/rooms', (req, res) => {
  const { name, email } = req.body || {};

  try {
    const room = addRoomToStore(name, email);
    res.status(201).json(room);
  } catch (err) {
    res.status(400).json({ error: err.message });
  }
});

app.delete('/admin/rooms/:roomEmail', (req, res) => {
  const roomEmail = normalizeRoomEmailStore(req.params.roomEmail);
  const assignedDevices = listDevicesByRoom(roomEmail);

  if (assignedDevices.length > 0) {
    return res.status(409).json({
      error: `Bu oda ${assignedDevices.length} cihaza atanmis. Once cihazlari baska odaya tasiyin veya silin.`,
      devices: assignedDevices.map((device) => device.deviceId),
    });
  }

  const removed = removeRoomFromStore(roomEmail);

  if (!removed) {
    return res.status(404).json({ error: 'Oda bulunamadi.' });
  }

  invalidateRoomEventsCache(roomEmail);
  res.json({ ok: true });
});

app.get('/admin/logs', (req, res) => {
  res.json({
    entries: logBuffer.getEntries(),
    capacity: logBuffer.MAX_ENTRIES,
  });
});

app.delete('/admin/logs', (req, res) => {
  logBuffer.clear();
  res.json({ ok: true });
});

// Yeni endpoint: Frontend'in ihtiyaci olan ham event listesi
app.get('/rooms/:roomEmail/events', asyncHandler(async (req, res) => {
  const room = requireAllowedRoom(req.params.roomEmail);

  const events = await getRoomEvents(room.email);
  const meaningfulEvents = events
    .filter((event) => event?.showAs !== 'free')
    .sort((left, right) => (
      new Date(left.start.dateTime) - new Date(right.start.dateTime)
    ));

  res.json({
    roomEmail: room.email,
    roomName: room.name,
    events: meaningfulEvents,
    lastUpdated: new Date().toISOString(),
  });
}));

// Mevcut endpoint: Islenip ozet halinde durum
app.get('/rooms/:roomEmail/status', asyncHandler(async (req, res) => {
  const room = requireAllowedRoom(req.params.roomEmail);
  const result = await getRoomStatus(room.email);
  res.json(result);
}));

app.post('/devices/:deviceId/register', asyncHandler(async (req, res) => {
  const { deviceId } = req.params;
  const { roomEmail, deviceLabel } = req.body || {};

  ensureValidDeviceId(deviceId);

  if (!roomEmail) {
    throw buildHttpError(400, 'roomEmail gerekli');
  }

  const room = requireAllowedRoom(roomEmail);
  const registeredDevice = assignRoom(deviceId, room.email, {
    label: deviceLabel,
    lastSeenAt: new Date().toISOString(),
  });

  res.json({ ok: true, ...registeredDevice });
}));

app.post('/devices/:deviceId/assign-room', asyncHandler(async (req, res) => {
  const { deviceId } = req.params;
  const { roomEmail } = req.body || {};

  ensureValidDeviceId(deviceId);

  if (!roomEmail) {
    throw buildHttpError(400, 'roomEmail gerekli');
  }

  const room = requireAllowedRoom(roomEmail);
  assignRoom(deviceId, room.email);

  res.json({ ok: true, deviceId, roomEmail: room.email });
}));

app.get('/devices/:deviceId/status', asyncHandler(async (req, res) => {
  const { deviceId } = req.params;

  ensureValidDeviceId(deviceId);

  const roomEmail = getRoom(deviceId);

  if (!roomEmail) {
    return res.status(404).json({ error: 'Cihaz atanmamis' });
  }

  const room = requireAllowedRoom(roomEmail);
  const result = await getRoomStatus(room.email);
  res.json(result);
}));

app.get('/devices', (req, res) => {
  res.json(listDevices());
});

app.delete('/devices/:deviceId', (req, res) => {
  const { deviceId } = req.params;

  if (!isSafeDeviceId(deviceId)) {
    return res.status(400).json({ error: 'Gecersiz cihaz kimligi.' });
  }

  if (!hasDevice(deviceId)) {
    return res.status(404).json({ error: 'Cihaz bulunamadi' });
  }

  removeDevice(deviceId);
  res.json({ ok: true });
});

// --- Hata yakalama ---
app.use((err, req, res, _next) => {
  const statusCode = err?.statusCode || err?.response?.status || 500;
  const safeCode = statusCode >= 400 && statusCode < 600 ? statusCode : 500;
  const level = safeCode >= 500 ? 'HATA' : 'UYARI';
  const target = `${req.method} ${req.path}`;

  console[safeCode >= 500 ? 'error' : 'warn'](
    `[${new Date().toISOString()}] ${level}: ${target} -> ${safeCode} - ${err?.message || err}`
  );

  // 4xx hatalarda kendi yazdığımız mesajı dön, 5xx'lerde detay sızdırma
  const message = safeCode < 500
    ? (err?.message || 'Istek hatasi')
    : 'Sunucu hatasi';

  res.status(safeCode).json({ error: message });
});

// --- Baslat ---
const PORT = process.env.PORT || 3000;

const createAppServer = () => {
  const httpsKeyPath = process.env.HTTPS_KEY_PATH?.trim();
  const httpsCertPath = process.env.HTTPS_CERT_PATH?.trim();

  if (!httpsKeyPath && !httpsCertPath) {
    return {
      server: http.createServer(app),
      protocol: 'http',
    };
  }

  if (!httpsKeyPath || !httpsCertPath) {
    throw new Error('HTTPS_KEY_PATH ve HTTPS_CERT_PATH birlikte tanimlanmali.');
  }

  return {
    server: https.createServer(
      {
        key: fs.readFileSync(httpsKeyPath),
        cert: fs.readFileSync(httpsCertPath),
      },
      app
    ),
    protocol: 'https',
  };
};

const { server, protocol } = createAppServer();

server.listen(PORT, process.env.HOST || '127.0.0.1', () => {
  console.log(`MeetingRoom Backend ${protocol.toUpperCase()} port ${PORT} uzerinde calisiyor`);
  console.log(`Tanimli oda sayisi: ${getRooms().length}`);

  const configErrors = getServerConfigErrors();

  configErrors.forEach((error) => {
    console.warn(`UYARI: ${error}`);
  });

  if (protocol !== 'https') {
    console.log('HTTP listener: keep local or configure TLS before remote use.');
  }
});
