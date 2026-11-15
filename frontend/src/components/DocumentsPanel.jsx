import { useCallback, useEffect, useRef, useState } from 'react'
import { ChevronDown, Download, Eye, FilePlus, FileText, FileUp, Layers, ShieldAlert, ShieldCheck, ShieldX } from 'lucide-react'
import { api, scarica } from '../api.js'
import { cx, dimensione, quando } from '../lib/format.js'
import { Badge, Button, Caricamento, Errore, Vuoto, useToast } from './ui.jsx'

const ESITO = {
  OK:         { tono: 'verde',     icona: ShieldCheck, testo: 'Firma valida' },
  ATTENZIONE: { tono: 'zafferano', icona: ShieldAlert, testo: 'Da controllare' },
  KO:         { tono: 'mattone',   icona: ShieldX,     testo: 'Firma non valida' },
  NESSUNA:    { tono: 'neutro',    icona: null,        testo: 'Non firmato' }
}

export function FirmaBadge({ esito }) {
  const e = ESITO[esito] || ESITO.NESSUNA
  return <Badge tono={e.tono} icona={e.icona}>{e.testo}</Badge>
}

/**
 * Documenti della pratica. Chi ha in carico un'attività con "documenti": "modifica"
 * può aggiungere documenti e caricare nuove versioni (il backend lo verifica).
 */
export default function DocumentsPanel({ pi, onChange }) {
  const [dati, setDati] = useState(null)
  const [errore, setErrore] = useState('')
  const toast = useToast()

  const carica = useCallback(async () => {
    try { setDati(await api.documenti(pi)) } catch (e) { setErrore(e.message) }
  }, [pi])
  useEffect(() => { carica() }, [carica])

  const dopoCaricamento = async (msg) => {
    toast.ok(msg)
    await carica()
    onChange?.()
  }

  if (errore) return <Errore>{errore}</Errore>
  if (!dati) return <Caricamento />

  return (
    <div className="space-y-3">
      {dati.documenti.length === 0 && !dati.modificabile && (
        <Vuoto icona={FileText} titolo="Nessun documento">La pratica non ha allegati.</Vuoto>
      )}
      {dati.documenti.map(d => (
        <Documento key={d.id} d={d} modificabile={dati.modificabile} onCaricato={dopoCaricamento} />
      ))}
      {dati.modificabile && <NuovoDocumento pi={pi} onCaricato={dopoCaricamento} />}
    </div>
  )
}

function Documento({ d, modificabile, onCaricato }) {
  const [aperto, setAperto] = useState(false)
  const [caricamento, setCaricamento] = useState(false)
  const corrente = d.versioni[0]
  const toast = useToast()
  if (!corrente) return null

  return (
    <article className="rounded-pannello border border-linea bg-white">
      <div className="flex items-start gap-3 p-4">
        <span className="mt-0.5 inline-flex size-10 shrink-0 items-center justify-center rounded-campo bg-carta text-pino-700">
          <FileText className="size-5" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <h4 className="truncate font-medium text-pino-900" title={d.nome}>{d.nome}</h4>
            {d.versioneCorrente > 1 && <Badge tono="pino">v{d.versioneCorrente}</Badge>}
            <FirmaBadge esito={corrente.firmaEsito} />
          </div>
          <p className="mt-1 text-[13px] text-grafite">
            {corrente.filename !== d.nome && <>{corrente.filename}, </>}
            {dimensione(corrente.dimensione)}. {corrente.caricatoDa}, {quando(corrente.caricatoIl)}
            {corrente.fase && <> ({corrente.fase})</>}
          </p>
          {corrente.firma?.descrizione && corrente.firmaEsito !== 'NESSUNA' && (
            <p className="mt-1 text-[13px] text-pino-800">{corrente.firma.descrizione}</p>
          )}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 border-t border-linea px-4 py-2.5">
        <AzioniVersione v={corrente} toast={toast} />
        {d.versioni.length > 1 && (
          <Button misura="sm" variante="fantasma" icona={Layers} onClick={() => setAperto(!aperto)} aria-expanded={aperto}>
            {aperto ? 'Nascondi' : `Versioni (${d.versioni.length})`}
          </Button>
        )}
        {d.versioni.length === 1 && corrente.firmaEsito !== 'NESSUNA' && (
          <Button misura="sm" variante="fantasma" icona={ChevronDown} onClick={() => setAperto(!aperto)} aria-expanded={aperto}>
            {aperto ? 'Nascondi' : 'Firme'}
          </Button>
        )}
        {modificabile && (
          <Button misura="sm" variante="secondario" icona={FileUp} className="ml-auto" onClick={() => setCaricamento(!caricamento)}>
            Nuova versione
          </Button>
        )}
      </div>

      {caricamento && (
        <div className="border-t border-linea bg-carta/60 p-4">
          <Carica etichetta={`Nuova versione di ${d.nome}`}
                  onInvia={async (file, nota) => {
                    const r = await api.nuovaVersione(d.id, file, nota)
                    setCaricamento(false)
                    await onCaricato(`Caricata la versione ${r.numero} di ${d.nome}`)
                  }}
                  onAnnulla={() => setCaricamento(false)} />
        </div>
      )}

      {aperto && (
        <ol className="divide-y divide-linea border-t border-linea">
          {d.versioni.map(v => (
            <li key={v.id} className="px-4 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="numeri inline-flex h-6 min-w-8 items-center justify-center rounded-md bg-carta px-1.5 text-[13px] font-semibold text-pino-800">v{v.numero}</span>
                <span className="text-[15px] text-pino-900">{v.filename}</span>
                <FirmaBadge esito={v.firmaEsito} />
                <span className="ml-auto"><AzioniVersione v={v} toast={toast} /></span>
              </div>
              <p className="mt-1 text-[13px] text-grafite">
                {v.caricatoDa}, {quando(v.caricatoIl)}{v.fase && <> in {v.fase}</>}
              </p>
              {v.nota && <p className="mt-1.5 rounded-campo bg-carta px-3 py-1.5 text-sm text-pino-900">{v.nota}</p>}
              <DettaglioFirma firma={v.firma} />
            </li>
          ))}
        </ol>
      )}
    </article>
  )
}

