import { describe, expect, it } from 'vitest'
import { etichettaAzione, etichettaLettura, parametri } from './registro.js'

describe('registro', () => {
  it('etichette di azioni e letture, codice se sconosciuto', () => {
    expect(etichettaAzione('ACCESSO')).toBe('Accesso')
    expect(etichettaAzione('USCITA')).toBe('Uscita')
    expect(etichettaAzione('NUOVA_COSA')).toBe('NUOVA_COSA')
    expect(etichettaLettura('PRATICA_APERTA')).toBe('Pratica aperta')
    expect(etichettaLettura('DOCUMENTO_SCARICATO')).toBe('Documento scaricato')
  })

  it('i filtri vuoti non vanno nella richiesta', () => {
    expect(parametri({ utente: 'giulia', esito: '', oggetto: null, pagina: 0 })).toBe('utente=giulia&pagina=0')
    expect(parametri({ q: 'E-1 2' })).toBe('q=E-1+2')
  })
})
