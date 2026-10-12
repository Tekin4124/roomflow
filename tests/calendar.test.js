const {test} = require('node:test');
const assert = require('node:assert/strict');
const {eventMillis,buildRoomStatus}=require('../backend/calendarStatus');
test('Graph timestamps without offsets are interpreted in Turkey, including seven decimal digits',()=>{
 assert.equal(eventMillis({dateTime:'2026-04-13T09:00:00.0000000',timeZone:'Turkey Standard Time'}),Date.parse('2026-04-13T06:00:00Z'));
 assert.equal(eventMillis({dateTime:'2026-04-13T06:00:00',timeZone:'UTC'}),Date.parse('2026-04-13T06:00:00Z'));
});
test('status counts only Turkey today, ignores free bookings and uses an exclusive meeting end',()=>{
 const event=(start,end,showAs='busy')=>({subject:'Test',showAs,start:{dateTime:start},end:{dateTime:end}});
 const events=[event('2026-04-13T09:00:00','2026-04-13T10:00:00'),event('2026-04-14T09:00:00','2026-04-14T10:00:00'),event('2026-04-13T09:00:00','2026-04-13T10:00:00','free')];
 const current=buildRoomStatus('room@example.invalid',events,Date.parse('2026-04-13T06:30:00Z'));
 assert.equal(current.isOccupied,true);assert.equal(current.totalToday,1);assert.equal(current.currentEvent.start,'09:00');
 assert.equal(buildRoomStatus('room@example.invalid',events,Date.parse('2026-04-13T07:00:00Z')).isOccupied,false);
});