function AzioniVersione({ v, toast }) {
  const pdf = v.filename.toLowerCase().endsWith('.pdf')
  const cades = v.firmaTipo === 'CADES' && v.firma?.contenutoNome
  const esegui = (url, apri) => scarica(url, { apri }).catch(e => toast.errore(e.message))
  const m = 'sm'
  return (
    <div className="flex flex-wrap gap-1.5">
      {pdf && <Button misura={m} variante="fantasma" icona={Eye} onClick={() => esegui(api.urlFile(v.id), true)}>Apri</Button>}
      {cades && (
        <Button misura={m} variante="fantasma" icona={Eye} onClick={() => esegui(api.urlContenuto(v.id), true)}
                title={`Apri ${v.firma.contenutoNome} estratto dalla busta firmata`}>
          Apri
        </Button>
      )}
      <Button misura={m} variante="fantasma" icona={Download} onClick={() => esegui(api.urlFile(v.id), false)}>
        {cades ? 'Scarica .p7m' : 'Scarica'}
      </Button>
    </div>
  )
}

function DettaglioFirma({ firma }) {
  if (!firma?.firme?.length) return null
  return (
    <div className="mt-2 space-y-2">
      {firma.firme.map((f, i) => (
        <div key={i} className={cx('rounded-campo border px-3 py-2 text-[13px]',
          f.integra && f.certificatoValido !== false ? 'border-verde-100 bg-verde-50/60' : 'border-mattone-100 bg-mattone-50/60')}>
          <p className="font-medium text-pino-900">
            {f.tipo === 'marca temporale' ? 'Marca temporale' : 'Firma'} di {f.firmatario || 'firmatario sconosciuto'}
            {f.codiceFiscale && <span className="font-normal text-grafite">, CF {f.codiceFiscale}</span>}
          </p>
          <p className="mt-0.5 text-grafite">
            {f.data && <>Firmato il {quando(f.data)}. </>}
            {f.emittente && <>Certificato emesso da {f.emittente}. </>}
          </p>
          <p className="mt-0.5">
            <span className={f.integra ? 'text-verde-700' : 'text-mattone-700'}>{f.integra ? 'Documento integro' : 'Integrità non verificata'}</span>
            {f.certificatoValido != null && (
              <span className={f.certificatoValido ? 'text-verde-700' : 'text-mattone-700'}>
                , certificato {f.certificatoValido ? 'valido' : 'non valido'} alla data di firma
              </span>
            )}
            {f.copreTuttoDocumento === false && <span className="text-grafite">, firma su una versione precedente del PDF</span>}
          </p>
          {f.errore && <p className="mt-0.5 text-mattone-700">{f.errore}</p>}
        </div>
      ))}
      <p className="text-xs text-nebbia">
        Verifica tecnica di integrità e validità del certificato. Non controlla la catena di fiducia (elenchi AgID/UE) né la revoca.
      </p>
    </div>
  )
}

function NuovoDocumento({ pi, onCaricato }) {
  const [aperto, setAperto] = useState(false)
  if (!aperto) {
    return (
      <button onClick={() => setAperto(true)}
              className="flex w-full items-center justify-center gap-2 rounded-pannello border-2 border-dashed border-linea px-4 py-4 text-[15px] font-medium text-pino-800 hover:border-pino-200 hover:bg-white">
        <FilePlus className="size-4" /> Aggiungi un documento
      </button>
    )
  }
  return (
    <div className="rounded-pannello border border-linea bg-white p-4">
      <Carica etichetta="Nuovo documento"
              onInvia={async (file, nota) => {
                await api.aggiungiDocumento(pi, file, nota)
                setAperto(false)
                await onCaricato(`Documento ${file.name} caricato`)
              }}
              onAnnulla={() => setAperto(false)} />
    </div>
  )
}

function Carica({ etichetta, onInvia, onAnnulla }) {
  const [file, setFile] = useState(null)
  const [nota, setNota] = useState('')
  const [attesa, setAttesa] = useState(false)
  const [errore, setErrore] = useState('')
  const input = useRef(null)
  const invia = async () => {
    setAttesa(true); setErrore('')
    try { await onInvia(file, nota) } catch (e) { setErrore(e.message) } finally { setAttesa(false) }
  }
  return (
    <div className="space-y-3">
      <p className="text-[15px] font-medium text-pino-900">{etichetta}</p>
      <div className="flex flex-wrap items-center gap-3">
        <Button misura="sm" icona={FileUp} onClick={() => input.current?.click()}>{file ? 'Cambia file' : 'Scegli file'}</Button>
        <span className="min-w-0 truncate text-sm text-grafite">{file ? `${file.name}, ${dimensione(file.size)}` : 'Nessun file scelto'}</span>
        <input ref={input} type="file" className="hidden" onChange={e => setFile(e.target.files[0] || null)} />
      </div>
      <textarea className="campo" rows={2} placeholder="Nota sulla modifica (facoltativa)" value={nota} onChange={e => setNota(e.target.value)} />
      <Errore>{errore}</Errore>
      <div className="flex justify-end gap-2">
        <Button misura="sm" variante="fantasma" onClick={onAnnulla}>Annulla</Button>
        <Button misura="sm" variante="primario" disabled={!file} attesa={attesa} onClick={invia}>Carica</Button>
      </div>
    </div>
  )
}
