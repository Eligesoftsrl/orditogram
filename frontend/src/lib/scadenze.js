/** Attività scadute e in scadenza entro due giorni (solo le scadenze vere, non solleciti e attese). */
export function contaScadenze(tasks, ora = Date.now()) {
  let scadute = 0, vicine = 0
  for (const t of tasks) {
    const s = (t.scadenze || []).find(x => x.tipo === 'scadenza')
    if (!s) continue
    const manca = new Date(s.quando).getTime() - ora
    if (manca <= 0) scadute++
    else if (manca < 2 * 86400e3) vicine++
  }
  return { scadute, vicine }
}
