import { describe, expect, it } from 'vitest'
import { csv } from './Esporta.jsx'

describe('esportazione CSV', () => {
  it('punto e virgola, BOM, virgolette e a capo protetti', async () => {
    const blob = csv({
      colonne: [{ titolo: 'Titolo', valore: r => r.t }, { titolo: 'Note', valore: r => r.n }],
      righe: [{ t: 'Pratica; uno', n: 'dice "sì"\nriga 2' }, { t: 'Due', n: null }]
    })
    const byte = new Uint8Array(await blob.arrayBuffer())
    expect([...byte.slice(0, 3)]).toEqual([0xEF, 0xBB, 0xBF])   // BOM: Excel riconosce l'UTF-8
    const testo = new TextDecoder().decode(byte)                  // (il decoder toglie il BOM)
    expect(testo.split('\r\n')).toEqual([
      'Titolo;Note',
      '"Pratica; uno";"dice ""sì""\nriga 2"',
      'Due;'
    ])
  })
})
