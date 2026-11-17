/** Una durata in parole: "meno di un minuto", "12 minuti", "5 ore", "3 giorni". */
export function durata(ms) {
  if (ms == null || Number.isNaN(ms) || ms < 0) return ''
  const min = Math.round(ms / 60000)
  if (min < 1) return 'meno di un minuto'
  if (min < 60) return min === 1 ? '1 minuto' : `${min} minuti`
  const ore = Math.round(min / 60)
  if (ore < 24) return ore === 1 ? '1 ora' : `${ore} ore`
  const giorni = Math.round(ore / 24)
  return giorni === 1 ? '1 giorno' : `${giorni} giorni`
}

/**
 * Quanto è durato ogni passo del percorso (fermate con il momento in cui sono state completate).
 * Un passo fatto dura dalla fermata precedente alla sua; quello corrente dura da "dal" (o dall'ultima fermata) a ora.
 * L'avvio e la conclusione non hanno durata.
 */
export function tempiDelPercorso(percorso = [], ora = Date.now(), dal = null) {
  const out = []
  let prima = null
  for (const f of percorso) {
    const t = f.quando ? new Date(f.quando).getTime() : null
    if (f.stato === 'corrente') {
      const inizio = dal ? new Date(dal).getTime() : prima
      out.push({ nome: f.nome, durata: inizio != null ? durata(ora - inizio) : '', inCorso: true })
    } else if (f.stato === 'fatto' && f.nome !== 'Avvio' && prima != null && t != null) {
      out.push({ nome: f.nome, durata: durata(t - prima), inCorso: false })
    }
    if (t != null) prima = t
  }
  return out
}

/** Dove si trova la pratica: indice della prima fermata corrente, oppure l'ultima (conclusa). */
export function posizioneAttuale(percorso = []) {
  const i = percorso.findIndex(f => f.stato === 'corrente')
  return i >= 0 ? i : Math.max(0, percorso.length - 1)
}
