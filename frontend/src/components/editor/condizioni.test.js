import { describe, expect, it } from 'vitest'
import { componi, descrivi, famiglia, scomponi } from './condizioni.js'

describe('condizioni guidate', () => {
  it('compongono espressioni per Activiti', () => {
    expect(componi({ campo: 'esito', operatore: '==', valore: 'Accettata', tipo: 'select' })).toBe("${esito == 'Accettata'}")
    expect(componi({ campo: 'importo', operatore: '>=', valore: '1000', tipo: 'number' })).toBe('${importo >= 1000}')
    expect(componi({ campo: 'urgente', operatore: 'vero' })).toBe('${urgente == true}')
    expect(componi({ campo: 'note', operatore: 'pieno' })).toBe('${not empty note}')
    expect(componi({ campo: 'nome', operatore: '==', valore: "D'Angelo" })).toBe("${nome == 'D\\'Angelo'}")
  })

  it('andata e ritorno', () => {
    for (const c of [
      { campo: 'esito', operatore: '==', valore: 'Accettata' },
      { campo: 'importo', operatore: '<', valore: '50' },
      { campo: 'note', operatore: 'vuoto', valore: '' },
      { campo: 'flag', operatore: 'falso', valore: '' },
      { campo: 'nome', operatore: '!=', valore: "D'Angelo" }
    ]) expect(scomponi(componi({ ...c, tipo: /^\d+$/.test(c.valore) ? 'number' : 'text' }))).toEqual(c)
  })

  it('le espressioni non guidate restano avanzate', () => {
    expect(scomponi('${a == 1 && b == 2}')).toBeNull()
    expect(descrivi('${a == 1 && b == 2}', [])).toBe('Condizione avanzata')
  })

  it('frasi leggibili', () => {
    expect(descrivi("${esito == 'Accettata'}", [{ name: 'esito', label: 'Esito della verifica', type: 'select' }])).toBe('Esito della verifica è Accettata')
    expect(descrivi("${nome == 'Rossi'}", [{ name: 'nome', label: 'Cognome', type: 'text' }])).toBe('Cognome è uguale a Rossi')
    expect(descrivi('${importo > 100}', [{ name: 'importo', label: 'Importo', type: 'number' }])).toBe('Importo è maggiore di 100')
    expect(famiglia('checkbox')).toBe('sino')
  })
})
