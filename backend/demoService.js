// All names, addresses and meetings here are synthetic. No external calls.
function createDemoCalendar() {
const scenarios = new Map();
function setScenario(roomEmail, scenario) {
  if (!['busy', 'available', 'empty'].includes(scenario)) {
    const error = new Error('Unknown demo scenario.'); error.statusCode = 400; throw error;
  }
  scenarios.set(roomEmail, { scenario, anchor: Date.now() });
}
function getRoomEvents(roomEmail) {
  if (!scenarios.has(roomEmail)) setScenario(roomEmail, roomEmail.startsWith('orion') ? 'available' : 'busy');
  const { scenario, anchor } = scenarios.get(roomEmail);
  if (scenario === 'empty') return Promise.resolve([]);
  const event = (id, subject, start, end) => ({
    id, subject, showAs: 'busy',
    start: { dateTime: new Date(anchor + start * 60000).toISOString(), timeZone: 'UTC' },
    end: { dateTime: new Date(anchor + end * 60000).toISOString(), timeZone: 'UTC' },
    organizer: { emailAddress: { name: 'Toplantı Ekibi', address: 'team@example.invalid' } }
  });
  const events = [event('completed', 'Haftalık planlama', -90, -60), event('next', 'Ürün değerlendirmesi', 45, 90)];
  if (scenario === 'busy') events.push(event('current', 'Tasarım toplantısı', -10, 25));
  return Promise.resolve(events.sort((a,b) => a.start.dateTime.localeCompare(b.start.dateTime)));
}
async function getRoomStatus(roomEmail) {
  return require('./calendarStatus').buildRoomStatus(roomEmail, await getRoomEvents(roomEmail));
}
return { getRoomEvents, getRoomStatus, setScenario,
  invalidateRoomEventsCache() {}, getClientSecretExpiryInfo() { return null; } };
}
module.exports = { ...createDemoCalendar(), createDemoCalendar };
