import test from 'node:test'
import assert from 'node:assert/strict'
import {readFile,writeFile,mkdtemp,rm} from 'node:fs/promises'
import {fileURLToPath,pathToFileURL} from 'node:url'
import path from 'node:path'
import ts from 'typescript'
import React from 'react'
import {renderToStaticMarkup} from 'react-dom/server'
test('shared input displays Vietnamese dates, preserves ISO payloads, rejects impossible dates and handles clearing',async()=>{
 const temp=await mkdtemp(path.join(fileURLToPath(new URL('../../../node_modules/',import.meta.url)),'.date-input-'))
 try{
 const source=await readFile(new URL('./DateInput.tsx',import.meta.url),'utf8')
 const helper=pathToFileURL(fileURLToPath(new URL('../../utils/dateTime.ts',import.meta.url))).href
 const output=ts.transpileModule(source,{compilerOptions:{jsx:ts.JsxEmit.ReactJSX,module:ts.ModuleKind.ESNext}}).outputText.replaceAll("'../../utils/dateTime'",JSON.stringify(helper))
 const file=path.join(temp,'input.mjs');await writeFile(file,output);const {default:Input}=await import(pathToFileURL(file).href)
 const html=renderToStaticMarkup(React.createElement(Input,{value:'2024-02-29',required:true}))
 assert.ok(html.includes('value="29/02/2024"'));assert.ok(html.includes('dd/MM/yyyy'));assert.doesNotMatch(html,/type="date"/)
 let received,validity;const input=Input({value:'',onChange:e=>received=e.target.value})
 const dispatch=value=>input.props.onChange({currentTarget:{value,setCustomValidity:v=>validity=v}})
 dispatch('31/12/2026');assert.equal(received,'2026-12-31');assert.equal(validity,'')
 dispatch('31/02/2026');assert.equal(received,'31/02/2026');assert.ok(validity)
 dispatch('');assert.equal(received,'');assert.equal(validity,'')
 const rerender=Input({value:''});rerender.props.ref({setCustomValidity:v=>validity=v});assert.equal(validity,'')
 }finally{await rm(temp,{recursive:true,force:true})}
})
