// Packages manually verified references and recorded experiment evidence. Does not run OCR/AI or access a DB.
import { readFile, writeFile, mkdir, realpath, stat } from 'node:fs/promises'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { pathToFileURL } from 'node:url'

const sha = bytes => createHash('sha256').update(bytes).digest('hex')
const categoryLabel = value => typeof value === 'string' && value.trim().length > 0 && /^[\p{L}\p{N}_.:@\/ +()-]{1,160}$/u.test(value)
export async function prepareBundle(sourceDirectory, outputDirectory) {
  const source = await realpath(sourceDirectory)
  const output = path.resolve(outputDirectory)
  if (output === source || output.startsWith(source + path.sep)) throw new Error('Output must be separate from source')
  // Output creation must be explicit and exclusive; never overwrite an existing benchmark.
  try { await stat(output); throw new Error('Output already exists') } catch (e) { if (e.code !== 'ENOENT') throw e }
  const read = async file => {
    if (typeof file !== 'string' || path.isAbsolute(file)) throw new Error('Artifact must be source-relative')
    const resolved = await realpath(path.resolve(source, file))
    if (!resolved.startsWith(source + path.sep)) throw new Error('Artifact outside source')
    if ((await stat(resolved)).size > 25_000_000) throw new Error('Artifact too large')
    return { bytes: await readFile(resolved), resolved }
  }
  const manifest = JSON.parse((await read('manifest.json')).bytes.toString('utf8'))
  if (![1, 2].includes(manifest.schemaVersion) || !Array.isArray(manifest.samples) || !Array.isArray(manifest.runs)) throw new Error('Invalid manifest')
  // Validate everything before creating any output.
  const pending = new Map()
  const artifact = (folder, bytes, suffix) => { const digest = sha(bytes); pending.set(`${folder}/${digest}${suffix}`, bytes); return digest }
  const samples = []
  for (const { assetFile, ...sample } of manifest.samples) {
    if (!sample.verifiedBy || !sample.verifiedAt) throw new Error('Manual verification is required')
    const hasTaxonomy = sample.categoryTaxonomy != null
    const hasCategory = sample.accountingCategory != null
    if (hasTaxonomy !== hasCategory || (manifest.schemaVersion === 1 && (hasTaxonomy || hasCategory))) throw new Error('Invalid category reference')
    if (hasTaxonomy && (![sample.categoryTaxonomy, sample.accountingCategory].every(categoryLabel) || sample.accountingCategory === '__NO_PREDICTION__')) throw new Error('Invalid category reference')
    const { bytes } = await read(assetFile)
    samples.push({ ...sample, assetSha256: artifact('assets', bytes, '.bin') })
  }
  const byId = new Map(samples.map(s => [s.id, s]))
  if (byId.size !== samples.length) throw new Error('Duplicate sample')
  const runs = []
  for (const run of manifest.runs) {
    const configBytes = (await read(run.configFile)).bytes
    const config = JSON.parse(configBytes.toString('utf8'))
    const results = []
    for (const reference of run.results) {
      const bytes = (await read(reference.evidenceFile)).bytes
      const result = JSON.parse(bytes.toString('utf8'))
      if (!byId.has(result.sampleId) || result.assetSha256 !== byId.get(result.sampleId).assetSha256 || 'evidenceSha256' in result) throw new Error('Evidence must identify the exact original sample')
      // Evidence must include explicit nulls for unavailable fields, matching the backend record contract.
      const required = ['sampleId', 'assetSha256', 'outcome', 'failureCode', 'durationMs', 'visionFallback', 'attemptCount', 'ocrText', 'ocrFields', 'fields', 'items', 'predictedType']
      if (manifest.schemaVersion === 2) required.push('predictedCategory')
      if (required.some(key => !(key in result)) || Object.keys(result).some(key => !required.includes(key))) throw new Error('Invalid evidence keys')
      if (result.predictedCategory != null && (!byId.get(result.sampleId).categoryTaxonomy || !categoryLabel(result.predictedCategory) || result.predictedCategory === '__NO_PREDICTION__')) throw new Error('Invalid category prediction')
      results.push({ ...result, evidenceSha256: artifact('evidence', bytes, '.json') })
    }
    runs.push({ id: run.id, startedAt: run.startedAt, strategy: config.strategy, model: config.model,
      modelDigest: config.modelDigest, environment: config.environment, codeRevision: config.codeRevision,
      configSha256: artifact('configs', configBytes, '.json'), results })
  }
  const bundle = { schemaVersion: manifest.schemaVersion, datasetId: manifest.datasetId, datasetVersion: manifest.datasetVersion, samples, runs }
  await mkdir(output, { recursive: false })
  for (const [relative, bytes] of pending) {
    const target = path.join(output, relative)
    await mkdir(path.dirname(target), { recursive: true })
    await writeFile(target, bytes, { flag: 'wx' })
  }
  await writeFile(path.join(output, 'benchmark.json'), JSON.stringify(bundle, null, 2), { flag: 'wx' })
  return { samples: samples.length, runs: runs.length, bundleSha256: sha(Buffer.from(JSON.stringify(bundle, null, 2))) }
}
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  if (process.argv.length !== 4) { console.error('Usage: node prepare-bundle.mjs <source-directory> <new-output-directory>'); process.exitCode = 1 }
  else try { console.log(JSON.stringify(await prepareBundle(process.argv[2], process.argv[3]))) }
  catch { console.error('EVALUATION_BUNDLE_PREPARATION_FAILED: check manifest, evidence and independent output directory'); process.exitCode = 1 }
}
