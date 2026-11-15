import { api } from '../api.js'

/**
 * Lavoro senza interruzioni: dopo aver avviato una pratica o completato un'attività, se il passo successivo
 * della stessa pratica tocca all'utente (in carico a lui o libero per il suo gruppo) ci si va subito,
 * senza ripassare dalla scrivania. Restituisce l'attività o null.
 */
export async function prossimaAttivita(processInstanceId) {
  try {
    const tasks = await api.tasks()
    const dellaPratica = tasks.filter(t => t.processInstanceId === processInstanceId && (t.mine || !t.assignee))
    return dellaPratica.find(t => t.mine) || dellaPratica[0] || null
  } catch {
    return null
  }
}
