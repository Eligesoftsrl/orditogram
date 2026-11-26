// Il percorso principale, come lo fa una persona: l'analista pubblica un processo dallo Studio, l'operatore avvia
// una pratica, la ritrova sulla scrivania, lavora l'attività e la pratica si conclude.
import { test, expect } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const dati = path.join(path.dirname(fileURLToPath(import.meta.url)), 'dati')
const PROCESSO = 'Segnalazione di prova'
// L'applicazione usa indirizzi con il cancelletto: /#/pratiche/...

async function accedi(page, utente) {
  const password = utente === 'admin' ? 'admin' : 'password'
  await page.goto('/')
  await page.getByLabel('Nome utente').fill(utente)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Accedi' }).click()
  await expect(page.getByRole('button', { name: 'Esci' })).toBeVisible()
}

test('Studio → pubblicazione → scrivania → pratica conclusa', async ({ browser }) => {
  const oggetto = `Lampione spento in via Roma ${Date.now()}`

  // 1. L'amministratore pubblica il processo con i suoi form
  const analista = await (await browser.newContext()).newPage()
  await accedi(analista, 'admin')
  await analista.goto('/#/studio/processi')
  await analista.getByRole('button', { name: 'Carica BPMN' }).click()
  const finestra = analista.getByRole('dialog', { name: 'Carica processo' })
  await expect(finestra).toBeVisible()
  const scelte = finestra.locator('input[type=file]')
  await scelte.nth(0).setInputFiles(path.join(dati, 'e2e-segnalazione.bpmn20.xml'))
  await scelte.nth(1).setInputFiles([path.join(dati, 'e2e-segnalazione.json'), path.join(dati, 'e2e-esame.json')])
  await expect(finestra.getByText('e2e-segnalazione.json, e2e-esame.json')).toBeVisible()
  await finestra.getByRole('button', { name: 'Metti in produzione' }).click()
  await expect(analista.getByText(`${PROCESSO} in produzione`)).toBeVisible()
  await expect(analista.getByText(PROCESSO).first()).toBeVisible()

  // 2. L'operatore avvia una pratica dal form iniziale
  const operatore = await (await browser.newContext()).newPage()
  await accedi(operatore, 'mario')
  await operatore.getByRole('button', { name: 'Nuova pratica' }).first().click()
  const avvio = operatore.getByRole('dialog')
  await avvio.getByRole('button', { name: new RegExp(PROCESSO) }).click()
  await avvio.getByLabel('Oggetto').fill(oggetto)
  await avvio.getByLabel('Descrizione').fill('Segnalata da un passante alle 21.')
  await avvio.getByRole('button', { name: 'Invia la segnalazione' }).click()
  // il primo passo tocca a chi ha avviato: l'applicazione porta subito all'attività
  await expect(operatore.getByText(/avviata/).first()).toBeVisible()
  await expect(operatore).toHaveURL(/#\/attivita\//)

  // 3. La ritrova sulla scrivania, apre la pratica e da lì lavora l'attività
  await operatore.goto('/#/')
  const riga = operatore.getByText(oggetto).first()
  await expect(riga).toBeVisible()
  // l'elenco si aggiorna da solo dopo il caricamento: se il clic cade durante il ridisegno si ripete
  const cassetto = operatore.getByRole('dialog', { name: oggetto })
  await expect(async () => {
    await riga.click()
    await expect(cassetto).toBeVisible({ timeout: 2_000 })
  }).toPass({ timeout: 20_000 })
  await cassetto.getByRole('button', { name: 'Apri la pratica' }).click()
  await expect(operatore).toHaveURL(/#\/pratiche\/[^/]+$/)
  const urlPratica = operatore.url()
  await expect(operatore.getByRole('heading', { name: oggetto })).toBeVisible()
  await expect(operatore.getByText('In corso').first()).toBeVisible()
  await operatore.getByRole('link', { name: "Lavora l'attività" }).click()
  await expect(operatore).toHaveURL(/#\/attivita\//)
  await expect(operatore.getByText(oggetto).first()).toBeVisible()   // il campo in sola lettura mostra il dato iniziale
  await operatore.getByText('Accolta', { exact: true }).click()
  await operatore.getByLabel('Note').fill('Intervento programmato.')
  await operatore.getByRole('button', { name: 'Completa' }).click()
  await expect(operatore).toHaveURL(/#\/$/)

  // 4. La pratica è conclusa, con i dati inseriti
  await operatore.goto(urlPratica)
  await expect(operatore.getByText('Conclusa').first()).toBeVisible()
  await expect(operatore.getByText('Intervento programmato.').first()).toBeVisible()
})

test('chi non è autorizzato non entra nello Studio', async ({ page }) => {
  await accedi(page, 'mario')
  await expect(page.getByRole('link', { name: 'Processi' })).toHaveCount(0)
  await page.goto('/#/studio/processi')
  await expect(page).not.toHaveURL(/#\/studio\/processi/)
})

test('password sbagliata: messaggio chiaro, nessun accesso', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Nome utente').fill('mario')
  await page.getByLabel('Password').fill('sbagliata')
  await page.getByRole('button', { name: 'Accedi' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Esci' })).toHaveCount(0)
})
