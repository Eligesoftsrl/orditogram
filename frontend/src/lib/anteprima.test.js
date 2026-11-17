import { describe, expect, it } from 'vitest'
import { genereAnteprima, siPuoVedere } from './anteprima.js'

describe('genereAnteprima', () => {
  it('riconosce PDF, immagini e testo dal tipo o dal nome', () => {
    expect(genereAnteprima('application/pdf')).toBe('pdf')
    expect(genereAnteprima('application/octet-stream', 'Provvedimento.PDF')).toBe('pdf')
    expect(genereAnteprima('image/png')).toBe('immagine')
    expect(genereAnteprima('', 'foto.jpeg')).toBe('immagine')
    expect(genereAnteprima('text/plain; charset=UTF-8')).toBe('testo')
  })
  it('non prova a mostrare i file che il browser non sa aprire', () => {
    expect(genereAnteprima('application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'lettera.docx')).toBeNull()
    expect(genereAnteprima('application/pkcs7-mime', 'atto.pdf.p7m')).toBeNull()
  })
})

describe('siPuoVedere', () => {
  it('guarda dentro le buste firmate', () => {
    expect(siPuoVedere('atto.pdf.p7m')).toBe(true)
    expect(siPuoVedere('lettera.docx')).toBe(false)
  })
})
