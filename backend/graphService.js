require('dotenv').config();

const axios = require('axios');

let cachedToken = null;
let tokenExpiresAt = 0;

const TOKEN_BUFFER_MS = 300000;
const CALENDAR_LOOKAHEAD_DAYS = Math.max(1, Number(process.env.CALENDAR_LOOKAHEAD_DAYS ?? 7));
const ROOM_EVENTS_CACHE_TTL_MS = Math.max(
  5_000,
  Number(process.env.ROOM_EVENTS_CACHE_TTL_MS ?? 30_000)
);

// roomEmail -> { events, fetchedAt }
const roomEventsCache = new Map();
// roomEmail -> Promise (in-flight isteği paylaş, thundering herd'i önle)
const inFlightRoomFetches = new Map();

function getClientSecretExpiryInfo() {
  const raw = process.env.CLIENT_SECRET_EXPIRES_AT?.trim();
  if (!raw) return null;

  const expiryDate = new Date(raw);
  if (Number.isNaN(expiryDate.getTime())) {
    return { error: 'CLIENT_SECRET_EXPIRES_AT gecersiz tarih formati. ISO 8601 kullanin (ornek: 2026-12-31).' };
  }

  const daysRemaining = Math.floor((expiryDate.getTime() - Date.now()) / 86_400_000);
  return {
    expiresAt: expiryDate.toISOString(),
    daysRemaining,
    isExpiringSoon: daysRemaining <= 30,
    isExpired: daysRemaining < 0,
  };
}

async function getAccessToken(forceRefresh = false) {
  if (
    cachedToken &&
    !forceRefresh &&
    Date.now() < tokenExpiresAt - TOKEN_BUFFER_MS
  ) {
    return cachedToken;
  }

  const tenantId = process.env.TENANT_ID;
  const clientId = process.env.CLIENT_ID;
  const clientSecret = process.env.CLIENT_SECRET;

  if (!tenantId || !clientId || !clientSecret) {
    throw new Error(
      'TENANT_ID, CLIENT_ID veya CLIENT_SECRET .env dosyasinda eksik. Lutfen kontrol edin.'
    );
  }

  const tokenUrl = `https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/token`;
  const requestBody = new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: clientId,
    client_secret: clientSecret,
    scope: 'https://graph.microsoft.com/.default',
  });

  try {
    const response = await axios.post(tokenUrl, requestBody.toString(), {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      timeout: 10000,
    });

    const accessToken = response?.data?.access_token;
    const expiresInSeconds = Number(response?.data?.expires_in ?? 0);

    if (!accessToken || !expiresInSeconds) {
      throw new Error('Microsoft token yaniti eksik. access_token veya expires_in bulunamadi.');
    }

    cachedToken = accessToken;
    tokenExpiresAt = Date.now() + (expiresInSeconds * 1000);

    return cachedToken;
  } catch (error) {
    cachedToken = null;
    tokenExpiresAt = 0;

    if (error?.response?.status === 401) {
      throw new Error('Azure kimlik bilgileri gecersiz. CLIENT_ID ve CLIENT_SECRET kontrol edin.');
    }

    if (error?.response?.status === 400) {
      throw new Error('Azure token istegi hatali. TENANT_ID, CLIENT_ID ve CLIENT_SECRET dogru mu?');
    }

    throw error;
  }
}

async function requestRoomEvents(roomEmail, token) {
  const now = new Date();
  const startDate = new Date(now);
  const endDate = new Date(now);

  startDate.setHours(0, 0, 0, 0);
  endDate.setDate(endDate.getDate() + (CALENDAR_LOOKAHEAD_DAYS - 1));
  endDate.setHours(23, 59, 59, 999);

  return axios.get(
    `https://graph.microsoft.com/v1.0/users/${encodeURIComponent(roomEmail)}/calendarView`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        Prefer: 'outlook.timezone="Turkey Standard Time"',
      },
      params: {
        startDateTime: startDate.toISOString(),
        endDateTime: endDate.toISOString(),
        $select: 'subject,organizer,start,end,showAs',
        $orderby: 'start/dateTime asc',
        $top: 100,
      },
      timeout: 10000,
    }
  );
}

async function fetchRoomEventsFromGraph(roomEmail) {
  try {
    const token = await getAccessToken();
    const response = await requestRoomEvents(roomEmail, token);
    return response?.data?.value ?? [];
  } catch (error) {
    if (error?.response?.status === 401) {
      const freshToken = await getAccessToken(true);
      const retryResponse = await requestRoomEvents(roomEmail, freshToken);
      return retryResponse?.data?.value ?? [];
    }

    if (error?.response?.status === 404) {
      throw new Error(`Oda bulunamadi: ${roomEmail}. E-posta adresini kontrol edin.`);
    }

    throw error;
  }
}

async function getRoomEvents(roomEmail) {
  const cached = roomEventsCache.get(roomEmail);
  if (cached && Date.now() - cached.fetchedAt < ROOM_EVENTS_CACHE_TTL_MS) {
    return cached.events;
  }

  // Aynı oda için zaten devam eden bir istek varsa onu paylaş (N tablet ayni anda istek atarsa Graph'e tek cagri gider)
  const inFlight = inFlightRoomFetches.get(roomEmail);
  if (inFlight) {
    return inFlight;
  }

  const fetchPromise = fetchRoomEventsFromGraph(roomEmail)
    .then((events) => {
      roomEventsCache.set(roomEmail, { events, fetchedAt: Date.now() });
      return events;
    })
    .finally(() => {
      inFlightRoomFetches.delete(roomEmail);
    });

  inFlightRoomFetches.set(roomEmail, fetchPromise);
  return fetchPromise;
}

function invalidateRoomEventsCache(roomEmail) {
  if (roomEmail) {
    roomEventsCache.delete(roomEmail);
  } else {
    roomEventsCache.clear();
  }
}

function getOrganizer(event) {
  return (
    event?.organizer?.emailAddress?.name ||
    event?.organizer?.emailAddress?.address ||
    'Bilinmeyen Kullanici'
  );
}

function formatTime(isoString) {
  const date = new Date(isoString);
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

function formatDuration(startISO, endISO) {
  const mins = Math.round((new Date(endISO) - new Date(startISO)) / 60000);
  const hours = Math.floor(mins / 60);
  const minutes = mins % 60;

  if (hours === 0) {
    return `${minutes}dk`;
  }

  if (minutes === 0) {
    return `${hours}s`;
  }

  return `${hours}s ${minutes}dk`;
}

async function getRoomStatus(roomEmail) {
  return require('./calendarStatus').buildRoomStatus(roomEmail, await getRoomEvents(roomEmail));
}

module.exports = {
  getRoomStatus,
  getRoomEvents,
  invalidateRoomEventsCache,
  getClientSecretExpiryInfo,
};
