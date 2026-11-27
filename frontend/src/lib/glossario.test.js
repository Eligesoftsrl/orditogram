import { describe, expect, it } from 'vitest'
import { articolo, creaGlossario, forma, PREDEFINITO } from './glossario.js'

describe('articolo', () => {
  it('maschile singolare: il, lo, l\'', () => {
    expect(articolo('comune')).toBe('il')
    expect(articolo('studio')).toBe('lo')
    expect(articolo('zaino')).toBe('lo')
    expect(articolo('psicologo')).toBe('lo')
    expect(articolo('ente')).toBe("l'")
    expect(articolo('ufficio')).toBe("l'")
  })
  it('femminile singolare: la, l\'', () => {
    expect(articolo('società', 'F')).toBe('la')
    expect(articolo('azienda', 'F')).toBe("l'")
  })
  it('plurale: i, gli, le', () => {
    expect(articolo('cittadini', 'M', true)).toBe('i')
    expect(articolo('enti', 'M', true)).toBe('gli')
    expect(articolo('studi', 'M', true)).toBe('gli')
    expect(articolo('aziende', 'F', true)).toBe('le')
  })
})

describe('forma', () => {
  it('usa i predefiniti senza glossario dal server', () => {
    expect(forma(undefined, 'ente', 'del')).toBe("dell'ente")
    expect(forma(null, 'esterno', 'nomi')).toBe('cittadini')
    expect(forma({}, 'ufficio')).toBe(PREDEFINITO.ufficio.singolare)
  })
  it('applica le parole scelte con articoli e preposizioni giusti', () => {
    const azienda = { ente: { singolare: 'azienda', plurale: 'aziende', genere: 'F' } }
    expect(forma(azienda, 'ente', 'del')).toBe("dell'azienda")
    expect(forma(azienda, 'ente', 'al')).toBe("all'azienda")
    const societa = { ente: { singolare: 'società', plurale: 'società', genere: 'F' } }
    expect(forma(societa, 'ente', 'del')).toBe('della società')
    expect(forma(societa, 'ente', 'dei')).toBe('delle società')
    const studio = { ente: { singolare: 'studio', plurale: 'studi', genere: 'M' } }
    expect(forma(studio, 'ente', 'dal')).toBe('dallo studio')
    expect(forma(studio, 'ente', 'i')).toBe('gli studi')
    const clienti = { esterno: { singolare: 'cliente', plurale: 'clienti', genere: 'M' } }
    expect(forma(clienti, 'esterno', 'ai')).toBe('ai clienti')
  })
})

describe('creaGlossario', () => {
  it('mette la maiuscola quando la forma la chiede', () => {
    const g = creaGlossario({ esterno: { singolare: 'socio', plurale: 'soci', genere: 'M' } })
    expect(g('esterno', 'Nomi')).toBe('Soci')
    expect(g('esterno', 'Il')).toBe('Il socio')
    expect(g('ente', 'Del')).toBe("Dell'ente")
    expect(g('esterno')).toBe('socio')
    expect(g.termine('esterno').plurale).toBe('soci')
  })
})

describe('pratica', () => {
  const ticket = creaGlossario({ pratica: { singolare: 'ticket', plurale: 'ticket', genere: 'M' } })
  const richiesta = creaGlossario({ pratica: { singolare: 'richiesta', plurale: 'richieste', genere: 'F' } })
  it('accorda articoli e aggettivi al genere scelto', () => {
    expect(creaGlossario(null)('pratica', 'un')).toBe('una pratica')
    expect(ticket('pratica', 'un')).toBe('un ticket')
    expect(ticket('pratica', 'Il')).toBe('Il ticket')
    expect(ticket('pratica', 'dei')).toBe('dei ticket')
    expect(ticket.agg('pratica', 'nuov')).toBe('nuovo')
    expect(ticket.agg('pratica', 'conclus', true)).toBe('conclusi')
    expect(richiesta('pratica', 'del')).toBe('della richiesta')
    expect(richiesta('pratica', 'un')).toBe('una richiesta')
    expect(richiesta.agg('pratica', 'apert', true)).toBe('aperte')
    expect(creaGlossario({ pratica: { singolare: 'istanza', plurale: 'istanze', genere: 'F' } })('pratica', 'un')).toBe("un'istanza")
    expect(creaGlossario({ pratica: { singolare: 'ordine', plurale: 'ordini', genere: 'M' } })('pratica', 'un')).toBe('un ordine')
  })
})

describe('termine processo', () => {
  it('vale "processo" finché l\'installazione non sceglie, poi la parola scelta con i suoi articoli', () => {
    expect(creaGlossario(null)('processo', 'nessun')).toBe('nessun processo')
    const bando = creaGlossario({ processo: { singolare: 'bando', plurale: 'bandi', genere: 'M' } })
    expect(bando('processo', 'Nomi')).toBe('Bandi')
    expect(bando('processo', 'del')).toBe('del bando')
    expect(bando.agg('processo', 'altr', true)).toBe('altri')
  })
})
