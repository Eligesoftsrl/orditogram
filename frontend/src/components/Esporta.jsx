import { useEffect, useRef, useState } from 'react'
import { ChevronDown, Download, FileSpreadsheet, FileText, LoaderCircle, Sheet } from 'lucide-react'
import { cx } from '../lib/format.js'
import { useToast } from './ui.jsx'

/**
 * Esportazione di una tabella in CSV, Excel e PDF, uguale per tutte le tabelle di Orditogram.
 *
 *   <Esporta nome="pratiche" titolo="Pratiche" sottotitolo="In corso, ricerca: scuola"
 *            colonne={[{ titolo: 'Titolo', valore: p => p.titolo, larghezza: 40 }, ...]} righe={lista} />
 *
 * Esporta esattamente le righe passate (quelle filtrate dalla ricerca), tutte, non solo quelle visibili.
 * Le librerie per Excel e PDF si caricano solo al primo uso.
 */
export default function Esporta({ nome, titolo, sottotitolo, colonne, righe, totale, carica, disabled }) {
  const [aperto, setAperto] = useState(false)
  const [lavoro, setLavoro] = useState(null)
  const box = useRef(null)
  const toast = useToast()

  useEffect(() => {
    if (!aperto) return
    const fuori = (e) => !box.current?.contains(e.target) && setAperto(false)
    const esc = (e) => e.key === 'Escape' && setAperto(false)
    document.addEventListener('mousedown', fuori)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', fuori); document.removeEventListener('keydown', esc) }
  }, [aperto])

  const esegui = async (formato) => {
    setAperto(false)
    setLavoro(formato)
    try {
      // con "carica" si scaricano tutte le righe della ricerca, non solo la pagina visibile
      const tutte = carica ? await carica() : (righe || [])
      const tabella = { titolo, sottotitolo, colonne, righe: tutte }
      const file = `${nome}-${oggi()}`
      if (formato === 'csv') scaricaBlob(csv(tabella), `${file}.csv`)
      if (formato === 'xlsx') scaricaBlob(await excel(tabella), `${file}.xlsx`)
      if (formato === 'pdf') scaricaBlob(await pdf(tabella), `${file}.pdf`)
    } catch (e) {
      toast.errore('Esportazione non riuscita: ' + e.message)
    } finally {
      setLavoro(null)
    }
  }

  const n = totale ?? (righe || []).length
  const voci = [
    ['xlsx', 'Excel', 'Foglio .xlsx', FileSpreadsheet],
    ['csv', 'CSV', 'Testo separato da punto e virgola', Sheet],
    ['pdf', 'PDF', 'Pronto da stampare o allegare', FileText]
  ]
  return (
    <div ref={box} className="relative">
      <button type="button" onClick={() => setAperto(!aperto)} disabled={disabled || !!lavoro}
              aria-haspopup="menu" aria-expanded={aperto}
              className="inline-flex h-10 items-center gap-2 rounded-campo border border-linea bg-white px-3.5 text-[15px] font-medium text-pino-900 hover:bg-carta disabled:opacity-50">
        {lavoro ? <LoaderCircle className="size-4 animate-spin" /> : <Download className="size-4" />}
        Esporta
        <ChevronDown className={cx('size-4 text-grafite transition-transform', aperto && 'rotate-180')} />
      </button>
      {aperto && (
        <div role="menu" className="absolute right-0 z-40 mt-1.5 w-64 overflow-hidden rounded-campo border border-linea bg-white py-1 shadow-sollevato">
          <p className="px-3.5 pb-1 pt-1.5 text-xs text-grafite">{n} {n === 1 ? 'riga' : 'righe'}</p>
          {voci.map(([k, l, d, Icona]) => (
            <button key={k} role="menuitem" type="button" onClick={() => esegui(k)}
                    className="flex w-full items-start gap-3 px-3.5 py-2 text-left hover:bg-carta">
              <Icona className="mt-0.5 size-4 text-verde-700" />
              <span>
                <span className="block text-[15px] font-medium text-pino-900">{l}</span>
                <span className="block text-xs text-grafite">{d}</span>
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// ------------------------------------------------------------------ formati

const testo = (v) => v == null ? '' : String(v)
const valori = ({ colonne, righe }) => righe.map((r, i) => colonne.map(c => c.valore(r, i)))

function oggi() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function generatoIl() {
  return new Intl.DateTimeFormat('it-IT', { day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date())
}

/**
 * Un testo che inizia con = + - @ o un tabulatore, aperto in Excel, diventerebbe una formula (CSV injection):
 * gli si mette davanti un apostrofo, che Excel non mostra. I numeri veri restano numeri.
 */
export function protetto(v) {
  const s = testo(v)
  return typeof v !== 'number' && /^[=+\-@\t\r]/.test(s) ? `'${s}` : s
}

/** CSV con separatore ";" e BOM: Excel in italiano lo apre già diviso in colonne e con gli accenti corretti. */
export function csv(t) {
  const cella = (v) => {
    const s = protetto(v)
    return /[";\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const righe = [t.colonne.map(c => cella(c.titolo)).join(';'), ...valori(t).map(r => r.map(cella).join(';'))]
  return new Blob(['﻿' + righe.join('\r\n')], { type: 'text/csv;charset=utf-8' })
}

async function excel(t) {
  const { default: writeXlsxFile } = await import('write-excel-file/browser')
  const dati = valori(t)
  const intestazione = t.colonne.map(c => ({ value: c.titolo, fontWeight: 'bold', color: '#FFFFFF', backgroundColor: '#0F2E2C' }))
  const corpo = dati.map(r => r.map(v => (
    v == null || v === '' ? null
      : typeof v === 'number' ? { value: v, type: Number }
      : { value: testo(v), type: String, wrap: testo(v).length > 60 }
  )))
  const columns = t.colonne.map((c, j) => ({
    width: c.larghezza || Math.min(60, Math.max(10, testo(c.titolo).length + 2, ...dati.slice(0, 200).map(r => testo(r[j]).length + 2)))
  }))
  return writeXlsxFile([intestazione, ...corpo], { columns, sheet: (t.titolo || 'Dati').slice(0, 31) }).toBlob()
}

async function pdf(t) {
  const [{ jsPDF }, { autoTable }] = await Promise.all([import('jspdf'), import('jspdf-autotable')])
  const doc = new jsPDF({ orientation: t.colonne.length > 4 ? 'landscape' : 'portrait', unit: 'mm', format: 'a4' })
  const larghezza = doc.internal.pageSize.getWidth()
  const altezza = doc.internal.pageSize.getHeight()
  const PINO = [15, 46, 44], VERDE = [14, 124, 102], ZAFFERANO = [242, 169, 59], GRAFITE = [92, 107, 104], CARTA = [244, 246, 245]

  // intestazione: il "binario" di Orditogram, titolo e contesto dell'esportazione
  const intestazione = () => {
    doc.setFillColor(...PINO)
    doc.rect(0, 0, larghezza, 22, 'F')
    ;[0, 1, 2].forEach(i => { doc.setFillColor(...(i === 2 ? ZAFFERANO : VERDE)); doc.circle(16 + i * 5, 11, 1.6, 'F') })
    doc.setTextColor(255, 255, 255)
    doc.setFont('helvetica', 'bold'); doc.setFontSize(13)
    doc.text('Orditogram', 31, 12.6)
    doc.setFont('helvetica', 'normal'); doc.setFontSize(9)
    doc.text(`Esportato il ${generatoIl()}`, larghezza - 14, 12.6, { align: 'right' })
  }
  intestazione()
  doc.setTextColor(...PINO)
  doc.setFont('helvetica', 'bold'); doc.setFontSize(17)
  doc.text(t.titolo || 'Esportazione', 14, 34)
  doc.setFont('helvetica', 'normal'); doc.setFontSize(10); doc.setTextColor(...GRAFITE)
  const n = t.righe.length
  doc.text([t.sottotitolo, `${n} ${n === 1 ? 'riga' : 'righe'}`].filter(Boolean).join('  ·  '), 14, 40.5)

  autoTable(doc, {
    startY: 46,
    head: [t.colonne.map(c => c.titolo)],
    body: valori(t).map(r => r.map(testo)),
    margin: { top: 30, left: 14, right: 14, bottom: 16 },
    styles: { font: 'helvetica', fontSize: 8.5, cellPadding: 2.2, textColor: PINO, lineColor: [220, 227, 224], lineWidth: 0.1, overflow: 'linebreak' },
    headStyles: { fillColor: PINO, textColor: [255, 255, 255], fontStyle: 'bold' },
    alternateRowStyles: { fillColor: CARTA },
    didDrawPage: (d) => {
      if (d.pageNumber > 1) intestazione()
      doc.setFontSize(8); doc.setTextColor(...GRAFITE)
      doc.text(t.titolo || '', 14, altezza - 8)
      doc.text(`Pagina ${d.pageNumber}`, larghezza - 14, altezza - 8, { align: 'right' })
    }
  })
  return doc.output('blob')
}

function scaricaBlob(blob, nomeFile) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nomeFile
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 2000)
}
