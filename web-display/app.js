(function () {
  "use strict";

  const STORAGE_CONFIG = "meeting_room_web_config_v1";
  const STORAGE_DEVICE_ID = "meeting_room_web_device_id_v1";
  const DEFAULT_REFRESH_INTERVAL_SECONDS = 60;
  const REGISTER_HEARTBEAT_INTERVAL_MS = 12 * 60 * 60 * 1000;
  const SETUP_HOLD_DURATION_MS = 5000;
  const TURKEY_OFFSET_MINUTES = 180;

  const monthNames = [
    "Ocak",
    "Şubat",
    "Mart",
    "Nisan",
    "Mayıs",
    "Haziran",
    "Temmuz",
    "Ağustos",
    "Eylül",
    "Ekim",
    "Kasım",
    "Aralık"
  ];

  const dayFormatter = new Intl.DateTimeFormat("tr-TR", {
    timeZone: "Europe/Istanbul",
    weekday: "long"
  });

  const partsFormatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Istanbul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  });

  const backendDateRegex =
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d{1,9}))?(Z|[+-]\d{2}:?\d{2})?$/;

  const app = document.getElementById("app");
  const rememberedSubjects = new Map();

  const state = {
    isBootstrapping: true,
    isSetupMode: false,
    isSavingConfig: false,
    setupError: "",
    activeConfig: null,
    setupValues: emptyConfig(),
    currentTime: Date.now(),
    serverTimeOffset: 0,
    events: [],
    currentEvent: null,
    nextEvent: null,
    isOccupied: false,
    lastUpdated: null,
    errorMessage: null,
    totalToday: 0,
    remaining: 0,
    occupiedHours: "0 sa",
    refreshTimer: null,
    clockTimer: null,
    loadInFlight: false,
    lastRegisteredAt: 0,
    lastRegisteredFingerprint: null,
    deviceId: resolveDeviceId(),
    deviceLabel: resolveDeviceLabel()
  };

  function emptyConfig() {
    return {
      backendUrl: "",
      apiKey: "",
      roomName: "",
      roomEmail: "",
      refreshIntervalSeconds: DEFAULT_REFRESH_INTERVAL_SECONDS
    };
  }

  async function init() {
    let demoConfig = null;
    try { demoConfig = await fetch('/api/config').then(r => r.ok ? r.json() : null); } catch (_) {}
    state.demo = demoConfig?.mode === 'demo' && !!demoConfig.apiKey;
    state.demoRooms = demoConfig?.rooms || [];
    const queryConfig = null;
    cleanQueryString();
    const demoPreset = state.demo ? { backendUrl: demoConfig.backendUrl, apiKey: demoConfig.apiKey, roomName: demoConfig.rooms[0]?.name, roomEmail: demoConfig.rooms[0]?.email } : null;
    const storedConfig = loadStoredConfig();
    const initialConfig = normalizeConfig(demoPreset || storedConfig || emptyConfig());
    const validationError = validateConfig(initialConfig);

    state.isBootstrapping = false;
    state.setupValues = initialConfig;

    if (queryConfig && !validationError) {
      saveStoredConfig(initialConfig);
      cleanQueryString();
    }

    if (validationError) {
      state.isSetupMode = true;
    } else {
      state.activeConfig = initialConfig;
      state.isSetupMode = false;
      startPolling();
      loadEvents();
    }

    startClock();
    render();
  }

  function cleanQueryString() {
    const url = new URL(window.location.href);
    ["backendUrl", "apiKey", "roomName", "roomEmail", "refreshIntervalSeconds"].forEach((key) => {
      url.searchParams.delete(key);
    });
    window.history.replaceState({}, "", url.pathname + url.search + url.hash);
  }

  function loadStoredConfig() {
    try {
      const raw = localStorage.getItem(STORAGE_CONFIG);
      return raw ? JSON.parse(raw) : null;
    } catch (_) {
      return null;
    }
  }

  function saveStoredConfig(config) {
    localStorage.setItem(STORAGE_CONFIG, JSON.stringify(normalizeConfig(config)));
  }

  function resolveDeviceId() {
    const existing = localStorage.getItem(STORAGE_DEVICE_ID);
    if (existing) {
      return existing;
    }

    const generated =
      crypto && typeof crypto.randomUUID === "function"
        ? `web-${crypto.randomUUID()}`
        : `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;

    localStorage.setItem(STORAGE_DEVICE_ID, generated);
    return generated;
  }

  function resolveDeviceLabel() {
    const host = window.location.hostname || "localhost";
    return `Web Ekran - ${host}`;
  }

  function normalizeConfig(config) {
    const backendUrl = String(config.backendUrl || "")
      .split(/\r?\n/, 1)[0]
      .trim()
      .replace(/\/+$/, "")
      .replace(/\/(?:admin|health)(?:\/.*)?$/i, "");

    const refreshIntervalSeconds = Number(config.refreshIntervalSeconds) || DEFAULT_REFRESH_INTERVAL_SECONDS;

    return {
      backendUrl,
      apiKey: String(config.apiKey || "")
        .split(/\r?\n/, 1)[0]
        .replace(/\s/g, ""),
      roomName: String(config.roomName || "").trim(),
      roomEmail: String(config.roomEmail || "").trim(),
      refreshIntervalSeconds: clamp(refreshIntervalSeconds, 15, 300)
    };
  }

  function validateConfig(config) {
    const normalized = normalizeConfig(config);

    if (!normalized.backendUrl) {
      return "Sunucu adresi zorunludur.";
    }

    if (!isBackendUrlAllowed(normalized.backendUrl)) {
      return "Sunucu adresi HTTPS olmalı veya iç ağ IP adresi kullanılmalıdır.";
    }

    if (!normalized.apiKey) {
      return "API anahtarı zorunludur.";
    }

    if (!normalized.roomEmail) {
      return "Oda e-posta adresi zorunludur.";
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized.roomEmail)) {
      return "Oda e-posta adresi geçersiz.";
    }

    return null;
  }

  function isBackendUrlAllowed(rawUrl) {
    try {
      const url = new URL(rawUrl);
      const scheme = url.protocol.replace(":", "").toLowerCase();
      const host = url.hostname.toLowerCase();

      if (!host) {
        return false;
      }

      if (scheme === "https") {
        return true;
      }

      return scheme === "http" && isPrivateNetworkHost(host);
    } catch (_) {
      return false;
    }
  }

  function isPrivateNetworkHost(host) {
    if (host === "localhost") {
      return true;
    }

    const octets = host.split(".");
    if (octets.length !== 4) {
      return false;
    }

    const values = octets.map((part) => Number(part));
    if (values.some((value) => !Number.isInteger(value) || value < 0 || value > 255)) {
      return false;
    }

    const [first, second] = values;
    return (
      first === 10 ||
      first === 127 ||
      (first === 192 && second === 168) ||
      (first === 172 && second >= 16 && second <= 31)
    );
  }

  function startClock() {
    clearInterval(state.clockTimer);
    state.clockTimer = setInterval(() => {
      state.currentTime = Date.now();
      recalculate();
      if (!state.isSetupMode) {
        renderMain();
      }
    }, 1000);
  }

  function startPolling() {
    clearInterval(state.refreshTimer);

    const config = state.activeConfig;
    if (!config) {
      return;
    }

    state.refreshTimer = setInterval(() => {
      if (!state.isSetupMode) {
        loadEvents();
      }
    }, config.refreshIntervalSeconds * 1000);
  }

  async function loadEvents() {
    const config = state.activeConfig ? normalizeConfig(state.activeConfig) : null;
    if (!config || state.loadInFlight) {
      return;
    }

    state.loadInFlight = true;

    try {
      const now = Date.now();
      const fingerprint = `${config.backendUrl}|${config.apiKey}|${config.roomEmail}|${state.deviceLabel}`;
      const shouldRegister =
        fingerprint !== state.lastRegisteredFingerprint ||
        now - state.lastRegisteredAt >= REGISTER_HEARTBEAT_INTERVAL_MS;

      if (shouldRegister) {
        await postJson("/api/register", {
          backendUrl: config.backendUrl,
          apiKey: config.apiKey,
          roomEmail: config.roomEmail,
          deviceId: state.deviceId,
          deviceLabel: state.deviceLabel
        }).catch(() => null);

        state.lastRegisteredAt = now;
        state.lastRegisteredFingerprint = fingerprint;
      }

      const responseReceivedAt = Date.now();
      const response = await postJson("/api/events", {
        backendUrl: config.backendUrl,
        apiKey: config.apiKey,
        roomEmail: config.roomEmail
      });

      if (state.activeConfig?.roomEmail !== config.roomEmail || state.activeConfig?.backendUrl !== config.backendUrl) return;
      const lastUpdatedMillis = parseBackendDateTime(response.lastUpdated);
      if (lastUpdatedMillis !== null) {
        state.serverTimeOffset = lastUpdatedMillis - responseReceivedAt;
      }

      const events = Array.isArray(response.events)
        ? response.events
            .map(toRoomEvent)
            .filter(Boolean)
            .filter((event) => event.showAs.toLowerCase() !== "free")
            .sort((left, right) => left.startMillis - right.startMillis)
        : [];

      state.activeConfig = {
        ...config,
        roomName: config.roomName || String(response.roomName || "").trim(),
        roomEmail: config.roomEmail || String(response.roomEmail || "").trim()
      };
      state.events = restoreRememberedSubjects(events);
      state.lastUpdated = lastUpdatedMillis;
      state.errorMessage = null;
      recalculate();
      render();
    } catch (error) {
      state.errorMessage = toUserMessage(error);
      recalculate();
      render();
    } finally {
      state.loadInFlight = false;
      if (state.activeConfig && (state.activeConfig.roomEmail !== config.roomEmail || state.activeConfig.backendUrl !== config.backendUrl)) queueMicrotask(loadEvents);
    }
  }

  async function postJson(url, payload) {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "content-type": "application/json; charset=utf-8",
        "accept": "application/json"
      },
      body: JSON.stringify(payload)
    });

    const text = await response.text();
    let body = null;
    if (text.trim()) {
      try {
        body = JSON.parse(text);
      } catch (_) {
        body = text;
      }
    }

    if (!response.ok) {
      const error = new Error(
        body && typeof body === "object" && body.error
          ? body.error
          : "Sunucudan veri alınırken bir hata oluştu."
      );
      error.status = response.status;
      error.body = body;
      throw error;
    }

    return body || {};
  }

  function toUserMessage(error) {
    if (error && error.status === 401) {
      return "API anahtarı geçersiz. Lütfen ayarları kontrol edin.";
    }

    if (error && error.status === 404) {
      return "Oda bulunamadı. E-posta adresini kontrol edin.";
    }

    if (error && error.status === 502) {
      const bodyError = error.body && typeof error.body === "object" ? error.body.error : "";
      if (bodyError) {
        return bodyError;
      }
      return "Sunucuya erişilemedi. Adres ve ağ bağlantısını kontrol edin.";
    }

    if (error && error.message) {
      return error.message;
    }

    return "Beklenmeyen bir hata oluştu.";
  }

  function toRoomEvent(payload) {
    const startMillis = parseBackendDateTime(payload && payload.start && payload.start.dateTime);
    const endMillis = parseBackendDateTime(payload && payload.end && payload.end.dateTime);

    if (startMillis === null || endMillis === null || endMillis < startMillis) {
      return null;
    }

    const organizerEmail = String(
      payload && payload.organizer && payload.organizer.emailAddress
        ? payload.organizer.emailAddress.address || ""
        : ""
    ).trim();
    const organizerName = String(
      payload && payload.organizer && payload.organizer.emailAddress
        ? payload.organizer.emailAddress.name || ""
        : ""
    ).trim() || organizerEmail || "Bilinmiyor";

    return {
      subject: String(payload && payload.subject ? payload.subject : "").trim() || "Toplantı",
      showAs: String(payload && payload.showAs ? payload.showAs : "").trim() || "busy",
      startMillis,
      endMillis,
      organizerName,
      organizerEmail,
      get durationMillis() {
        return Math.max(0, endMillis - startMillis);
      }
    };
  }

  function restoreRememberedSubjects(events) {
    return events.map((event) => {
      const key = eventIdentityKey(event);
      const subject = event.subject.trim();
      const rememberedSubject = rememberedSubjects.get(key);

      if (subject && !subjectLooksLikeOrganizer(event)) {
        rememberedSubjects.set(key, subject);
        return event;
      }

      if (rememberedSubject) {
        return {
          ...event,
          subject: rememberedSubject
        };
      }

      return event;
    });
  }

  function eventIdentityKey(event) {
    const organizer = (event.organizerEmail || event.organizerName || "").trim().toLowerCase();
    return `${event.startMillis}|${event.endMillis}|${organizer}`;
  }

  function subjectLooksLikeOrganizer(event) {
    const subject = event.subject.trim().toLowerCase();
    if (!subject) {
      return false;
    }

    return (
      subject === event.organizerName.trim().toLowerCase() ||
      subject === event.organizerEmail.trim().toLowerCase()
    );
  }

  function recalculate() {
    const nowMillis = adjustedNow();
    const orderedEvents = [...state.events].sort((left, right) => left.startMillis - right.startMillis);
    const currentEvent = orderedEvents.find(
      (event) => nowMillis >= event.startMillis && nowMillis < event.endMillis
    ) || null;
    const nextEvent = orderedEvents.find((event) => event.startMillis > nowMillis) || null;
    const todayEvents = orderedEvents.filter((event) => isSameTurkeyDay(event.startMillis, nowMillis));

    state.currentEvent = currentEvent;
    state.nextEvent = nextEvent;
    state.isOccupied = Boolean(currentEvent);
    state.totalToday = todayEvents.length;
    state.remaining = todayEvents.filter((event) => event.endMillis >= nowMillis).length;
    state.occupiedHours = formatOccupiedHours(
      todayEvents.reduce((total, event) => total + event.durationMillis, 0)
    );
  }

  function adjustedNow() {
    return state.currentTime + state.serverTimeOffset;
  }

  function parseBackendDateTime(dateTime) {
    const raw = String(dateTime || "").trim();
    if (!raw) {
      return null;
    }

    const match = raw.match(backendDateRegex);
    if (!match) {
      return null;
    }

    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    const hour = Number(match[4]);
    const minute = Number(match[5]);
    const second = Number(match[6] || "0");
    const millisecond = parseMilliseconds(match[7] || "");
    const offsetToken = match[8] || null;

    const utcMillis = Date.UTC(year, month - 1, day, hour, minute, second, millisecond);

    if (!offsetToken) {
      return utcMillis - TURKEY_OFFSET_MINUTES * 60000;
    }

    return utcMillis - parseOffsetMinutes(offsetToken) * 60000;
  }

  function parseMilliseconds(rawMillis) {
    if (!rawMillis) {
      return 0;
    }

    if (rawMillis.length === 1) {
      return Number(rawMillis) * 100;
    }

    if (rawMillis.length === 2) {
      return Number(rawMillis) * 10;
    }

    return Number(rawMillis.slice(0, 3));
  }

  function parseOffsetMinutes(offsetToken) {
    if (offsetToken === "Z") {
      return 0;
    }

    const sign = offsetToken.startsWith("-") ? -1 : 1;
    const normalized = offsetToken.slice(1).replace(":", "");
    const hours = Number(normalized.slice(0, 2));
    const minutes = Number(normalized.slice(2, 4));
    return sign * (hours * 60 + minutes);
  }

  function turkeyParts(millis) {
    return partsFormatter.formatToParts(new Date(millis)).reduce((acc, part) => {
      if (part.type !== "literal") {
        acc[part.type] = Number(part.value);
      }
      return acc;
    }, {});
  }

  function formatClockTime(millis) {
    const parts = turkeyParts(millis);
    return `${twoDigits(parts.hour)}:${twoDigits(parts.minute)}`;
  }

  function formatPanelDay(millis) {
    return dayFormatter.format(new Date(millis)).toLocaleUpperCase("tr-TR");
  }

  function formatPanelDate(millis) {
    const parts = turkeyParts(millis);
    return `${parts.day} ${monthNames[parts.month - 1]} ${parts.year}`;
  }

  function formatEventRange(startMillis, endMillis) {
    return `${formatClockTime(startMillis)} – ${formatClockTime(endMillis)}`;
  }

  function formatOccupiedHours(durationMillis) {
    const totalMinutes = Math.max(0, durationMillis / 60000);
    if (totalMinutes <= 0) {
      return "0 sa";
    }

    if (totalMinutes < 60) {
      return `${Math.round(totalMinutes)} dk`;
    }

    const roundedHours = Math.round((totalMinutes / 60) * 10) / 10;
    return Number.isInteger(roundedHours)
      ? `${roundedHours} sa`
      : `${String(roundedHours).replace(/\.0$/, "")} sa`;
  }

  function formatCompactDuration(durationMillis) {
    const totalMinutes = Math.max(0, Math.floor(durationMillis / 60000));
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;

    if (hours > 0 && minutes === 0) {
      return `${hours} SA`;
    }

    if (hours > 0) {
      return `${hours} SA ${minutes} DK`;
    }

    return `${minutes} DK`;
  }

  function formatNextEventHeadline(eventMillis, nowMillis) {
    const eventParts = turkeyParts(eventMillis);
    const nowParts = turkeyParts(nowMillis);
    const timeLabel = formatClockTime(eventMillis);

    if (
      eventParts.year === nowParts.year &&
      eventParts.month === nowParts.month &&
      eventParts.day === nowParts.day
    ) {
      return timeLabel;
    }

    return `${eventParts.day} ${monthNames[eventParts.month - 1]} ${timeLabel}`;
  }

  function formatRemainingLabel(eventMillis, nowMillis) {
    const remainingMillis = Math.max(0, eventMillis - nowMillis);
    if (remainingMillis < 60000) {
      return "Toplantı birazdan başlıyor";
    }

    const totalMinutes = Math.floor(remainingMillis / 60000);
    const days = Math.floor(totalMinutes / (24 * 60));
    const hours = Math.floor((totalMinutes % (24 * 60)) / 60);
    const minutes = totalMinutes % 60;

    if (days > 0 && hours > 0) {
      return `${days} gün ${hours} saat kaldı`;
    }

    if (days > 0) {
      return `${days} gün kaldı`;
    }

    if (hours > 0 && minutes > 0) {
      return `${hours} saat ${minutes} dk kaldı`;
    }

    if (hours > 0) {
      return `${hours} saat kaldı`;
    }

    return `${minutes} dk kaldı`;
  }

  function isSameTurkeyDay(firstMillis, secondMillis) {
    const first = turkeyParts(firstMillis);
    const second = turkeyParts(secondMillis);
    return first.year === second.year && first.month === second.month && first.day === second.day;
  }

  function calculateWorkdayOccupancyPercentage(events, referenceMillis) {
    const parts = turkeyParts(referenceMillis);
    const workdayStart = Date.UTC(parts.year, parts.month - 1, parts.day, 8 - 3, 0, 0, 0);
    const workdayEnd = Date.UTC(parts.year, parts.month - 1, parts.day, 17 - 3, 0, 0, 0);
    const workdayDuration = Math.max(1, workdayEnd - workdayStart);
    const occupiedMillis = events
      .filter((event) => isSameTurkeyDay(event.startMillis, referenceMillis) || isSameTurkeyDay(event.endMillis, referenceMillis))
      .reduce((total, event) => {
        const overlapStart = Math.max(event.startMillis, workdayStart);
        const overlapEnd = Math.min(event.endMillis, workdayEnd);
        return total + Math.max(0, overlapEnd - overlapStart);
      }, 0);

    return clamp(Math.round((occupiedMillis / workdayDuration) * 100), 0, 100);
  }

  function buildStatusMetrics(events, nowMillis) {
    const todayEvents = events.filter((event) => isSameTurkeyDay(event.startMillis, nowMillis));
    const completedCount = todayEvents.filter((event) => event.endMillis < nowMillis).length;
    const remainingCount = todayEvents.filter((event) => event.endMillis >= nowMillis).length;
    const occupancyPercentage = calculateWorkdayOccupancyPercentage(todayEvents, nowMillis);

    return [
      { label: "BUGÜN TOPLAM", value: String(todayEvents.length) },
      { label: "YAPILAN", value: String(completedCount) },
      { label: "KALAN", value: String(remainingCount) },
      { label: "DOLULUK", value: `%${occupancyPercentage}` }
    ];
  }

  function render() {
    if (state.isBootstrapping) {
      app.innerHTML = renderSplash();
      return;
    }

    if (state.isSetupMode) {
      renderSetup();
      return;
    }

    renderMain();
  }

  function renderSplash() {
    return `
      <div class="splash">
        ${renderBrand()}
      </div>
    `;
  }

  function renderSetup() {
    const values = state.setupValues;
    app.innerHTML = `
      <main class="setup-screen">
        <div class="setup-center">
          <section class="setup-card">
            <header class="setup-header">
              ${renderBrand()}
              <div class="setup-pill">CİHAZ KURULUMU</div>
            </header>

            <section class="setup-copy">
              <h1>Toplantı ekranı bağlantısını ayarla</h1>
              <p>Sunucu adresi, API anahtarı ve oda bilgilerini girin. Ayarlar bu tarayıcıda saklanır.</p>
            </section>

            <form class="setup-form" id="setupForm" autocomplete="off">
              ${renderField("backendUrl", "Sunucu Adresi", values.backendUrl, "https://calendar.example.invalid", "url")}
              <div class="field-grid">
                ${renderField("apiKey", "API Anahtarı", values.apiKey, "Sunucu erişim anahtarı", "password")}
                ${renderField("roomName", "Oda Adı", values.roomName, "Toplantı Odası 1", "text")}
              </div>
              ${renderField("roomEmail", "Oda E-postası", values.roomEmail, "team@example.invalid", "email")}
            </form>

            <div class="setup-divider"></div>
            <p class="setup-note">Ana ekrandaki Ayarlar düğmesiyle kurulum ekranına dönebilirsiniz.</p>
            ${state.setupError ? `<div class="error-pill">${escapeHtml(state.setupError)}</div>` : ""}

            <footer class="setup-actions">
              ${
                state.activeConfig
                  ? `
                    <button class="button button-secondary" id="cancelSetup" type="button" ${state.isSavingConfig ? "disabled" : ""}>Vazgeç</button>
                    <button class="button button-warn" id="resetSetup" type="button" ${state.isSavingConfig ? "disabled" : ""}>Sıfırla</button>
                  `
                  : ""
              }
              <button class="button button-primary" id="saveSetup" type="button" ${state.isSavingConfig ? "disabled" : ""}>
                ${state.isSavingConfig ? "Kaydediliyor..." : "Kaydet ve Bağlan"}
              </button>
            </footer>
          </section>
        </div>
      </main>
    `;

    bindSetup();
    if (state.demo && state.demoRooms.length) {
      const button = document.createElement('button'); button.className = 'button button-secondary'; button.textContent = 'Örnek takvime dön';
      button.onclick = () => window.location.reload(); document.querySelector('.setup-actions').append(button);
    }
  }

  function renderField(id, label, value, placeholder, type) {
    return `
      <div class="field">
        <label for="${id}">${escapeHtml(label)}</label>
        <input id="${id}" name="${id}" type="${type}" value="${escapeAttr(value)}" placeholder="${escapeAttr(placeholder)}" ${state.isSavingConfig ? "disabled" : ""} />
      </div>
    `;
  }

  function bindSetup() {
    ["backendUrl", "apiKey", "roomName", "roomEmail"].forEach((field) => {
      const input = document.getElementById(field);
      if (!input) {
        return;
      }

      input.addEventListener("input", () => {
        state.setupValues = {
          ...state.setupValues,
          [field]: input.value
        };
        state.setupError = "";
      });
    });

    const saveButton = document.getElementById("saveSetup");
    if (saveButton) {
      saveButton.addEventListener("click", saveConfig);
    }

    const cancelButton = document.getElementById("cancelSetup");
    if (cancelButton) {
      cancelButton.addEventListener("click", cancelSetup);
    }

    const resetButton = document.getElementById("resetSetup");
    if (resetButton) {
      resetButton.addEventListener("click", resetConfig);
    }

    const form = document.getElementById("setupForm");
    if (form) {
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        saveConfig();
      });
    }
  }

  function saveConfig() {
    const normalized = normalizeConfig(state.setupValues);
    const validationError = validateConfig(normalized);

    if (validationError) {
      state.setupError = validationError;
      renderSetup();
      return;
    }

    state.isSavingConfig = true;
    state.setupError = "";
    renderSetup();

    window.setTimeout(() => {
      saveStoredConfig(normalized);
      state.activeConfig = normalized;
      state.setupValues = normalized;
      state.isSetupMode = false;
      state.isSavingConfig = false;
      state.errorMessage = null;
      state.events = [];
      recalculate();
      startPolling();
      render();
      loadEvents();
    }, 80);
  }

  function cancelSetup() {
    if (!state.activeConfig) {
      return;
    }

    state.setupValues = state.activeConfig;
    state.setupError = "";
    state.isSetupMode = false;
    render();
  }

  function resetConfig() {
    localStorage.removeItem(STORAGE_CONFIG);
    state.activeConfig = null;
    state.setupValues = emptyConfig();
    state.events = [];
    state.errorMessage = null;
    state.serverTimeOffset = 0;
    state.isSetupMode = true;
    state.setupError = "";
    clearInterval(state.refreshTimer);
    recalculate();
    render();
  }

  function openSetup() {
    state.isSetupMode = true;
    state.setupError = "";
    state.setupValues = state.activeConfig || state.setupValues || emptyConfig();
    render();
  }

  function renderMain() {
    if (document.activeElement?.id === "demoRoom") return;
    const config = state.activeConfig || emptyConfig();
    const nowMillis = adjustedNow();
    const roomName = displayRoomName(config);
    const roomEmail = config.roomEmail || "";
    const metrics = buildStatusMetrics(state.events, nowMillis);
    const timelineEvents = selectTimelineEvents(state.events, nowMillis, currentTimelineMaxItems());
    const occupiedClass = state.isOccupied ? "is-occupied" : "is-available";

    app.innerHTML = `
      <main class="display-frame">
        <section class="display-surface ${occupiedClass}" id="displaySurface">
          <aside class="timeline-panel">
            <h2 class="timeline-title">GÜNÜN AKIŞI</h2>
            ${
              timelineEvents.length
                ? `<div class="timeline-list">${timelineEvents.map((event) => renderTimelineItem(event, nowMillis)).join("")}</div>`
                : `<div class="empty-timeline">Bugün için görünür toplantı yok.</div>`
            }
            <footer class="timeline-footer">
              ${renderBrand({ symbol: 23, title: 16, subtitle: 10 })}
            </footer>
          </aside>

          <section class="status-side">
            <header class="status-header">
              <div class="day-label">${escapeHtml(formatPanelDay(nowMillis))}</div>
              <div class="room-meta">
                <div class="room-name" title="${escapeHtml(roomEmail)}">${escapeHtml(roomName)}</div>
                <div class="clock">${escapeHtml(formatClockTime(nowMillis))}</div>
                <div class="date">${escapeHtml(formatPanelDate(nowMillis))}</div>
              </div>
            </header>
            ${renderStatusPanel(nowMillis, metrics)}
          </section>
        </section>
        ${state.errorMessage ? `<div class="connection-error error-pill">BAĞLANTI HATASI</div>` : ""}
      </main>
    `;


    const toolbar = document.createElement('nav');
    toolbar.className = 'demo-toolbar';
    toolbar.setAttribute('aria-label', 'Takvim kontrolleri');
    toolbar.innerHTML = `${state.demo ? `<select id="demoRoom" aria-label="Oda">${state.demoRooms.map(room => `<option value="${escapeHtml(room.email)}" ${room.email === config.roomEmail ? 'selected' : ''}>${escapeHtml(room.name)}</option>`).join('')}</select>${[['busy','Dolu'],['available','Müsait'],['empty','Boş takvim']].map(([value,label]) => `<button data-scenario="${value}" aria-pressed="${state.demoScenario === value}">${label}</button>`).join('')}` : ''}<button id="openSettings">Ayarlar</button>`;
    app.append(toolbar);
    if (state.demo) {
      const note = document.createElement('small');
      note.className = 'calendar-note';
      note.textContent = 'Örnek veriler · Rezervasyon bu ekrandan yapılmaz';
      app.append(note);
    }
    document.getElementById('openSettings').onclick = openSetup;
    if (state.demo) {
      document.getElementById('demoRoom').onchange = event => {
        const room = state.demoRooms.find(r => r.email === event.target.value);
        state.activeConfig = { ...state.activeConfig, roomName: room.name, roomEmail: room.email };
        event.target.blur(); state.events = []; state.demoScenario = null; loadEvents(); render();
      };
      toolbar.querySelectorAll('[data-scenario]').forEach(button => button.onclick = async () => {
        const scenario = button.dataset.scenario;
        try {
          const response = await fetch('/api/scenario', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...state.activeConfig,scenario})});
          if (!response.ok) throw new Error('Senaryo değiştirilemedi.');
          state.demoScenario = scenario; await loadEvents();
        } catch (error) { state.errorMessage = error.message; render(); }
      });
    }
  }

  function renderTimelineItem(event, nowMillis) {
    const timelineState = getTimelineState(event, nowMillis);
    return `
      <article class="timeline-item is-${timelineState}">
        <div class="timeline-time">
          <span>${escapeHtml(formatClockTime(event.startMillis))}</span>
          <span>${escapeHtml(formatClockTime(event.endMillis))}</span>
        </div>
        <div class="timeline-accent"></div>
        <div class="timeline-main">
          <div class="timeline-subject">${escapeHtml(displaySubject(event))}</div>
          <div class="timeline-organizer">${escapeHtml(event.organizerName)}</div>
        </div>
        <div class="timeline-badge">${escapeHtml(timelineBadgeText(event, timelineState, nowMillis))}</div>
      </article>
    `;
  }

  function renderStatusPanel(nowMillis, metrics) {
    if (state.isOccupied && state.currentEvent) {
      return renderOccupiedPanel(state.currentEvent, state.nextEvent, nowMillis, metrics);
    }

    return renderAvailablePanel(state.nextEvent, nowMillis, metrics);
  }

  function renderOccupiedPanel(currentEvent, nextEvent, nowMillis, metrics) {
    return `
      <section class="status-panel">
        <div class="hero">
          <h1 class="hero-title">MEŞGUL</h1>

        </div>

        <div class="summary-row">
          ${renderCurrentMeetingCard(currentEvent)}
          ${
            nextEvent
              ? renderNextMeetingCard(nextEvent, nowMillis)
              : renderNeutralCard("SONRAKİ TOPLANTI", "Başka toplantı yok", "")
          }
        </div>

        <div class="status-spacer"></div>
        ${renderMetrics(metrics)}
      </section>
    `;
  }

  function renderAvailablePanel(nextEvent, nowMillis, metrics) {
    const hasTodayMeetings = state.events.some(event => isSameTurkeyDay(event.startMillis, nowMillis));
    return `
      <section class="status-panel">
        <div class="hero">
          <h1 class="hero-title">MÜSAİT</h1>
        </div>

        <div class="summary-row ${nextEvent ? "" : "summary-row-single"}">
          ${
            nextEvent
              ? `${renderOpenWindowCard(nextEvent, nowMillis)}${renderNextMeetingCard(nextEvent, nowMillis)}`
              : renderNeutralCard("GÜNÜN TAKVİMİ", hasTodayMeetings ? "Bugün başka toplantı yok" : "Bugün toplantı yok", "Günün geri kalanında oda serbest.")
          }
        </div>

        <div class="status-spacer"></div>
        ${renderMetrics(metrics)}
      </section>
    `;
  }

  function renderCurrentMeetingCard(event) {
    return renderSummaryCard({
      title: "AKTİF TOPLANTI",
      topRight: formatCompactDuration(event.durationMillis),
      headline: displaySubject(event),
      headlineClass: "",
      lines: [
        `Planlayan: ${event.organizerName}`,
        `<strong>${escapeHtml(formatEventRange(event.startMillis, event.endMillis))}</strong>`
      ]
    });
  }

  function renderNextMeetingCard(event, nowMillis) {
    return renderSummaryCard({
      title: "SONRAKİ TOPLANTI",
      headline: formatNextEventHeadline(event.startMillis, nowMillis),
      headlineClass: "headline-medium",
      lines: [
        displaySubject(event),
        `Planlayan: ${event.organizerName}`
      ]
    });
  }

  function renderOpenWindowCard(event, nowMillis) {
    return renderSummaryCard({
      title: "BOŞ SÜRE",
      headline: formatCompactDuration(Math.max(0, event.startMillis - nowMillis)),
      headlineClass: "headline-medium",
      lines: ["Sonraki toplantıya kadar"]
    });
  }

  function renderNeutralCard(title, headline, supporting) {
    return renderSummaryCard({
      title,
      headline,
      headlineClass: "headline-small",
      lines: supporting ? [supporting] : []
    });
  }

  function renderSummaryCard({ title, topRight, headline, headlineClass, lines }) {
    return `
      <article class="summary-card">
        <div class="summary-card-head">
          <span>${escapeHtml(title)}</span>
          ${topRight ? `<span class="summary-title-right">${escapeHtml(topRight)}</span>` : ""}
        </div>
        <h2 class="${headlineClass || ""}">${escapeHtml(headline)}</h2>
        ${lines
          .map((line) => {
            if (String(line).startsWith("<strong>")) {
              return line;
            }
            return `<p>${escapeHtml(line)}</p>`;
          })
          .join("")}
      </article>
    `;
  }

  function renderMetrics(metrics) {
    return `
      <div class="metrics-strip">
        ${metrics
          .map(
            (metric) => `
              <article class="metric-card">
                <div class="metric-label">${escapeHtml(metric.label)}</div>
                <div class="metric-value">${escapeHtml(metric.value)}</div>
              </article>
            `
          )
          .join("")}
      </div>
    `;
  }

  function renderBrand(options = {}) {
    const symbol = options.symbol || 20;
    const title = options.title || 13;
    const subtitle = options.subtitle || 9;
    const width = symbol * 1.7;
    const height = symbol * 1.28;

    return `
      <div class="brand" style="--brand-width:${width}px;--brand-height:${height}px;--brand-title:${title}px;--brand-subtitle:${subtitle}px">
        <span class="brand-mark" aria-hidden="true"></span>
        <span>
          <span class="brand-title">ROOMFLOW</span>
          <span class="brand-subtitle">TOPLANTI TAKVİMİ</span>
        </span>
      </div>
    `;
  }

  function bindLongPress() {
    const surface = document.getElementById("displaySurface");
    if (!surface) {
      return;
    }

    let timer = null;
    const cancel = () => {
      clearTimeout(timer);
      timer = null;
      window.removeEventListener("pointerup", cancel);
      window.removeEventListener("pointercancel", cancel);
      window.removeEventListener("pointermove", cancel);
    };

    surface.addEventListener("pointerdown", () => {
      clearTimeout(timer);
      timer = window.setTimeout(openSetup, SETUP_HOLD_DURATION_MS);
      window.addEventListener("pointerup", cancel, { once: true });
      window.addEventListener("pointercancel", cancel, { once: true });
      window.addEventListener("pointermove", cancel, { once: true });
    });
  }

  function displayRoomName(config) {
    if (config.roomName && config.roomName.trim()) {
      return config.roomName.trim();
    }

    const derived = String(config.roomEmail || "")
      .split("@")[0]
      .replace(/[._-]/g, " ")
      .replace(/\s+/g, " ")
      .trim();

    return derived ? derived.toLocaleUpperCase("tr-TR") : "TOPLANTI ODASI";
  }

  function displaySubject(event) {
    return event.subject.trim() || "Toplantı";
  }

  function selectTimelineEvents(events, nowMillis, maxItems) {
    const todayEvents = events
      .filter((event) => isSameTurkeyDay(event.startMillis, nowMillis))
      .sort((left, right) => left.startMillis - right.startMillis);

    if (todayEvents.length <= maxItems) {
      return todayEvents;
    }

    const focusIndexRaw = todayEvents.findIndex((event) => nowMillis < event.endMillis);
    const focusIndex = focusIndexRaw >= 0 ? focusIndexRaw : todayEvents.length - 1;
    let startIndex = Math.max(0, focusIndex - 2);
    const endIndex = Math.min(todayEvents.length, startIndex + maxItems);
    startIndex = Math.max(0, endIndex - maxItems);

    return todayEvents.slice(startIndex, endIndex);
  }

  function getTimelineState(event, nowMillis) {
    if (nowMillis > event.endMillis) {
      return "past";
    }

    if (nowMillis >= event.startMillis) {
      return "active";
    }

    return "upcoming";
  }

  function timelineBadgeText(event, timelineState, nowMillis) {
    if (timelineState === "past") {
      return "Bitti";
    }

    if (timelineState === "active") {
      return "Şimdi";
    }

    const minutesUntilStart = Math.max(1, Math.ceil((event.startMillis - nowMillis) / 60000));
    if (minutesUntilStart < 60) {
      return `${minutesUntilStart} dk`;
    }

    return `${Math.ceil(minutesUntilStart / 60)} sa`;
  }

  function currentTimelineMaxItems() {
    const width = window.innerWidth;
    const height = window.innerHeight;

    if (width < 900 || height < 500) {
      return 3;
    }

    if (width < 1380 || height < 820) {
      return 4;
    }

    return 6;
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function escapeAttr(value) {
    return escapeHtml(value).replace(/`/g, "&#96;");
  }

  function twoDigits(value) {
    return String(value).padStart(2, "0");
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  window.addEventListener("resize", () => {
    if (!state.isSetupMode) {
      renderMain();
    }
  });

  init();
})();
