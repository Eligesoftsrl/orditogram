import { describe, expect, it } from 'vitest'
import { AZIONI_ANAGRAFICA, campiCambiati, colonneEsportazione, descriviImportazione, valoriScheda } from './anagrafiche.js'

const definizione = {
  fields: [
    { type: 'sezione', label: 'Dati' },
    { name: 'nome', label: 'Nome', type: 'text', titolo: true },
    { name: 'attiva', label: 'Attiva', type: 'checkbox' },
    { name: 'societa', label: 'Società', type: 'anagrafica', tipoAnagrafica: 'societa' },
    { name: 'quote', label: 'Quote', type: 'tabella', colonne: [{ name: 'anno', label: 'Anno' }, { name: 'importo', label: 'Importo' }] }
  ]
}

describe('anagrafiche', () => {
  it('le schede collegate mostrano il nome, o il numero se non si vedono', () => {
    const v = valoriScheda({
      dati: { nome: 'Fondo', societa: 7, altro: 9 },
      collegamenti: { societa: { id: 7, titolo: 'Alfa S.p.A.', visibile: true }, altro: { id: 9, visibile: false } }
    })
    expect(v.societa_titolo).toBe('Alfa S.p.A.')
    expect(v.altro_titolo).toBe('Scheda n. 9')
  })

  it('esportazione reimportabile: etichette, numero e nome delle schede collegate, tabelle in testo', () => {
    const colonne = colonneEsportazione(definizione)
    expect(colonne.map(c => c.titolo)).toEqual(['Nome', 'Attiva', 'Società', 'Società (nome)', 'Quote', 'Stato'])
    const riga = { stato: 'ATTIVA', dati: { nome: 'Fondo', attiva: true, societa: 7, societa_titolo: 'Alfa', quote: [{ anno: 2025, importo: 10 }, { anno: 2026 }] } }
    expect(colonne.map(c => c.valore(riga))).toEqual(['Fondo', 'sì', 7, 'Alfa', 'Anno: 2025, Importo: 10 | Anno: 2026', 'attiva'])
    expect(colonne.map(c => c.valore({ stato: 'CESSATA', dati: {} }))).toEqual(['', '', '', '', '', 'cessata'])
  })

  it('campi cambiati tra due versioni, con le etichette', () => {
    expect(campiCambiati(definizione, { nome: 'A', attiva: true }, { nome: 'B', attiva: true, societa: 3 })).toEqual(['Nome', 'Società'])
    expect(campiCambiati(definizione, null, { nome: 'A' })).toEqual(['Nome'])
  })

  it('esito di un\'importazione', () => {
    expect(descriviImportazione({ create: 1, aggiornate: 2, invariate: 0, scartate: ['Riga 3: x'] })).toBe('1 nuova, 2 aggiornate, 1 riga scartata')
  })

  it('le azioni delle regole, dalla più ristretta', () => {
    expect(AZIONI_ANAGRAFICA.map(a => a.codice)).toEqual(['SCEGLIE', 'VEDE', 'MODIFICA', 'ESPORTA'])
  })
})
