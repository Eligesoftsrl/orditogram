// Client verso il backend. Autenticazione Basic, conservata per la sessione della scheda.
const CHIAVE = 'orditogram.auth'
let authHeader = sessionStorage.getItem(CHIAVE)
let onUnauthorized = () => {}

export function setCredentials(user, pass) {
  authHeader = user ? 'Basic ' + btoa(unescape(encodeURIComponent(`${user}:${pass}`))) : null
  if (authHeader) sessionStorage.setItem(CHIAVE, authHeader)
  else sessionStorage.removeItem(CHIAVE)
}
export const hasCredentials = () => !!authHeader
export const setOnUnauthorized = (fn) => { onUnauthorized = fn }

/**
 * body: oggetto (JSON), FormData (upload) o stringa (inviata così com'è come JSON).
 * as:   'json' | 'text' | 'blob'
 */
async function call(method, url, body, as = 'json') {
  // X-Requested-With: Spring Security risponde 401 senza aprire il popup di login del browser
  const headers = { 'X-Requested-With': 'XMLHttpRequest' }
  if (authHeader) headers.Authorization = authHeader
  let payload
  if (body instanceof FormData) payload = body
  else if (typeof body === 'string') { payload = body; headers['Content-Type'] = 'application/json' }
  else if (body !== undefined) { payload = JSON.stringify(body); headers['Content-Type'] = 'application/json' }

  const res = await fetch(url, { method, headers, body: payload })
  if (res.status === 401) {
    const conCredenziali = !!authHeader
    onUnauthorized()
    throw new Error(conCredenziali ? 'Nome utente o password non corretti' : 'Sessione scaduta: accedi di nuovo')
  }
  if (res.status === 403) {
    let msg = 'Non hai i permessi per questa operazione'
    try { const j = await res.json(); if (j.message && j.message !== 'Forbidden') msg = j.message } catch { /* vuoto */ }
    throw new Error(msg)
  }
  if (!res.ok) {
    let msg = `Errore ${res.status}`
    try { const j = await res.json(); if (j.message) msg = j.message } catch { /* vuoto */ }
    throw new Error(msg)
  }
  if (as === 'blob') return res
  const text = await res.text()
  if (as === 'text') return text
  return text ? JSON.parse(text) : null
}

/** Scarica un file protetto (serve l'header di autenticazione, quindi niente link diretti). */
export async function scarica(url, { apri = false } = {}) {
  // la finestra va aperta subito, nel click dell'utente, altrimenti il browser la blocca
  const finestra = apri ? window.open('', '_blank') : null
  try {
    const res = await call('GET', url, undefined, 'blob')
    const blob = await res.blob()
    const nome = nomeDaHeader(res.headers.get('Content-Disposition')) || 'documento'
    const href = URL.createObjectURL(blob)
    if (finestra) {
      finestra.location.href = href
    } else {
      const a = document.createElement('a')
      a.href = href
      a.download = nome
      a.click()
    }
    setTimeout(() => URL.revokeObjectURL(href), 60_000)
  } catch (e) {
    finestra?.close()
    throw e
  }
}

function nomeDaHeader(h) {
  if (!h) return null
  const utf = /filename\*=UTF-8''([^;]+)/i.exec(h)
  if (utf) return decodeURIComponent(utf[1])
  const plain = /filename="?([^";]+)"?/i.exec(h)
  return plain ? plain[1] : null
}

