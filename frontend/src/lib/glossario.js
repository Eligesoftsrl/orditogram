// Glossario dell'installazione (Studio → Glossario, backend glossario/Termine.java): le parole con cui ogni
// installazione chiama i concetti comuni. Qui le forme con l'articolo, per scrivere testi che valgono per
// "l'ente", "la società", "il comune", "lo studio".

/** Le parole predefinite, uguali a quelle del backend: valgono finché /api/info non risponde. */
export const PREDEFINITO = {
  ente: { singolare: 'ente', plurale: 'enti', genere: 'M' },
  ufficio: { singolare: 'Ufficio', plurale: 'Uffici', genere: 'M' },
  esterno: { singolare: 'cittadino', plurale: 'cittadini', genere: 'M' }
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
  da: { il: 'dal', lo: 'dallo', "l'": "dall'", la: 'dalla', i: 'dai', gli: 'dagli', le: 'dalle' }
}

const unisci = (art, parola) => art.endsWith("'") ? art + parola : `${art} ${parola}`

/** La parola di un termine del glossario, unito a quello predefinito (anche se il server ne manda una parte). */
export function termine(glossario, chiave) {
  return { ...PREDEFINITO[chiave], ...(glossario?.[chiave] || {}) }
}

/**
 * Una forma della parola. forma: 'nome' (cittadino), 'nomi' (cittadini), 'il' (il cittadino), 'i' (i cittadini),
 * 'del' / 'al' / 'dal' (del cittadino), 'dei' / 'ai' / 'dai' (dei cittadini).
 */
export function forma(glossario, chiave, forma = 'nome') {
  const t = termine(glossario, chiave)
  const plurale = ['nomi', 'i', 'dei', 'ai', 'dai'].includes(forma)
  const parola = plurale ? t.plurale : t.singolare
  if (forma === 'nome' || forma === 'nomi') return parola
  const art = articolo(parola, t.genere, plurale)
  const prep = { del: 'di', dei: 'di', al: 'a', ai: 'a', dal: 'da', dai: 'da' }[forma]
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
  return g
}
