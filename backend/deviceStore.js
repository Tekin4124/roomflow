const fs = require('fs');
const path = require('path');

const STORE_FILE = path.join(require('./config').dataDir, 'devices.json');
const STORE_TEMP_FILE = `${STORE_FILE}.tmp`;
const DEVICE_ID_PATTERN = /^[A-Za-z0-9._:-]{1,128}$/;
const MAX_LABEL_LENGTH = 64;
const BLOCKED_DEVICE_IDS = new Set(['__proto__', 'constructor', 'prototype']);
// lastSeenAt yazma sıklığı: ayni cihaz icin 5 dakikadan once tekrar yazma
const LAST_SEEN_WRITE_DEBOUNCE_MS = 5 * 60 * 1000;

function normalizeRoomEmail(roomEmail) {
  return String(roomEmail ?? '').trim().toLowerCase();
}

function normalizeDeviceLabel(label) {
  return String(label ?? '')
    .trim()
    .replace(/\s+/g, ' ')
    .slice(0, MAX_LABEL_LENGTH);
}

function isSafeDeviceId(deviceId) {
  return (
    typeof deviceId === 'string' &&
    DEVICE_ID_PATTERN.test(deviceId) &&
    !BLOCKED_DEVICE_IDS.has(deviceId)
  );
}

function toDeviceStore(rawStore) {
  const sanitizedStore = Object.create(null);

  if (!rawStore || typeof rawStore !== 'object' || Array.isArray(rawStore)) {
    return sanitizedStore;
  }

  Object.entries(rawStore).forEach(([deviceId, rawEntry]) => {
    const normalizedEntry = normalizeDeviceEntry(rawEntry);

    if (isSafeDeviceId(deviceId) && normalizedEntry) {
      sanitizedStore[deviceId] = normalizedEntry;
    }
  });

  return sanitizedStore;
}

function normalizeDeviceEntry(rawEntry) {
  if (typeof rawEntry === 'string') {
    const roomEmail = normalizeRoomEmail(rawEntry);

    return roomEmail
      ? { roomEmail, label: '', lastSeenAt: null }
      : null;
  }

  if (!rawEntry || typeof rawEntry !== 'object' || Array.isArray(rawEntry)) {
    return null;
  }

  const roomEmail = normalizeRoomEmail(rawEntry.roomEmail);

  if (!roomEmail) {
    return null;
  }

  const lastSeenAt = typeof rawEntry.lastSeenAt === 'string' && rawEntry.lastSeenAt.trim()
    ? rawEntry.lastSeenAt.trim()
    : null;

  return {
    roomEmail,
    label: normalizeDeviceLabel(rawEntry.label),
    lastSeenAt,
  };
}

function loadStore() {
  try {
    if (fs.existsSync(STORE_FILE)) {
      const raw = fs.readFileSync(STORE_FILE, 'utf-8');
      return toDeviceStore(JSON.parse(raw));
    }
  } catch (error) {
    console.error('Cihaz verisi okunamadi, bos store ile devam ediliyor:', error.message);
  }

  return Object.create(null);
}

function saveStore(data) {
  // Atomic write: önce .tmp'ye yaz, sonra rename. Yazma ortasinda crash olursa
  // gercek dosya bozulmaz; en kotu ihtimalle yarim .tmp kalir.
  try {
    fs.writeFileSync(STORE_TEMP_FILE, JSON.stringify(data, null, 2), 'utf-8');
    fs.renameSync(STORE_TEMP_FILE, STORE_FILE);
  } catch (error) {
    console.error('Cihaz verisi kaydedilemedi:', error.message);
    try { fs.unlinkSync(STORE_TEMP_FILE); } catch (_) { /* ignore */ }
  }
}

let devices = loadStore();

function assignRoom(deviceId, roomEmail, options = {}) {
  if (!isSafeDeviceId(deviceId)) {
    throw new Error('Gecersiz cihaz kimligi.');
  }

  const normalizedRoomEmail = normalizeRoomEmail(roomEmail);

  if (!normalizedRoomEmail) {
    throw new Error('Gecersiz oda e-postasi.');
  }

  const existing = devices[deviceId] ?? {
    roomEmail: normalizedRoomEmail,
    label: '',
    lastSeenAt: null,
  };
  const normalizedLabel = normalizeDeviceLabel(options.label);
  const finalLabel = normalizedLabel || existing.label || '';
  const finalLastSeenAt = options.lastSeenAt ?? existing.lastSeenAt ?? null;

  const isNewDevice = !devices[deviceId];
  const roomChanged = existing.roomEmail !== normalizedRoomEmail;
  const labelChanged = existing.label !== finalLabel;
  const lastSeenChangedSignificantly = isLastSeenWriteDue(
    existing.lastSeenAt,
    finalLastSeenAt
  );

  devices[deviceId] = {
    roomEmail: normalizedRoomEmail,
    label: finalLabel,
    lastSeenAt: finalLastSeenAt,
  };

  // Sadece anlamli bir degisiklik varsa diske yaz. Tabletler dakikada bir register
  // ediyor; her seferinde lastSeenAt yazimi disk I/O katlar.
  if (isNewDevice || roomChanged || labelChanged || lastSeenChangedSignificantly) {
    saveStore(devices);
  }

  return {
    deviceId,
    roomEmail: normalizedRoomEmail,
    label: devices[deviceId].label,
    lastSeenAt: devices[deviceId].lastSeenAt,
  };
}

function isLastSeenWriteDue(previousIso, nextIso) {
  if (!nextIso) return false;
  if (!previousIso) return true;

  const previousTime = Date.parse(previousIso);
  const nextTime = Date.parse(nextIso);

  if (Number.isNaN(previousTime) || Number.isNaN(nextTime)) {
    return true;
  }

  return nextTime - previousTime >= LAST_SEEN_WRITE_DEBOUNCE_MS;
}

function getRoom(deviceId) {
  if (!isSafeDeviceId(deviceId)) {
    return null;
  }

  return devices[deviceId]?.roomEmail ?? null;
}

function removeDevice(deviceId) {
  if (!isSafeDeviceId(deviceId)) {
    return false;
  }

  const existed = Object.prototype.hasOwnProperty.call(devices, deviceId);

  delete devices[deviceId];
  saveStore(devices);
  return existed;
}

function listDevices() {
  return Object.entries(devices)
    .map(([deviceId, entry]) => ({
      deviceId,
      roomEmail: entry.roomEmail,
      label: entry.label,
      lastSeenAt: entry.lastSeenAt,
    }))
    .sort((left, right) => {
      const leftKey = `${left.label || ''} ${left.deviceId}`.toLowerCase();
      const rightKey = `${right.label || ''} ${right.deviceId}`.toLowerCase();
      return leftKey.localeCompare(rightKey, 'tr');
    });
}

function listDevicesByRoom(roomEmail) {
  const normalizedRoomEmail = normalizeRoomEmail(roomEmail);

  return listDevices().filter((device) => device.roomEmail === normalizedRoomEmail);
}

function hasDevice(deviceId) {
  return isSafeDeviceId(deviceId) &&
    Object.prototype.hasOwnProperty.call(devices, deviceId);
}

module.exports = {
  assignRoom,
  getRoom,
  hasDevice,
  isSafeDeviceId,
  listDevicesByRoom,
  removeDevice,
  listDevices,
};
