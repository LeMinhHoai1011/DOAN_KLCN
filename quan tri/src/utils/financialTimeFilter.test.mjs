import test from 'node:test'
import assert from 'node:assert/strict'
import {defaultFinancialFilter,selectedTimeRange,selectedInterval,financialFilterError,monthBounds,shiftMonth,readTimeFilter,writeTimeFilter} from './financialTimeFilter.ts'
test('test clock gives exactly October 1–17; production defaults are dynamic',()=>{
 const f=defaultFinancialFilter('2026-10-17');assert.deepEqual(selectedTimeRange(f),{dateFrom:'2026-10-01',dateTo:'2026-10-17'})
 assert.equal(defaultFinancialFilter('2027-01-02').from,'2027-01-01');assert.equal(selectedInterval(f),'DAILY')
})
test('month/day/range retain independent selections through URL navigation and pagination',()=>{
 const original={...defaultFinancialFilter('2026-10-17'),day:'2024-02-29',month:'2025-12',from:'2026-10-31',to:'2026-11-01'}
 let params=writeTimeFilter(new URLSearchParams('companyId=13&page=4'),original)
 for(const mode of ['MONTH','DAY','RANGE']){const state={...readTimeFilter(params,defaultFinancialFilter('2028-01-01')),mode};params=writeTimeFilter(params,state);const restored=readTimeFilter(params,defaultFinancialFilter('2028-01-01'));assert.deepEqual(restored,state);assert.equal(restored.from,original.from);assert.equal(restored.day,original.day);assert.equal(params.get('companyId'),'13')}
 params.set('page','5');assert.equal(readTimeFilter(params,defaultFinancialFilter()).from,'2026-10-31')
 // Same serialized state can be restored on a different financial page.
 assert.deepEqual(readTimeFilter(new URLSearchParams(params.toString()),defaultFinancialFilter()),{...original,mode:'RANGE'})
})
test('calendar month navigation, leap day and long ranges',()=>{
 assert.deepEqual(monthBounds('2024-02'),{dateFrom:'2024-02-01',dateTo:'2024-02-29'})
 assert.equal(shiftMonth('2026-12',1),'2027-01');assert.equal(shiftMonth('2027-01',-1),'2026-12')
 const f=defaultFinancialFilter('2026-10-17');assert.equal(financialFilterError({...f,from:'2026-10-17',to:'2026-10-01'}).length>0,true)
 assert.ok(financialFilterError({...f,mode:'DAY',day:'2026-02-29'}));assert.ok(financialFilterError({...f,from:''}))
 assert.equal(selectedInterval({...f,from:'2026-01-01',to:'2027-01-01'}),'MONTHLY')
 assert.equal(selectedInterval({...f,mode:'MONTH'}),'DAILY');assert.equal(selectedInterval({...f,mode:'DAY'}),'DAILY')
})
