// Una pratica con un'attività che chiede una data e un allegato: l'operatore carica il documento dall'attività,
// cambia il titolo, e la scheda della pratica si apre con la data leggibile, il titolo nuovo e l'allegato.
// Protegge da due errori visti in una demo: date salvate come "Mon Feb 15 01:00:00 CET 2010" (pagina bianca) e
// "documenti": "carica" nelle attività (nessun posto dove allegare).
import { test, expect } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const dati = path.join(path.dirname(fileURLToPath(import.meta.url)), 'dati')
const PROCESSO = 'Domanda con allegati di prova'

async function accedi(page, utente) {
  const password = utente === 'admin' ? 'admin' : 'password'
  await page.goto('/')
  await page.getByLabel('Nome utente').fill(utente)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Accedi' }).click()
  await expect(page.getByRole('button', { name: 'Esci' })).toBeVisible()
}

/** I documenti possono stare in una colonna o dietro un pulsante che apre il pannello: si apre se serve. */
async function documenti(page, testo) {
  const voce = page.getByText(testo).first()
  if (!(await voce.isVisible())) {
    const pulsante = page.getByRole('button', { name: /^Documenti/ }).first()
    if (await pulsante.count()) await pulsante.click()
  }
  return voce
}

test('attività con data e allegato → scheda della pratica leggibile', async ({ browser }) => {
  const oggetto = `Domanda di contributo ${Date.now()}`
  const rivisto = `${oggetto} (rivista)`

  const analista = await (await browser.newContext()).newPage()
  await accedi(analista, 'admin')
  await analista.goto('/#/studio/processi')
  await analista.getByRole('button', { name: 'Carica BPMN' }).click()
  const finestra = analista.getByRole('dialog', { name: 'Carica processo' })
  const scelte = finestra.locator('input[type=file]')
  await scelte.nth(0).setInputFiles(path.join(dati, 'e2e-documenti.bpmn20.xml'))
  await scelte.nth(1).setInputFiles(['e2e-doc-avvio.json', 'e2e-doc-dati.json', 'e2e-doc-verifica.json'].map(f => path.join(dati, f)))
  await finestra.getByRole('button', { name: 'Metti in produzione' }).click()
  await expect(analista.getByText(`${PROCESSO} in produzione`)).toBeVisible()

  // avvio: il primo passo tocca a chi ha avviato e l'applicazione porta all'attività
  const operatore = await (await browser.newContext()).newPage()
  await accedi(operatore, 'mario')
  await operatore.getByRole('button', { name: 'Nuova pratica' }).first().click()
  const avvio = operatore.getByRole('dialog')
  await avvio.getByRole('button', { name: new RegExp(PROCESSO) }).click()
  await avvio.getByLabel('Oggetto').fill(oggetto)
  await avvio.getByRole('button', { name: 'Crea la domanda' }).click()
  await expect(operatore).toHaveURL(/#\/attivita\//)

  // l'attività: si prende in carico, si allega un documento, si scrivono data e titolo nuovo
  const riserva = operatore.getByRole('button', { name: 'Riservala a me' })
  if (await riserva.isVisible()) await riserva.click()
  await documenti(operatore, 'Aggiungi un documento')
  await operatore.getByRole('button', { name: 'Aggiungi un documento' }).click()
  await operatore.locator('input[type=file]').last().setInputFiles(path.join(dati, 'allegato-prova.txt'))
  await operatore.getByRole('button', { name: 'Carica', exact: true }).click()
  await expect(operatore.getByText('allegato-prova.txt').first()).toBeVisible()
  await operatore.getByLabel('Data di costituzione').fill('2010-03-01')
  await operatore.getByLabel('Oggetto').fill(rivisto)
  await operatore.getByRole('button', { name: 'Conferma' }).click()
  // il passo dopo tocca allo stesso gruppo: l'applicazione può portare subito lì
  await expect(operatore.getByText('Verifica la domanda').first()).toBeVisible()
  await operatore.goto('/#/')

  // la scrivania mostra il titolo nuovo; la scheda della pratica si apre con data e allegato
  const riga = operatore.getByText(rivisto).first()
  await expect(riga).toBeVisible()
  const cassetto = operatore.getByRole('dialog', { name: rivisto })
  await expect(async () => {
    await riga.click()
    await expect(cassetto).toBeVisible({ timeout: 2_000 })
  }).toPass({ timeout: 20_000 })
  await cassetto.getByRole('button', { name: 'Apri la pratica' }).click()
  await expect(operatore).toHaveURL(/#\/pratiche\/[^/]+$/)
  await expect(operatore.getByRole('heading', { name: rivisto })).toBeVisible()
  await expect(operatore.getByText('1 marzo 2010').first()).toBeVisible()
  await expect(await documenti(operatore, 'allegato-prova.txt')).toBeVisible()
})
