// Glossario dell'installazione (Studio → Glossario, backend glossario/Termine.java): le parole con cui ogni
// installazione chiama i concetti comuni. Qui le forme con l'articolo, per scrivere testi che valgono per
// "l'ente", "la società", "il comune", "lo studio".

/** Le parole predefinite, uguali a quelle del backend: valgono finché /api/info non risponde. */
export const PREDEFINITO = {
  ente: { singolare: 'ente', plurale: 'enti', genere: 'M' },
  ufficio: { singolare: 'Ufficio', plurale: 'Uffici', genere: 'M' },
  esterno: { singolare: 'cittadino', plurale: 'cittadini', genere: 'M' },
  pratica: { singolare: 'pratica', plurale: 'pratiche', genere: 'F' },
  processo: { singolare: 'processo', plurale: 'processi', genere: 'M' }
}

const VOCALE = /^[aeiouàèéìòóùh]/i
// "lo" e "gli": s impura, z, gn, ps, pn, x, y, i semiconsonante ("lo studio", "lo zaino", "lo psicologo", "lo iato")
const IMPURA = /^(s[^aeiouàèéìòóù]|z|gn|ps|pn|x|y|i[aeiou])/i

/** Articolo determinativo: il, lo, l', la, i, gli, le. */
export function articolo(parola, genere = 'M', plurale = false) {
  const p = String(parola || '').trim()
  const f = genere === 'F'
  if (plurale) return f ? 'le' : (VOCALE.test(p) && !/^h/i.test(p) && !/^i[aeiou]/i.test(p)) || IMPURA.test(p) ? 'gli' : 'i'
  if (VOCALE.test(p) && !/^i[aeiou]/i.test(p)) return "l'"
  if (f) return 'la'
  return IMPURA.test(p) ? 'lo' : 'il'
}

const PREPOSIZIONI = {
  di: { il: 'del', lo: 'dello', "l'": "dell'", la: 'della', i: 'dei', gli: 'degli', le: 'delle' },
  a: { il: 'al', lo: 'allo', "l'": "all'", la: 'alla', i: 'ai', gli: 'agli', le: 'alle' },
  da: { il: 'dal', lo: 'dallo', "l'": "dall'", la: 'dalla', i: 'dai', gli: 'dagli', le: 'dalle' },
  in: { il: 'nel', lo: 'nello', "l'": "nell'", la: 'nella', i: 'nei', gli: 'negli', le: 'nelle' },
  su: { il: 'sul', lo: 'sullo', "l'": "sull'", la: 'sulla', i: 'sui', gli: 'sugli', le: 'sulle' }
}

const unisci = (art, parola) => art.endsWith("'") ? art + parola : `${art} ${parola}`

/** Articolo indeterminativo: un, uno, una, un'. */
export function indeterminativo(parola, genere = 'M') {
  const p = String(parola || '').trim()
  if (genere === 'F') return VOCALE.test(p) && !/^h/i.test(p) ? "un'" : 'una'
  return IMPURA.test(p) ? 'uno' : 'un'
}

/** La parola di un termine del glossario, unito a quello predefinito (anche se il server ne manda una parte). */
export function termine(glossario, chiave) {
  return { ...PREDEFINITO[chiave], ...(glossario?.[chiave] || {}) }
}

/**
 * Una forma della parola. forma: 'nome' (cittadino), 'nomi' (cittadini), 'il' (il cittadino), 'i' (i cittadini),
 * 'del' / 'al' / 'dal' (del cittadino), 'dei' / 'ai' / 'dai' / 'nei' / 'sui' (dei cittadini), 'nel' / 'sul', 'un' (un cittadino, una pratica),
 * 'nessun' (nessun ticket, nessuna pratica).
 */
export function forma(glossario, chiave, forma = 'nome') {
  const t = termine(glossario, chiave)
  const plurale = ['nomi', 'i', 'dei', 'ai', 'dai', 'nei', 'sui'].includes(forma)
  const parola = plurale ? t.plurale : t.singolare
  if (forma === 'nome' || forma === 'nomi') return parola
  if (forma === 'un') return unisci(indeterminativo(parola, t.genere), parola)
  if (forma === 'nessun') return unisci({ un: 'nessun', uno: 'nessuno', una: 'nessuna', "un'": "nessun'" }[indeterminativo(parola, t.genere)], parola)
  const art = articolo(parola, t.genere, plurale)
  const prep = { del: 'di', dei: 'di', al: 'a', ai: 'a', dal: 'da', dai: 'da', nel: 'in', nei: 'in', sul: 'su', sui: 'su' }[forma]
  return unisci(prep ? PREPOSIZIONI[prep][art] : art, parola)
}

/** Prima lettera maiuscola: "l'ente" → "L'ente". */
export const maiuscola = (s) => s ? s.charAt(0).toUpperCase() + s.slice(1) : s

/**
 * Funzione comoda per i componenti: g('ente', 'del') → "dell'ente"; g('esterno', 'Nomi') → "Cittadini"
 * (una forma con la maiuscola restituisce la parola con la maiuscola).
 */
export function creaGlossario(glossario) {
  const g = (chiave, f = 'nome') => {
    const grande = f.charAt(0) !== f.charAt(0).toLowerCase()
    const testo = forma(glossario, chiave, f.charAt(0).toLowerCase() + f.slice(1))
    return grande ? maiuscola(testo) : testo
  }
  g.termine = (chiave) => termine(glossario, chiave)
  /** Aggettivo accordato al termine: g.agg('pratica', 'nuov') → "nuova" / "nuovo"; plurale → "nuove" / "nuovi". */
  g.agg = (chiave, radice, plurale = false) => accorda(termine(glossario, chiave).genere, radice, plurale)
  return g
}

/** Desinenza di un aggettivo in -o/-a: radice "conclus" → conclusa, concluso, concluse, conclusi. */
export function accorda(genere, radice, plurale = false) {
  const f = genere === 'F'
  return radice + (plurale ? (f ? 'e' : 'i') : (f ? 'a' : 'o'))
}

// Glossario attivo, per i testi fuori dai componenti (costanti, editor BPMN, simulazione).
// Lo imposta GlossarioProvider appena /api/info risponde; fino ad allora valgono i predefiniti.
let attivo = creaGlossario(null)
export function impostaGlossario(glossario) { attivo = creaGlossario(glossario) }
/** Come g() di useGlossario, per il codice che non è un componente React. */
export const gl = (chiave, f) => attivo(chiave, f)
gl.agg = (chiave, radice, plurale) => attivo.agg(chiave, radice, plurale)


/** "Nuova pratica", "Nuovo ticket": il pulsante di avvio. */
export const nuovaPratica = () => `${maiuscola(gl.agg('pratica', 'nuov'))} ${gl('pratica')}`
/** "Pratica senza titolo", "Ticket senza titolo". */
export const senzaTitolo = () => `${gl('pratica', 'Nome')} senza titolo`
