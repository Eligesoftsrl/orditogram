#!/usr/bin/env node
/**
 * Immagine PNG del diagramma, come lo mostra lo Studio (stesso bpmn-js).
 * Serve per controllare il disegno e per la documentazione del cliente.
 *
 *     node anteprima.mjs processo.bpmn20.xml diagramma.png
 *
 * Usa bpmn-js da frontend/node_modules (cd frontend && npm ci) e Playwright con Chromium.
 */
import { readFileSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const [, , ingresso, uscita = 'diagramma.png'] = process.argv
if (!ingresso) { console.error('Uso: node anteprima.mjs processo.bpmn20.xml diagramma.png'); process.exit(2) }

const qui = dirname(fileURLToPath(import.meta.url))
const radiceRepo = resolve(qui, '../../../..')
const viewer = resolve(radiceRepo, 'frontend/node_modules/bpmn-js/dist/bpmn-viewer.production.min.js')
const css = resolve(radiceRepo, 'frontend/node_modules/bpmn-js/dist/assets/bpmn-js.css')
if (!existsSync(viewer)) { console.error('Manca bpmn-js: lancia "cd frontend && npm ci"'); process.exit(2) }

const require = createRequire(import.meta.url)
let playwright
try { playwright = require('playwright') } catch { playwright = require(resolve(radiceRepo, 'frontend/node_modules/playwright')) }

const xml = readFileSync(ingresso, 'utf8')
const browser = await playwright.chromium.launch()
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 }, deviceScaleFactor: 2 })
await page.setContent(`<!doctype html><html><head><style>${readFileSync(css, 'utf8')}
  html,body{margin:0;background:#fff} #tela{width:4000px;height:3000px}</style></head>
  <body><div id="tela"></div></body></html>`)
await page.addScriptTag({ content: readFileSync(viewer, 'utf8') })
const esito = await page.evaluate(async (xml) => {
  const v = new window.BpmnJS({ container: '#tela' })
  try {
    const { warnings } = await v.importXML(xml)
    const canvas = v.get('canvas')
    const box = canvas.getActiveLayer().getBBox()
    return { ok: true, warnings: warnings.map(w => w.message), box: { x: box.x, y: box.y, w: box.width, h: box.height } }
  } catch (e) { return { ok: false, errore: e.message } }
}, xml)
if (!esito.ok) { console.error('bpmn-js non riesce a leggere il diagramma: ' + esito.errore); await browser.close(); process.exit(1) }
const { x, y, w, h } = esito.box
await page.setViewportSize({ width: Math.ceil(x + w + 40), height: Math.ceil(y + h + 40) })
await page.screenshot({ path: uscita, clip: { x: Math.max(0, x - 20), y: Math.max(0, y - 20), width: w + 40, height: h + 40 } })
for (const m of esito.warnings) console.warn('avviso bpmn-js: ' + m)
console.log(`Immagine scritta in ${uscita}`)
await browser.close()
