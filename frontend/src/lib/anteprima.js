/** Come mostrare un file nell'anteprima laterale, dal tipo o dal nome. */
export function genereAnteprima(tipo = '', nome = '') {
  const t = tipo.toLowerCase().split(';')[0].trim()
  const n = nome.toLowerCase()
  if (t === 'application/pdf' || n.endsWith('.pdf')) return 'pdf'
  if (/^image\/(png|jpe?g|gif|webp|svg\+xml)$/.test(t) || /\.(png|jpe?g|gif|webp)$/.test(n)) return 'immagine'
  if (t.startsWith('text/') || /\.(txt|csv|xml|json)$/.test(n)) return 'testo'
  return null
}

/** Si può vedere senza scaricare? Le buste .p7m si aprono sul contenuto estratto. */
export function siPuoVedere(nome = '') {
  return genereAnteprima('', nome.replace(/\.p7m$/i, '')) !== null
}
