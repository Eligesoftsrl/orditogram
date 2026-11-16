// Permessi applicativi dell'utente (da /api/me). Il server controlla sempre: qui servono solo a mostrare
// le voci giuste. I codici sono quelli di backend accesso/Permesso.java.

export const PERMESSI = {
  processi: 'studio.processi',
  pubblica: 'studio.pubblica',
  form: 'studio.form',
  integrazioni: 'integrazioni.gestisci',
  utenti: 'utenti.gestisci',
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
  { chiave: 'integrazioni', to: '/studio/integrazioni', label: 'Integrazioni', permessi: [PERMESSI.integrazioni] },
  { chiave: 'registro', to: '/studio/registro', label: 'Registro', permessi: [PERMESSI.registro] }
]

export const sezioniStudio = (me) => SEZIONI_STUDIO.filter(s => puo(me, ...s.permessi))

/** Come presentare l'utente sotto il nome: amministratore, i suoi ruoli o i suoi gruppi. */
export function descriviUtente(me) {
  if (me?.admin) return 'Amministratore'
  const ruoli = (me?.ruoli || []).map(r => r.nome)
  if (ruoli.length) return ruoli.join(', ')
  return (me?.groups || []).join(', ') || 'Nessun gruppo'
}

/** "Responsabili di zona" -> "responsabili-di-zona": il codice proposto dal nome. */
export const codiceDa = (nome) => nome.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 60)
