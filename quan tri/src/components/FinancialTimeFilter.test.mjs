import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile,writeFile,mkdtemp,rm} from 'node:fs/promises'
import {fileURLToPath,pathToFileURL} from 'node:url'
import path from 'node:path'
import ts from 'typescript'
import React from 'react'
import {renderToStaticMarkup} from 'react-dom/server'
import {defaultFinancialFilter} from '../utils/financialTimeFilter.ts'
test('renders three modes with Vietnamese dates and month navigation preserves other choices',async()=>{
 const temp=await mkdtemp(path.join(fileURLToPath(new URL('../../node_modules/',import.meta.url)),'.financial-time-'))
 try{
  for(const [name,relative] of [['date','./ui/DateInput.tsx'],['filter','./FinancialTimeFilter.tsx']]){
   const source=await readFile(new URL(relative,import.meta.url),'utf8')
   let out=ts.transpileModule(source,{compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.ESNext}}).outputText
   out=out.replaceAll("'../../utils/dateTime'",JSON.stringify(pathToFileURL(fileURLToPath(new URL('../utils/dateTime.ts',import.meta.url))).href))
    .replaceAll("'../utils/financialTimeFilter'",JSON.stringify(pathToFileURL(fileURLToPath(new URL('../utils/financialTimeFilter.ts',import.meta.url))).href))
    .replaceAll("'./ui/DateInput'","'./date.mjs'")
   await writeFile(path.join(temp,name+'.mjs'),out)
  }
  const {default:Filter}=await import(pathToFileURL(path.join(temp,'filter.mjs')).href)
  const value=defaultFinancialFilter('2026-10-17')
  for(const mode of ['MONTH','DAY','RANGE']){
   const html=renderToStaticMarkup(React.createElement(Filter,{value:{...value,mode},onChange:()=>{}}))
   for(const label of ['Theo tháng','Theo ngày cụ thể','Khoảng ngày'])assert.ok(html.includes(label))
   if(mode==='RANGE'){assert.ok(html.includes('01/10/2026'));assert.ok(html.includes('17/10/2026'))}
   if(mode==='DAY')assert.ok(html.includes('17/10/2026'))
   if(mode==='MONTH'){assert.ok(html.includes('Tháng trước'));assert.ok(html.includes('Tháng sau'))}
  }
  let updated
  const tree=Filter({value:{...value,mode:'MONTH',month:'2026-12'},onChange:v=>updated=v})
  const walk=node=>!node||typeof node!=='object'?[]:[node,...React.Children.toArray(node.props?.children).flatMap(walk)]
  const next=walk(tree).find(n=>n.props?.['aria-label']==='Tháng sau');next.props.onClick()
  assert.equal(updated.month,'2027-01');assert.equal(updated.from,value.from);assert.equal(updated.day,value.day)
 }finally{await rm(temp,{recursive:true,force:true})}
})
