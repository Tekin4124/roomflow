const fs = require('fs');
const path = require('path');

const STORE_FILE = path.join(require('./config').dataDir, 'rooms.json');
const STORE_TEMP_FILE = `${STORE_FILE}.tmp`;

const normalizeRoomEmail = (email) => String(email ?? '').trim().toLowerCase();
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function loadRooms() {
  try {
    if (fs.existsSync(STORE_FILE)) {
      const raw = JSON.parse(fs.readFileSync(STORE_FILE, 'utf-8'));
      if (Array.isArray(raw)) {
        return raw
          .filter((r) => r && typeof r.name === 'string' && typeof r.email === 'string')
          .map((r) => ({ name: r.name.trim(), email: normalizeRoomEmail(r.email) }))
          .filter((r) => r.name && r.email);
      }
    }
  } catch (err) {
    console.error('rooms.json okunamadi:', err.message);
  }
  return null;
}

function saveRooms(rooms) {
  // Atomic write: yarim yazimi onlemek icin .tmp + rename
  try {
    fs.writeFileSync(STORE_TEMP_FILE, JSON.stringify(rooms, null, 2), 'utf-8');
    fs.renameSync(STORE_TEMP_FILE, STORE_FILE);
  } catch (error) {
    console.error('rooms.json kaydedilemedi:', error.message);
    try { fs.unlinkSync(STORE_TEMP_FILE); } catch (_) { /* ignore */ }
    throw error;
  }
}

function parseRoomsFromEnv() {
  const raw = process.env.ROOMS || '';
  if (!raw.trim()) return [];

  return raw.split(',').map((entry) => {
    const sep = entry.indexOf(':');
    if (sep === -1) {
      return { name: entry.trim(), email: normalizeRoomEmail(entry) };
    }
    return {
      name: entry.slice(0, sep).trim(),
      email: normalizeRoomEmail(entry.slice(sep + 1)),
    };
  }).filter((r) => r.email);
}

// Ilk yuklemede: rooms.json varsa onu kullan, yoksa .env'den oku ve rooms.json olustur
let rooms = loadRooms();
if (!rooms) {
  rooms = parseRoomsFromEnv();
  saveRooms(rooms);
}

function getRooms() {
  return rooms;
}

function getRoomByEmail(email) {
  const normalized = normalizeRoomEmail(email);
  return rooms.find((r) => r.email === normalized) || null;
}

function addRoom(name, email) {
  const trimmedName = String(name ?? '').trim();
  const normalizedEmail = normalizeRoomEmail(email);

  if (!trimmedName) throw new Error('Oda adi bos olamaz.');
  if (!normalizedEmail || !EMAIL_PATTERN.test(normalizedEmail)) {
    throw new Error('Gecerli bir e-posta adresi girin.');
  }
  if (rooms.some((r) => r.email === normalizedEmail)) {
    throw new Error('Bu e-posta ile bir oda zaten mevcut.');
  }

  const room = { name: trimmedName, email: normalizedEmail };
  rooms.push(room);
  saveRooms(rooms);
  return room;
}

function removeRoom(email) {
  const normalizedEmail = normalizeRoomEmail(email);
  const index = rooms.findIndex((r) => r.email === normalizedEmail);
  if (index === -1) return false;

  rooms.splice(index, 1);
  saveRooms(rooms);
  return true;
}

function buildRoomsByEmail() {
  return new Map(rooms.map((r) => [r.email, r]));
}

module.exports = {
  getRooms,
  getRoomByEmail,
  addRoom,
  removeRoom,
  buildRoomsByEmail,
  normalizeRoomEmail,
};