export const api = {
  me: () => call('GET', '/api/me'),

  // processi e attività
  processes: () => call('GET', '/api/processes'),
  start: (key, values, files = []) => {
    if (!files.length) return call('POST', `/api/processes/${key}/start`, values || {})
    const fd = new FormData()
    fd.append('data', JSON.stringify(values || {}))
    files.forEach(f => fd.append('files', f))
    return call('POST', `/api/processes/${key}/start`, fd)
  },
  tasks: () => call('GET', '/api/tasks'),
  task: (id) => call('GET', `/api/tasks/${id}`),
  claim: (id) => call('POST', `/api/tasks/${id}/claim`),
  release: (id) => call('POST', `/api/tasks/${id}/release`),
  complete: (id, values) => call('POST', `/api/tasks/${id}/complete`, values || {}),

  // pratiche
  /** { righe, totale, pagina, dimensione } */
  pratiche: (stato = 'tutte', q = '', { processo = '', pagina = 0, dimensione = 25 } = {}) =>
    call('GET', `/api/pratiche?stato=${encodeURIComponent(stato)}&q=${encodeURIComponent(q)}&processo=${encodeURIComponent(processo)}&pagina=${pagina}&dimensione=${dimensione}`),
  registro: ({ q = '', azione = '', pagina = 0, dimensione = 50 } = {}) =>
    call('GET', `/api/admin/registro?q=${encodeURIComponent(q)}&azione=${encodeURIComponent(azione)}&pagina=${pagina}&dimensione=${dimensione}`),
  tipologie: () => call('GET', '/api/pratiche/tipologie'),
  pratica: (id) => call('GET', `/api/pratiche/${id}`),
  diagramma: (id) => call('GET', `/api/pratiche/${id}/diagramma`),

  // documenti
  documenti: (pi) => call('GET', `/api/pratiche/${pi}/documenti`),
  aggiungiDocumento: (pi, file, nota) => {
    const fd = new FormData(); fd.append('file', file); if (nota) fd.append('nota', nota)
    return call('POST', `/api/pratiche/${pi}/documenti`, fd)
  },
  nuovaVersione: (docId, file, nota) => {
    const fd = new FormData(); fd.append('file', file); if (nota) fd.append('nota', nota)
    return call('POST', `/api/documenti/${docId}/versioni`, fd)
  },
  urlFile: (versioneId) => `/api/documenti/versioni/${versioneId}/file`,
  urlContenuto: (versioneId) => `/api/documenti/versioni/${versioneId}/contenuto`,

  // notifiche
  notifiche: () => call('GET', '/api/notifiche'),
  notificaLetta: (id) => call('POST', `/api/notifiche/${id}/letta`),
  notificheLette: () => call('POST', '/api/notifiche/lette'),

  // amministrazione
  users: () => call('GET', '/api/admin/users'),
  createUser: (u) => call('POST', '/api/admin/users', u),
  updateUser: (id, u) => call('PUT', `/api/admin/users/${id}`, u),
  deleteUser: (id) => call('DELETE', `/api/admin/users/${id}`),
  ruoli: () => call('GET', '/api/admin/ruoli'),
  catalogoPermessi: () => call('GET', '/api/admin/ruoli/permessi'),
  creaRuolo: (r) => call('POST', '/api/admin/ruoli', r),
  modificaRuolo: (id, r) => call('PUT', `/api/admin/ruoli/${id}`, r),
  eliminaRuolo: (id) => call('DELETE', `/api/admin/ruoli/${id}`),
  adminProcesses: () => call('GET', '/api/admin/processes'),
  processXml: (id) => call('GET', `/api/admin/processes/${id}/xml`, undefined, 'text'),
  deploy: (formData) => call('POST', '/api/admin/processes/deploy', formData),
  pubblicaXml: (chiave, xml) => {
    const fd = new FormData()
    fd.append('bpmn', new File([xml], `${chiave}.bpmn20.xml`, { type: 'text/xml' }))
    return call('POST', '/api/admin/processes/deploy', fd)
  },
  processXmlPerChiave: (key) => call('GET', `/api/admin/processes/chiave/${key}/xml`, undefined, 'text'),
  valida: (xml) => call('POST', '/api/admin/processes/valida', { xml }),
  connettori: () => call('GET', '/api/admin/connettori'),
  gruppi: () => call('GET', '/api/admin/gruppi'),
  credenziali: () => call('GET', '/api/admin/credenziali'),
  registraMessaggio: (pi, nome, dati = {}) => call('POST', `/api/pratiche/${pi}/messaggi/${encodeURIComponent(nome)}`, dati),
  info: () => call('GET', '/api/info'),

  // integrazioni (Studio): chiavi dell'API pubblica, webhook, documentazione
  integrazioni: () => call('GET', '/api/admin/integrazioni/panoramica'),
  chiaviApi: () => call('GET', '/api/admin/integrazioni/chiavi'),
  creaChiaveApi: (d) => call('POST', '/api/admin/integrazioni/chiavi', d),
  modificaChiaveApi: (id, d) => call('PUT', `/api/admin/integrazioni/chiavi/${id}`, d),
  ruotaChiaveApi: (id) => call('POST', `/api/admin/integrazioni/chiavi/${id}/ruota`),
  revocaChiaveApi: (id) => call('POST', `/api/admin/integrazioni/chiavi/${id}/revoca`),
  webhook: () => call('GET', '/api/admin/integrazioni/webhook'),
  creaWebhook: (d) => call('POST', '/api/admin/integrazioni/webhook', d),
  modificaWebhook: (id, d) => call('PUT', `/api/admin/integrazioni/webhook/${id}`, d),
  eliminaWebhook: (id) => call('DELETE', `/api/admin/integrazioni/webhook/${id}`),
  segretoWebhook: (id) => call('GET', `/api/admin/integrazioni/webhook/${id}/segreto`),
  rigeneraSegretoWebhook: (id) => call('POST', `/api/admin/integrazioni/webhook/${id}/segreto`),
  provaWebhook: (id) => call('POST', `/api/admin/integrazioni/webhook/${id}/prova`),
  consegne: ({ webhook = '', stato = '', tipo = '', pratica = '', pagina = 0, dimensione = 25 } = {}) =>
    call('GET', `/api/admin/integrazioni/consegne?webhook=${webhook}&stato=${encodeURIComponent(stato)}&tipo=${encodeURIComponent(tipo)}&pratica=${encodeURIComponent(pratica)}&pagina=${pagina}&dimensione=${dimensione}`),
  consegna: (id) => call('GET', `/api/admin/integrazioni/consegne/${id}`),
  ripetiConsegna: (id) => call('POST', `/api/admin/integrazioni/consegne/${id}/ripeti`),
  openapi: () => call('GET', '/api/admin/integrazioni/openapi.json'),
  ricevuti: () => call('GET', '/api/admin/integrazioni/ricevuti'),
  svuotaRicevuti: () => call('DELETE', '/api/admin/integrazioni/ricevuti'),
  metodiAccesso: () => call('GET', '/api/auth/metodi'),
  calendario: (anno) => call('GET', `/api/calendario${anno ? `?anno=${anno}` : ''}`),
  anteprimaTermine: (giorni, modo) => call('GET', `/api/calendario/anteprima?giorni=${giorni}&modo=${encodeURIComponent(modo)}`),
  bozze: () => call('GET', '/api/admin/bozze'),
  bozza: (chiave) => call('GET', `/api/admin/bozze/${chiave}`),
  salvaBozza: (chiave, xml, nome) => call('PUT', `/api/admin/bozze/${chiave}`, { xml, nome }),
  scartaBozza: (chiave) => call('DELETE', `/api/admin/bozze/${chiave}`),
  urlPacchetto: (chiave) => `/api/admin/pacchetti/${chiave}`,
  importaPacchetto: (file, sovrascrivi = false) => {
    const fd = new FormData(); fd.append('file', file)
    return call('POST', `/api/admin/pacchetti?sovrascrivi=${sovrascrivi}`, fd)
  },
  forms: () => call('GET', '/api/admin/forms'),
  form: (key) => call('GET', `/api/admin/forms/${key}`),
  saveForm: (key, jsonText) => call('PUT', `/api/admin/forms/${key}`, jsonText),
  deleteForm: (key) => call('DELETE', `/api/admin/forms/${key}`)
}

/**
 * Chiamata all'API pubblica con una chiave (pagina "Documentazione API", pulsante Prova).
 * Non usa le credenziali dell'utente: solo la chiave indicata. Restituisce stato, durata e corpo.
 */
export async function chiamaApiPubblica(metodo, percorso, chiave, corpo) {
  const headers = { 'X-API-Key': chiave }
  let body
  if (corpo !== undefined && corpo !== null && metodo !== 'GET') {
    headers['Content-Type'] = 'application/json'
    body = typeof corpo === 'string' ? corpo : JSON.stringify(corpo)
  }
  const inizio = performance.now()
  const res = await fetch(`/api/v1${percorso}`, { method: metodo, headers, body, credentials: 'omit' })
  const testo = await res.text()
  let dati = testo
  try { dati = testo ? JSON.parse(testo) : null } catch { /* non JSON */ }
  return { stato: res.status, ok: res.ok, durata: Math.round(performance.now() - inizio), dati,
    tipo: res.headers.get('Content-Type') || '' }
}
