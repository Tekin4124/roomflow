// Graph's requested Turkey Standard Time values often omit an explicit UTC offset.
function eventMillis(value) {
  const raw = String(value?.dateTime || '').replace(/(\.\d{3})\d+/, '$1');
  const offset = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw) ? '' : value?.timeZone === 'UTC' ? 'Z' : '+03:00';
  return Date.parse(raw + offset);
}
const dayFormatter = new Intl.DateTimeFormat('en-CA', {timeZone:'Europe/Istanbul'});
const timeFormatter = new Intl.DateTimeFormat('tr-TR', {timeZone:'Europe/Istanbul',hour:'2-digit',minute:'2-digit',hour12:false});
function buildRoomStatus(roomEmail, events, now = Date.now()) {
  const meetings = events.filter(e => e.showAs !== 'free' && Number.isFinite(eventMillis(e.start)) && eventMillis(e.end) > eventMillis(e.start))
    .sort((a,b)=>eventMillis(a.start)-eventMillis(b.start));
  const current = meetings.find(e=>eventMillis(e.start)<=now && now<eventMillis(e.end));
  const next = meetings.find(e=>eventMillis(e.start)>now);
  const brief = event => event ? {subject:event.subject,organizer:event.organizer?.emailAddress?.name || 'Toplantı Ekibi',start:timeFormatter.format(eventMillis(event.start)),end:timeFormatter.format(eventMillis(event.end)),duration:`${Math.round((eventMillis(event.end)-eventMillis(event.start))/60000)}dk`} : null;
  return {roomEmail,isOccupied:!!current,currentEvent:brief(current),nextEvent:brief(next),
    totalToday:meetings.filter(e=>dayFormatter.format(eventMillis(e.start))===dayFormatter.format(now)).length,
    remaining:meetings.filter(e=>eventMillis(e.end)>now).length,lastUpdated:new Date(now).toISOString()};
}
module.exports = { eventMillis, buildRoomStatus };
