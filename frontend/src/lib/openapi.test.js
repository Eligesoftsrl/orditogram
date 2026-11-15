import { describe, expect, it } from 'vitest'
import doc from './__fixtures__/openapi-accesso-atti.json'
import { campi, codice, compilaPercorso, esempio, risolvi, sezioni, tipoLeggibile } from './openapi.js'

// Documento generato dal backend (OpenApiGeneratore) per il processo "Accesso agli atti":
// se il formato cambia, questi test lo segnalano.

describe('documento OpenAPI di Orditogram', () => {
  it('raggruppa le operazioni per sezione nell\'ordine dei tag', () => {
    const s = sezioni(doc)
    expect(s.map(x => x.tag)).toEqual(['Processi', 'Pratiche', 'Documenti', 'Messaggi', 'Sistema'])
    const avvio = s[1].operazioni.find(o => o.percorso === '/processi/accessoAtti/pratiche')
    expect(avvio.metodo).toBe('POST')
    expect(avvio.corpo.json).toBe(true)
    expect(avvio.corpo.multipart).toBe(true)
    expect(avvio.corpo.esempio.dati.tipoAccesso).toBe('Visione')
  })

  it('legge i campi dello schema di avvio, con obbligatori e scelte', () => {
    const righe = campi(doc, { $ref: '#/components/schemas/Dati_accessoAtti' })
    const tipo = righe.find(r => r.nome === 'tipoAccesso')
    expect(tipo.obbligatorio).toBe(true)
    expect(tipo.valori).toEqual(['Visione', 'Copia'])
    expect(righe.find(r => r.nome === 'importo').tipo).toBe('numero')
    expect(righe.find(r => r.nome === 'privacy').tipo).toBe('sempre true')
  })

  it('unisce allOf e segue i riferimenti', () => {
    const creata = risolvi(doc, { $ref: '#/components/schemas/PraticaCreata' })
    expect(Object.keys(creata.properties)).toContain('creata')
    expect(Object.keys(creata.properties)).toContain('stato')
    expect(tipoLeggibile(doc, { type: 'array', items: { $ref: '#/components/schemas/Attivita' } })).toBe('elenco di oggetto')
  })

  it('costruisce esempi dagli schemi', () => {
    const p = esempio(doc, { $ref: '#/components/schemas/Pratica' })
    expect(p.stato).toBe('in_corso')
    expect(Array.isArray(p.attivitaCorrenti)).toBe(true)
  })
})

describe('esempi di codice', () => {
  const corpo = { riferimento: "DOM-1", dati: { oggetto: "L'accesso" } }
  it('curl con apici protetti', () => {
    const c = codice('curl', { metodo: 'POST', url: 'https://servizi.ente.it/api/v1/pratiche', corpo })
    expect(c).toContain("-H 'X-API-Key: $ORDITOGRAM_API_KEY'")
    expect(c).toContain("L'\\''accesso")
  })
  it('python con True/False', () => {
    expect(codice('python', { metodo: 'POST', url: 'u', corpo: { a: true, b: null } })).toContain('"a": True')
  })
  it('multipart in javascript', () => {
    expect(codice('javascript', { metodo: 'POST', url: 'u', corpo, multipart: true })).toContain("fd.append('documenti'")
  })
  it('compila i segnaposto del percorso', () => {
    expect(compilaPercorso('/pratiche/{id}/messaggi/{nome}', { id: '42' })).toBe('/pratiche/42/messaggi/{nome}')
  })
})
