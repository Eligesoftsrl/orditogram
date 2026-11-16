import { describe, expect, it } from 'vitest'
import { alberoStrutture, codiceDa, descriviRegola, descriviUtente, puo, sezioniStudio } from './permessi.js'

const analista = { permessi: ['studio.processi', 'studio.form'], ruoli: [{ codice: 'analista', nome: 'Analista' }], groups: ['analisti'] }
const operatore = { permessi: [], ruoli: [], groups: ['operatori'] }
const admin = { admin: true, permessi: ['studio.processi', 'studio.form', 'integrazioni.gestisci', 'utenti.gestisci', 'registro.leggi', 'pratiche.vedi-tutte'], ruoli: [], groups: [] }

describe('permessi', () => {
  it('basta uno dei permessi indicati', () => {
    expect(puo(analista, 'studio.form')).toBe(true)
    expect(puo(analista, 'utenti.gestisci', 'studio.processi')).toBe(true)
    expect(puo(analista, 'utenti.gestisci')).toBe(false)
    expect(puo(null, 'studio.form')).toBe(false)
  })

  it('lo Studio mostra solo le sezioni permesse', () => {
    expect(sezioniStudio(analista).map(s => s.chiave)).toEqual(['processi', 'form'])
    expect(sezioniStudio(operatore)).toEqual([])
    expect(sezioniStudio(admin).map(s => s.chiave)).toEqual(['processi', 'form', 'utenti', 'ruoli', 'strutture', 'integrazioni', 'registro'])
  })

  it("descrive l'utente con ruoli o gruppi", () => {
    expect(descriviUtente(admin)).toBe('Amministratore')
    expect(descriviUtente(analista)).toBe('Analista')
    expect(descriviUtente(operatore)).toBe('operatori')
    expect(descriviUtente({ groups: [] })).toBe('Nessun gruppo')
  })
})

describe('codice del ruolo', () => {
  it('nasce dal nome, senza accenti né spazi', () => {
    expect(codiceDa('Responsabili di zona')).toBe('responsabili-di-zona')
    expect(codiceDa('Qualità & Città')).toBe('qualita-citta')
    expect(codiceDa('  -Auditor-  ')).toBe('auditor')
  })
})

describe('permessi per processo e strutture', () => {
  it('descrive le regole', () => {
    expect(descriviRegola({ azione: 'VEDE', tipoSoggetto: 'GRUPPO', soggetto: 'supervisori', ambito: 'STRUTTURA' }))
      .toBe('gruppo supervisori, della propria struttura')
    expect(descriviRegola({ azione: 'AVVIA', tipoSoggetto: 'RUOLO', soggetto: 'analista', ambito: 'TUTTE' }, { analista: 'Analista' }))
      .toBe('ruolo Analista')
  })

  it("mette le strutture in ordine d'albero", () => {
    const albero = alberoStrutture([
      { id: 3, nome: 'Ufficio B', padreId: 1 }, { id: 1, nome: 'Regione', padreId: null },
      { id: 2, nome: 'Ufficio A', padreId: 1 }, { id: 4, nome: 'Sportello', padreId: 2 }, { id: 5, nome: 'Altro ente', padreId: null }
    ])
    expect(albero.map(s => `${s.livello}:${s.nome}`)).toEqual(['0:Altro ente', '0:Regione', '1:Ufficio A', '2:Sportello', '1:Ufficio B'])
  })
})
