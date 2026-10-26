import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, writeFile, readFile, rm, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { prepareBundle } from './prepare-bundle.mjs'
test('bundle builder preserves recorded evidence, hashes assets, and refuses overwrite', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'evaluation-unit-'))
  try {
    const source = path.join(root, 'source'); await mkdir(source)
    const asset = Buffer.from('synthetic unit-test fixture, never an actual benchmark'); await writeFile(path.join(source, 'asset.bin'), asset)
    const result = { sampleId: 's', assetSha256: createHash('sha256').update(asset).digest('hex'), outcome: 'FAILED', failureCode: 'AI_TIMEOUT', durationMs: 100, visionFallback: null, attemptCount: 1, ocrText: null, ocrFields: null, fields: null, items: null, predictedType: null }
    await writeFile(path.join(source, 'result.json'), JSON.stringify(result))
    await writeFile(path.join(source, 'config.json'), JSON.stringify({ strategy: 'A', model: 'unit', modelDigest: 'a'.repeat(64), environment: 'test only', codeRevision: 'unit', settings: { temperature: 0 } }))
    const manifest = { schemaVersion: 1, datasetId: 'unit', datasetVersion: '1', samples: [{ id: 's', assetFile: 'asset.bin', documentType: 'INVOICE', tags: ['CLEAR'], verifiedBy: 'unit reviewer', verifiedAt: '2026-01-01T00:00:00Z', ocrText: null, fields: null, ocrFields: null, items: null }], runs: [{ id: 'a', startedAt: '2026-01-02T00:00:00Z', configFile: 'config.json', results: [{ evidenceFile: 'result.json' }] }] }
    await writeFile(path.join(source, 'manifest.json'), JSON.stringify(manifest))
    const output = path.join(root, 'output'), summary = await prepareBundle(source, output)
    assert.equal(summary.samples, 1); assert.equal(summary.runs, 1)
    const bundle = JSON.parse(await readFile(path.join(output, 'benchmark.json'), 'utf8'))
    assert.equal(bundle.runs[0].results[0].outcome, 'FAILED'); assert.equal(bundle.runs[0].results[0].durationMs, 100)
    assert.equal(bundle.samples[0].assetFile, undefined)
    await assert.rejects(() => prepareBundle(source, output), /already exists/)
    await assert.rejects(() => prepareBundle(source, path.join(source, 'nested')), /separate/)
    manifest.samples[0].verifiedBy = ''; await writeFile(path.join(source, 'manifest.json'), JSON.stringify(manifest))
    await assert.rejects(() => prepareBundle(source, path.join(root, 'bad')), /Manual verification/)
  } finally { await rm(root, { recursive: true, force: true }) } // Only this test's mkdtemp directory.
})

test('v2 preserves independent cost-category labels and requires explicit category evidence', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'evaluation-category-unit-'))
  try {
    const source = path.join(root, 'source'); await mkdir(source)
    const asset = Buffer.from('category unit fixture'); await writeFile(path.join(source, 'asset.bin'), asset)
    const result = { sampleId: 's', assetSha256: createHash('sha256').update(asset).digest('hex'), outcome: 'SUCCESS', failureCode: null, durationMs: 10, visionFallback: false, attemptCount: 1, ocrText: null, ocrFields: null, fields: null, items: null, predictedType: 'INVOICE', predictedCategory: 'TRANSPORT' }
    await writeFile(path.join(source, 'result.json'), JSON.stringify(result))
    await writeFile(path.join(source, 'config.json'), JSON.stringify({ strategy: 'A', model: 'unit', modelDigest: 'a'.repeat(64), environment: 'unit', codeRevision: 'unit', settings: { temperature: 0 } }))
    const manifest = { schemaVersion: 2, datasetId: 'unit', datasetVersion: '2', samples: [{ id: 's', assetFile: 'asset.bin', documentType: 'INVOICE', categoryTaxonomy: 'internal-costs-v1', accountingCategory: 'OFFICE', tags: [], verifiedBy: 'human', verifiedAt: '2026-01-01T00:00:00Z', ocrText: null, fields: null, ocrFields: null, items: null }], runs: [{ id: 'a', startedAt: '2026-01-02T00:00:00Z', configFile: 'config.json', results: [{ evidenceFile: 'result.json' }] }] }
    const writeManifest = () => writeFile(path.join(source, 'manifest.json'), JSON.stringify(manifest))
    await writeManifest()
    await prepareBundle(source, path.join(root, 'good'))
    const bundle = JSON.parse(await readFile(path.join(root, 'good', 'benchmark.json'), 'utf8'))
    assert.equal(bundle.schemaVersion, 2)
    assert.equal(bundle.samples[0].accountingCategory, 'OFFICE')
    assert.equal(bundle.runs[0].results[0].predictedCategory, 'TRANSPORT')
    delete result.predictedCategory
    await writeFile(path.join(source, 'result.json'), JSON.stringify(result))
    await assert.rejects(() => prepareBundle(source, path.join(root, 'missing')), /Invalid evidence keys/)
    result.predictedCategory = null
    await writeFile(path.join(source, 'result.json'), JSON.stringify(result))
    await prepareBundle(source, path.join(root, 'no-prediction'))
    manifest.samples[0].categoryTaxonomy = null; await writeManifest()
    await assert.rejects(() => prepareBundle(source, path.join(root, 'no-taxonomy')), /Invalid category reference/)
    manifest.schemaVersion = 1; manifest.samples[0].categoryTaxonomy = 'internal-costs-v1'; await writeManifest()
    await assert.rejects(() => prepareBundle(source, path.join(root, 'legacy')), /Invalid category reference/)
  } finally { await rm(root, { recursive: true, force: true }) }
})
