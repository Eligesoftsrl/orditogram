// Permessi applicativi dell'utente (da /api/me). Il server controlla sempre: qui servono solo a mostrare
// le voci giuste. I codici sono quelli di backend accesso/Permesso.java.

export const PERMESSI = {
  processi: 'studio.processi',
  pubblica: 'studio.pubblica',
  form: 'studio.form',
  integrazioni: 'integrazioni.gestisci',
  utenti: 'utenti.gestisci',
  organizzazioni: 'organizzazioni.gestisci',
  registro: 'registro.leggi',
  praticheTutte: 'pratiche.vedi-tutte'
}

/** L'utente ha almeno uno dei permessi indicati. */
export function puo(me, ...permessi) {
  const suoi = me?.permessi || []
  return permessi.some(p => suoi.includes(p))
}

/** Sezioni dello Studio, ognuna con i permessi che la aprono (basta uno). */
export const SEZIONI_STUDIO = [
  { chiave: 'processi', to: '/studio/processi', label: 'Processi', permessi: [PERMESSI.processi] },
  { chiave: 'form', to: '/studio/form', label: 'Form', permessi: [PERMESSI.form] },
  { chiave: 'utenti', to: '/studio/utenti', label: 'Utenti', permessi: [PERMESSI.utenti] },
  { chiave: 'ruoli', to: '/studio/ruoli', label: 'Ruoli', permessi: [PERMESSI.utenti] },
  { chiave: 'strutture', to: '/studio/strutture', label: 'Strutture', permessi: [PERMESSI.utenti] },
  { chiave: 'organizzazioni', to: '/studio/organizzazioni', label: 'Organizzazioni', permessi: [PERMESSI.organizzazioni] },
  { chiave: 'integrazioni', to: '/studio/integrazioni', label: 'Integrazioni', permessi: [PERMESSI.integrazioni] },
  { chiave: 'registro', to: '/studio/registro', label: 'Registro', permessi: [PERMESSI.registro] }
]

export const sezioniStudio = (me) => SEZIONI_STUDIO.filter(s => puo(me, ...s.permessi))

/** Come presentare l'utente sotto il nome: amministratore, i suoi ruoli o i suoi gruppi. */
export function descriviUtente(me) {
  if (me?.esterno) return 'Utente esterno'
  if (me?.admin) return 'Amministratore'
  const ruoli = (me?.ruoli || []).map(r => r.nome)
  if (ruoli.length) return ruoli.join(', ')
  return (me?.groups || []).join(', ') || 'Nessun gruppo'
}

/** "Responsabili di zona" -> "responsabili-di-zona": il codice proposto dal nome. */
export const codiceDa = (nome) => nome.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 60)

// ------------------------------------------------------------ permessi per processo (accesso/PermessoProcesso)

export const AZIONI_PROCESSO = [
  { codice: 'AVVIA', titolo: 'Chi può avviarlo', aiuto: 'Se indichi qualcuno, vale questo elenco al posto dei gruppi di avvio del BPMN.' },
  { codice: 'VEDE', titolo: 'Chi vede tutte le pratiche', aiuto: 'Supervisori e responsabili: vedono le pratiche anche senza lavorarle, entro l\'ambito scelto.' },
  { codice: 'ESPORTA', titolo: 'Chi può esportare le pratiche', aiuto: 'Se indichi qualcuno, solo loro esportano l\'elenco delle pratiche di questo processo.' }
]

export const AMBITI = [
  { codice: 'TUTTE', nome: 'tutte le pratiche' },
  { codice: 'STRUTTURA', nome: 'della propria struttura' },
  { codice: 'STRUTTURA_E_SOTTO', nome: 'della propria struttura e di quelle sotto' }
]

/** "gruppo supervisori, tutte le pratiche" / "ruolo Analista". */
export function descriviRegola(r, nomiRuoli = {}) {
  const chi = r.tipoSoggetto === 'RUOLO' ? `ruolo ${nomiRuoli[r.soggetto] || r.soggetto}` : `gruppo ${r.soggetto}`
  if (r.azione !== 'VEDE') return chi
  return `${chi}, ${(AMBITI.find(a => a.codice === r.ambito) || AMBITI[0]).nome}`
}

/** Le strutture in ordine di albero, con la profondità: per elenchi rientrati e menu a tendina. */
export function alberoStrutture(strutture) {
  const figli = new Map()
  const ids = new Set(strutture.map(s => s.id))
  for (const s of strutture) {
    const padre = s.padreId != null && ids.has(s.padreId) ? s.padreId : null
    if (!figli.has(padre)) figli.set(padre, [])
    figli.get(padre).push(s)
  }
  const out = []
  const visita = (padre, livello, visti) => {
    for (const s of (figli.get(padre) || []).sort((a, b) => a.nome.localeCompare(b.nome, 'it'))) {
      if (visti.has(s.id)) continue
      out.push({ ...s, livello })
      visita(s.id, livello + 1, new Set([...visti, s.id]))
    }
  }
  visita(null, 0, new Set())
  return out
}
