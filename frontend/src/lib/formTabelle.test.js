import { describe, expect, it } from 'vitest'
import { erroriForm, erroriTabella, righePiene, descriviRegole } from './formRegole.js'

const OGGI = '2026-11-15'

// stesso form di TabelleFormTest nel backend: i messaggi devono coincidere
const VOCI = {
  name: 'voci', label: 'Voci di spesa', type: 'tabella', required: true, maxRighe: 3,
  colonne: [
    { name: 'descrizione', label: 'Descrizione', type: 'text', required: true, maxLunghezza: 20 },
    { name: 'importo', label: 'Importo', type: 'number', required: true, min: 0.01 },
    { name: 'data', label: 'Data', type: 'date', max: 'oggi' },
    { name: 'tipo', label: 'Tipo', type: 'select', options: ['Viaggio', 'Pasto'] },
    { name: 'fattura', label: 'Con fattura', type: 'checkbox' }
  ]
}

describe('tabelle di righe (uguali al backend)', () => {
  it('si tolgono righe vuote e colonne sconosciute', () => {
    expect(righePiene(VOCI, [
      { descrizione: ' Treno ', importo: '45.50', nascosta: 'x' },
      { descrizione: '', importo: '', fattura: false }
    ])).toEqual([{ descrizione: 'Treno', importo: '45.50', fattura: false }])
    expect(righePiene(VOCI, 'Treno')).toEqual([])
  })

  it('regole delle colonne riga per riga', () => {
    const righe = righePiene(VOCI, [
      { descrizione: 'Treno', importo: '45' },
      { descrizione: 'Cena con il cliente di Milano', importo: '0' },
      { importo: '12', data: '2026-11-16' }
    ])
    expect(erroriTabella(VOCI, righe, OGGI)).toBe('riga 2, Descrizione: può avere al massimo 20 caratteri; '
      + 'riga 2, Importo: deve essere almeno 0,01; riga 3, Descrizione: è obbligatorio')
  })

  it('numero di righe', () => {
    const riga = { descrizione: 'Treno', importo: '1' }
    expect(erroriTabella(VOCI, [riga, riga, riga, riga], OGGI)).toBe('al massimo 3 righe')
    expect(erroriTabella({ ...VOCI, minRighe: 2 }, [riga], OGGI)).toBe('servono almeno 2 righe')
    expect(descriviRegole({ minRighe: 1, maxRighe: 3 })).toBe('almeno 1 riga, al massimo 3 righe')
  })

  it('nel form: tabella obbligatoria vuota', () => {
    const fields = [VOCI]
    expect(erroriForm(fields, { voci: [{ descrizione: '', importo: '' }] }, new Set(), OGGI))
      .toEqual({ voci: 'aggiungi almeno una riga' })
    expect(erroriForm(fields, { voci: [{ descrizione: 'Treno', importo: '45' }] }, new Set(), OGGI)).toEqual({})
    expect(erroriForm([{ ...VOCI, readonly: true }], {}, new Set(), OGGI)).toEqual({})
  })
})
