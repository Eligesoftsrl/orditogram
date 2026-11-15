import { describe, expect, it } from 'vitest'
import { campiNascosti, condizione, descriviCondizione, raggruppa, senzaNascosti, valoriPossibili } from './formCondizioni.js'

const campi = [
  { name: 'oggetto', label: 'Oggetto', type: 'text', readonly: true },
  { type: 'sezione', label: 'Esito' },
  { name: 'esito', label: 'Esito', type: 'select', options: ['Accoglibile', 'Serve integrazione', 'Ci sono controinteressati'] },
  { name: 'noteIntegrazione', label: 'Cosa chiedere', type: 'textarea', required: true, visibileSe: { campo: 'esito', valori: ['Serve integrazione'] } },
  { name: 'urgente', label: 'Urgente', type: 'checkbox', visibileSe: { campo: 'esito', valori: ['Serve integrazione'] } },
  { name: 'motivoUrgenza', label: 'Perché', type: 'text', visibileSe: { campo: 'urgente', valori: ['true'] } },
  { type: 'sezione', label: 'Controinteressati', help: 'Solo se ce ne sono' },
  { name: 'emailControinteressati', label: 'Email', type: 'text', visibileSe: { campo: 'esito', valore: 'Ci sono controinteressati' } }
]

describe('campi condizionali', () => {
  it('nascondono i campi finché la condizione non è vera, anche a catena', () => {
    expect([...campiNascosti(campi, {})].sort()).toEqual(['emailControinteressati', 'motivoUrgenza', 'noteIntegrazione', 'urgente'])
    expect([...campiNascosti(campi, { esito: 'Serve integrazione' })].sort()).toEqual(['emailControinteressati', 'motivoUrgenza'])
    expect([...campiNascosti(campi, { esito: 'Serve integrazione', urgente: true })]).toEqual(['emailControinteressati'])
    expect(campiNascosti(campi, { esito: 'Accoglibile', urgente: true }).has('motivoUrgenza')).toBe(true)
  })

  it('non inviano i valori dei campi nascosti', () => {
    expect(senzaNascosti(campi, { esito: 'Accoglibile', noteIntegrazione: 'vecchia', urgente: false }))
      .toEqual({ esito: 'Accoglibile' })
  })

  it('usano i dati della pratica per i campi in sola lettura', () => {
    const f = [{ name: 'tipo', type: 'text', readonly: true }, { name: 'copie', type: 'number', visibileSe: { campo: 'tipo', valori: ['Copia'] } }]
    expect(senzaNascosti(f, { copie: '2' }, { tipo: 'Visione' })).toEqual({})
    expect(senzaNascosti(f, { copie: '2' }, { tipo: 'Copia' })).toEqual({ copie: '2' })
  })

  it('accettano "valore" singolo e ignorano condizioni incomplete', () => {
    expect(condizione({ visibileSe: { campo: 'a', valore: true } })).toEqual({ campo: 'a', valori: ['true'] })
    expect(condizione({ visibileSe: { valori: ['x'] } })).toBeNull()
    expect(condizione({})).toBeNull()
  })

  it('si descrivono in italiano', () => {
    expect(descriviCondizione(condizione(campi[3]), campi)).toBe('visibile se Esito è Serve integrazione')
    expect(descriviCondizione(condizione(campi[5]), campi)).toBe('visibile se Urgente è Sì')
    expect(valoriPossibili({ type: 'text' })).toEqual([])
  })
})

describe('sezioni', () => {
  it('raggruppano i campi e saltano i gruppi vuoti', () => {
    const g = raggruppa(campi.filter(f => !f.readonly))
    expect(g.map(x => x.sezione?.label ?? null)).toEqual(['Esito', 'Controinteressati'])
    expect(g[0].campi.map(f => f.name)).toEqual(['esito', 'noteIntegrazione', 'urgente', 'motivoUrgenza'])
    expect(raggruppa([{ name: 'a' }, { type: 'sezione', label: 'Vuota' }]).map(x => x.sezione)).toEqual([null])
  })
})
