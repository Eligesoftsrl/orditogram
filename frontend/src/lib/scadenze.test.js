import { describe, expect, it } from 'vitest'
import { contaScadenze } from './scadenze.js'

const ora = new Date('2026-11-17T12:00:00Z').getTime()
const t = (quando, tipo = 'scadenza') => ({ scadenze: [{ tipo, quando, nome: 'Scadenza' }] })

describe('contaScadenze', () => {
  it('conta le scadute e quelle entro due giorni', () => {
    const tasks = [t('2026-11-16T12:00:00Z'), t('2026-11-18T09:00:00Z'), t('2026-11-25T09:00:00Z'), { scadenze: [] }]
    expect(contaScadenze(tasks, ora)).toEqual({ scadute: 1, vicine: 1 })
  })
  it('ignora solleciti e attese', () => {
    expect(contaScadenze([t('2026-11-16T12:00:00Z', 'sollecito'), t('2026-11-16T12:00:00Z', 'attesa')], ora)).toEqual({ scadute: 0, vicine: 0 })
  })
})
