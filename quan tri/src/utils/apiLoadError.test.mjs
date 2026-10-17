import test from 'node:test'
import assert from 'node:assert/strict'
import {apiLoadError} from './apiLoadError.ts'
test('database API failures remain errors, with HTTP and code, never numeric empty results',()=>{
 const error={isAxiosError:true,response:{status:500,data:{error:'DATABASE_ERROR',message:'SQL password=private stack trace'}}}
 const message=apiLoadError(error,'Không tải được báo cáo')
 assert.match(message,/HTTP 500/);assert.match(message,/DATABASE_ERROR/);assert.doesNotMatch(message,/private|SQL|stack|Chưa xác định/)
})
test('forbidden and disconnected backend differ from empty data and from each other',()=>{
 assert.match(apiLoadError({response:{status:403,data:{error:'FORBIDDEN'}}},'Lỗi tải'),/không có quyền/)
 assert.match(apiLoadError({isAxiosError:true},'Lỗi tải'),/Không thể kết nối backend/)
 assert.equal(apiLoadError(new Error('SQL private'),'Lỗi tải'),'Lỗi tải')
})
test('untrusted technical error code is not displayed',()=>{
 assert.equal(apiLoadError({response:{status:500,data:{error:'SELECT password FROM secret'}}},'Lỗi tải'),'Lỗi tải (HTTP 500).')
})
