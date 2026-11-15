import { describe, expect, it } from 'vitest'
import { componiTimer, descriviTimer, durata, leggiDurata, scomponiTimer, tipoBordo } from './timer.js'

const def = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, k === 'businessCalendarName' ? v : { body: v }]))

describe('timer in parole', () => {
  it('durate ISO', () => {
    expect(durata(30, 'giorni')).toBe('P30D')
    expect(durata(4, 'ore')).toBe('PT4H')
    expect(durata(2, 'settimane')).toBe('P2W')
    expect(durata(1, 'mesi')).toBe('P1M')
    expect(durata(0, 'giorni')).toBe('P1D')
    expect(leggiDurata('PT15M')).toEqual({ n: 15, unita: 'minuti' })
    expect(leggiDurata('PT10S')).toBeNull()
  })

  it('andata e ritorno', () => {
    for (const d of [{ timeDuration: 'P30D' }, { timeDate: '${scadenza}' }, { timeCycle: 'R3/P5D' }, { timeCycle: 'R/PT1H' }]) {
      const t = scomponiTimer(def(d))
      const { tipo, body } = componiTimer(t)
      expect({ [tipo]: body }).toEqual(d)
    }
  })

  it('calendario', () => {
    const t = scomponiTimer(def({ timeDuration: 'P30D', businessCalendarName: 'lavorativi' }))
    expect(t.calendario).toBe('lavorativi')
    expect(componiTimer(t).calendario).toBe('lavorativi')
    // i giorni lavorativi valgono solo per i giorni: con le settimane diventa proroga
    expect(componiTimer({ ...t, unita: 'settimane' }).calendario).toBe('proroga')
    // le ripetizioni non hanno calendario
    expect(componiTimer({ modo: 'ripeti', n: 5, unita: 'giorni', volte: 3 }).calendario).toBeUndefined()
  })

  it('descrizioni', () => {
    expect(descriviTimer(def({ timeDuration: 'P30D' }))).toBe('dopo 30 giorni')
    expect(descriviTimer(def({ timeDuration: 'P30D', businessCalendarName: 'lavorativi' }))).toBe('dopo 30 giorni lavorativi')
    expect(descriviTimer(def({ timeCycle: 'R3/P5D' }))).toBe('ogni 5 giorni, al massimo 3 volte')
    expect(descriviTimer(def({ timeCycle: 'R/PT1H' }))).toBe('ogni ora')
    expect(descriviTimer(def({ timeDate: '${scad}' }), [{ name: 'scad', label: 'Scadenza' }])).toBe('alla data in "Scadenza"')
    expect(descriviTimer(def({}))).toBe('tempo da impostare')
  })

  it('tipi di evento sul bordo', () => {
    const timer = { $type: 'bpmn:TimerEventDefinition' }
    expect(tipoBordo({ eventDefinitions: [timer] })).toBe('scadenza')
    expect(tipoBordo({ cancelActivity: false, eventDefinitions: [timer] })).toBe('sollecito')
    expect(tipoBordo({ eventDefinitions: [{ $type: 'bpmn:ErrorEventDefinition' }] })).toBe('errore')
    expect(tipoBordo({ eventDefinitions: [{ $type: 'bpmn:MessageEventDefinition' }] })).toBe('messaggio')
    expect(tipoBordo({})).toBeNull()
  })
})
