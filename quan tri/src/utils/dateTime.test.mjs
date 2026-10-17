import test from 'node:test'
import assert from 'node:assert/strict'
import {isIsoDate,formatDate,parseDisplayDate,formatDateTime,businessToday,dateRangeError,instantDateBounds,formatPeriod} from './dateTime.ts'
import {spawnSync} from 'node:child_process'
test('calendar boundaries and leap years never roll over',()=>{
 for(const v of ['2024-02-29','2026-01-01','2026-04-30','2026-12-31'])assert.equal(isIsoDate(v),true)
 for(const v of ['2026-02-29','1900-02-29','2026-04-31','2026-13-01','0000-01-01'])assert.equal(isIsoDate(v),false)
 assert.equal(formatDate('2024-02-29'),'29/02/2024');assert.equal(parseDisplayDate('29/02/2024'),'2024-02-29');assert.equal(parseDisplayDate('31/02/2024'),'31/02/2024')
 assert.equal(formatDate(null),'—');assert.equal(formatDate('garbage'),'—')
})
test('ranges across months/years and invalid drafts',()=>{
 assert.equal(dateRangeError('2026-01-31','2026-02-01'),'');assert.equal(dateRangeError('2025-12-31','2026-01-01'),'')
 assert.ok(dateRangeError('2026-02-01','2026-01-31'));assert.ok(dateRangeError('31/',''));assert.ok(dateRangeError('2026-02-30',''))
 assert.equal(dateRangeError('2026-01-01','2026-01-01'),'');assert.equal(formatPeriod('2026-01'),'01/2026')
})
test('offset timestamps use Vietnam; legacy wall clocks preserve history',()=>{
 assert.equal(formatDateTime('2026-12-31T18:00:00Z'),'01/01/2027 01:00')
 assert.equal(formatDateTime('2026-01-31T23:59:59.123456'),'31/01/2026 23:59')
 assert.equal(formatDateTime('2026-02-30T10:00:00'),'—');assert.equal(formatDateTime('2026-01-01T24:00:00'),'—')
 assert.equal(businessToday(new Date('2026-01-31T18:00:00Z')),'2026-02-01')
})
test('evaluation bounds are Vietnam inclusive dates, independent of browser timezone',()=>{
 assert.deepEqual(instantDateBounds('2024-02-29','2024-02-29'),{from:'2024-02-28T17:00:00.000Z',to:'2024-02-29T16:59:59.999Z'})
 for(const TZ of ['UTC','America/Los_Angeles','Asia/Tokyo']){
 const result=spawnSync(process.execPath,['--experimental-strip-types','--input-type=module','-e',"import {formatDate,formatDateTime} from './src/utils/dateTime.ts';console.log(formatDate('2024-02-29')+'|'+formatDateTime('2026-12-31T18:00:00Z'))"],{env:{...process.env,TZ},encoding:'utf8'})
 assert.equal(result.status,0,result.stderr);assert.equal(result.stdout.trim(),'29/02/2024|01/01/2027 01:00')
 }
})
