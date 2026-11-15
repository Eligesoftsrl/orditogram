/**
 * Regole di validazione dei campi, uguali al backend (form/RegoleCampo.java): tienile allineate.
 *
 *   min, max                       numero: valori ammessi; data: "AAAA-MM-GG" oppure "oggi"
 *   minLunghezza, maxLunghezza     testo: numero di caratteri
 *   formato                        email, codiceFiscale, partitaIva, cap, telefono, iban, espressione
 *   espressione, messaggioFormato  con formato "espressione"
 *   minRighe, maxRighe             tabella: numero di righe (le colonne hanno le regole dei campi)
 *
 * Tabelle: righePiene ed erroriTabella seguono ValoriForm.righe ed erroriTabella del backend.
 */

export const FORMATI = [
  { id: 'email', label: 'Email' },
  { id: 'codiceFiscale', label: 'Codice fiscale' },
  { id: 'partitaIva', label: 'Partita IVA' },
  { id: 'iban', label: 'IBAN' },
  { id: 'cap', label: 'CAP' },
  { id: 'telefono', label: 'Telefono' },
  { id: 'espressione', label: 'Personalizzato (espressione regolare)' }
]

const CF_PERSONA = /^[A-Z]{6}[0-9LMNPQRSTUV]{2}[A-EHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$/
const UNDICI_CIFRE = /^[0-9]{11}$/
const CAP = /^[0-9]{5}$/
const TELEFONO = /^\+?[0-9][0-9 ./-]{5,19}$/
const IBAN = /^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$/
const MAX_ESPRESSIONE = 300

/** Una sola @, niente spazi, un punto nel dominio seguito da almeno due caratteri (come RegoleCampo.emailValida). */
export function emailValida(s) {
  if (typeof s !== 'string' || s.length > 254 || /\s/.test(s)) return false
  const at = s.indexOf('@')
  if (at < 1 || at !== s.lastIndexOf('@')) return false
  const dominio = s.slice(at + 1)
  const punto = dominio.lastIndexOf('.')
  return punto > 0 && dominio.length - punto - 1 >= 2
}

export function partitaIvaValida(p) {
  if (!UNDICI_CIFRE.test(p)) return false
  let somma = 0
  for (let i = 0; i < 10; i++) {
    let c = Number(p[i])
    if (i % 2 === 1) { c *= 2; if (c > 9) c -= 9 }
    somma += c
  }
  return (10 - somma % 10) % 10 === Number(p[10])
}

export function codiceFiscaleValido(cf) {
  if (UNDICI_CIFRE.test(cf)) return partitaIvaValida(cf)
  if (!CF_PERSONA.test(cf)) return false
  const dispari = [1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23]
  let somma = 0
  for (let i = 0; i < 15; i++) {
    const c = cf[i]
    const v = /[0-9]/.test(c) ? Number(c) : c.charCodeAt(0) - 65
    somma += i % 2 === 0 ? dispari[v] : v
  }
  return cf[15] === String.fromCharCode(65 + somma % 26)
}

export function ibanValido(iban) {
  if (!IBAN.test(iban)) return false
  if (iban.startsWith('IT') && iban.length !== 27) return false
  const numeri = (iban.slice(4) + iban.slice(0, 4)).split('').map(c => /[0-9]/.test(c) ? c : String(c.charCodeAt(0) - 55)).join('')
  let resto = 0
  for (const cifra of numeri) resto = (resto * 10 + Number(cifra)) % 97
  return resto === 1
}

const oggiIso = () => new Date().toLocaleDateString('sv-SE')   // AAAA-MM-GG nel fuso del browser
const data = (v, oggi) => {
  if (v === undefined || v === null || v === '') return null
  const s = String(v).trim()
  if (s.toLowerCase() === 'oggi') return oggi
  return /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : null
}
const giorno = (iso, origine) => String(origine).trim().toLowerCase() === 'oggi'
  ? 'giorno di oggi' : iso.split('-').reverse().join('/')
const numero = (v) => v === undefined || v === null || v === '' || Number.isNaN(Number(v)) ? null : Number(v)
const testoNumero = (n) => String(n).replace('.', ',')

/** Il messaggio d'errore per il valore, o null se va bene (un campo vuoto lo controllano gli obbligatori). */
export function controlla(f, valore, oggi = oggiIso()) {
  if (valore === undefined || valore === null) return null
  const s = String(valore).trim()
  if (s === '') return null
  const tipo = f.type || 'text'
  if (tipo === 'number') {
    const n = Number(s)
    if (Number.isNaN(n)) return 'deve essere un numero'
    const min = numero(f.min), max = numero(f.max)
    if (min !== null && n < min) return `deve essere almeno ${testoNumero(min)}`
    if (max !== null && n > max) return `deve essere al massimo ${testoNumero(max)}`
  } else if (tipo === 'date') {
    const min = data(f.min, oggi), max = data(f.max, oggi)
    if (min && s < min) return `non può essere prima del ${giorno(min, f.min)}`
    if (max && s > max) return `non può essere dopo il ${giorno(max, f.max)}`
  } else if (tipo === 'text' || tipo === 'textarea') {
    const lunghezza = [...s].length
    const min = numero(f.minLunghezza), max = numero(f.maxLunghezza)
    if (min !== null && lunghezza < min) return `deve avere almeno ${min} ${min === 1 ? 'carattere' : 'caratteri'}`
    if (max !== null && lunghezza > max) return `può avere al massimo ${max} ${max === 1 ? 'carattere' : 'caratteri'}`
    if (f.formato) return formato(f, s)
  }
  return null
}

function formato(f, s) {
  const compatto = s.replace(/ /g, '').toUpperCase()
  switch (f.formato) {
    case 'email': return emailValida(s) ? null : 'deve essere un indirizzo email valido'
    case 'codiceFiscale': return codiceFiscaleValido(compatto) ? null : 'deve essere un codice fiscale valido'
    case 'partitaIva': return partitaIvaValida(compatto) ? null : 'deve essere una partita IVA valida (11 cifre)'
    case 'cap': return CAP.test(s) ? null : 'deve essere un CAP di 5 cifre'
    case 'telefono': return TELEFONO.test(s) ? null : 'deve essere un numero di telefono valido'
    case 'iban': return ibanValido(compatto) ? null : 'deve essere un IBAN valido'
    case 'espressione': {
      if (!f.espressione) return null
      // il controllo vero lo fa il server (con un limite di tempo); qui si aiuta chi compila
      if (f.espressione.length > MAX_ESPRESSIONE || s.length > 10000) return f.messaggioFormato || 'non è nel formato richiesto'
      let ok
      try { ok = new RegExp(`^(?:${f.espressione})$`).test(s) } catch { return null }
      return ok ? null : (f.messaggioFormato || 'non è nel formato richiesto')
    }
    default: return null
  }
}

export const MAX_RIGHE = 500
const vuoto = (v) => v === undefined || v === null || v === false || String(v).trim() === ''

/** Le righe con almeno una cella compilata, solo con le colonne previste; le caselle non spuntate valgono false. */
export function righePiene(f, righe) {
  if (!Array.isArray(righe)) return []
  const colonne = (f.colonne || []).filter(c => c.name)
  const out = []
  for (const r of righe) {
    if (!r || typeof r !== 'object') continue
    const pulita = {}
    for (const c of colonne) if (!vuoto(r[c.name])) pulita[c.name] = typeof r[c.name] === 'string' ? r[c.name].trim() : r[c.name]
    if (Object.keys(pulita).length === 0) continue
    for (const c of colonne) if (c.type === 'checkbox' && !(c.name in pulita)) pulita[c.name] = false
    out.push(pulita)
  }
  return out
}

const quanteRighe = (n) => `${n} ${n === 1 ? 'riga' : 'righe'}`

/** Problemi delle righe già pulite: numero di righe, colonne obbligatorie, regole. Al massimo tre, uniti da "; ". */
export function erroriTabella(f, righe, oggi = oggiIso()) {
  const out = []
  const min = numero(f.minRighe), max = numero(f.maxRighe)
  const limite = max !== null ? Math.min(max, MAX_RIGHE) : MAX_RIGHE
  if (min !== null && righe.length < min) out.push(`servono almeno ${quanteRighe(min)}`)
  if (righe.length > limite) out.push(`al massimo ${quanteRighe(limite)}`)
  for (let i = 0; i < righe.length && out.length < 3; i++) {
    for (const c of (f.colonne || [])) {
      const v = righe[i][c.name]
      let msg
      if (c.required && c.type === 'checkbox') msg = v === true ? null : 'va spuntato'
      else if (c.required && vuoto(v)) msg = 'è obbligatorio'
      else msg = controlla(c, v, oggi)
      if (msg) {
        out.push(`riga ${i + 1}, ${c.label || c.name}: ${msg}`)
        if (out.length >= 3) break
      }
    }
  }
  return out.length ? out.join('; ') : null
}

/** Errori di tutti i campi da compilare e visibili, obbligatori compresi: { nome: messaggio }. */
export function erroriForm(fields = [], valori = {}, nascosti = new Set(), oggi = oggiIso()) {
  const out = {}
  for (const f of fields) {
    if (!f.name || f.readonly || f.type === 'sezione' || nascosti.has(f.name)) continue
    const v = valori[f.name]
    if (f.type === 'tabella') {
      const righe = righePiene(f, v)
      if (f.required && righe.length === 0) { out[f.name] = 'aggiungi almeno una riga'; continue }
      const m = erroriTabella(f, righe, oggi)
      if (m) out[f.name] = m
      continue
    }
    if (f.required && f.type === 'checkbox' && v !== true) { out[f.name] = 'va spuntato per proseguire'; continue }
    if (f.required && (v === undefined || v === null || String(v).trim() === '')) { out[f.name] = 'è obbligatorio'; continue }
    const m = controlla(f, v, oggi)
    if (m) out[f.name] = m
  }
  return out
}

/** "minimo 10, massimo 100", "email": riassunto delle regole per il costruttore dei form. */
export function descriviRegole(f) {
  const parti = []
  if (f.min !== undefined && f.min !== '') parti.push(`da ${f.min}`)
  if (f.max !== undefined && f.max !== '') parti.push(`fino a ${f.max}`)
  if (f.minLunghezza) parti.push(`almeno ${f.minLunghezza} caratteri`)
  if (f.maxLunghezza) parti.push(`al massimo ${f.maxLunghezza} caratteri`)
  if (f.minRighe) parti.push(`almeno ${quanteRighe(Number(f.minRighe))}`)
  if (f.maxRighe) parti.push(`al massimo ${quanteRighe(Number(f.maxRighe))}`)
  if (f.formato) parti.push(FORMATI.find(x => x.id === f.formato)?.label.toLowerCase() || f.formato)
  return parti.join(', ')
}
