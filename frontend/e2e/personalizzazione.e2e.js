// Un'installazione consegnata al cliente porta il suo nome, il suo logo e le sue parole: l'amministratore li
// imposta da Studio → Glossario e logo, e li vedono subito tutti, pagina di accesso compresa.
// Alla fine tutto torna come prima, perché gli altri scenari cercano "Nuova pratica".
import { test, expect } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const dati = path.join(path.dirname(fileURLToPath(import.meta.url)), 'dati')
const NOME = 'Portale incentivi di prova'

async function accedi(page, utente) {
  await page.goto('/')
  await page.getByLabel('Nome utente').fill(utente)
  await page.getByLabel('Password').fill(utente === 'admin' ? 'admin' : 'password')
  await page.getByRole('button', { name: 'Accedi' }).click()
  await expect(page.getByRole('button', { name: 'Esci' })).toBeVisible()
}

/** Rimette nome, logo e parole predefiniti con le API dell'amministratore. */
async function ripristina(request) {
  const h = { Authorization: 'Basic ' + Buffer.from('admin:admin').toString('base64'), 'X-Requested-With': 'XMLHttpRequest' }
  await request.put('/api/admin/personalizzazione/nome', { headers: h, data: { nome: '' } })
  await request.delete('/api/admin/personalizzazione/logo', { headers: h })
  await request.put('/api/admin/glossario', { headers: h, data: { pratica: {}, processo: {} } })
}

test('nome, logo e parole del cliente valgono per tutti', async ({ browser, request }) => {
  try {
    const admin = await (await browser.newContext()).newPage()
    await accedi(admin, 'admin')
    await admin.goto('/#/studio/glossario')
    await expect(admin.getByRole('heading', { name: 'Glossario e logo' })).toBeVisible()

    await admin.getByLabel("Nome dell'applicazione").fill(NOME)
    await admin.getByRole('button', { name: 'Salva il nome' }).click()
    await expect(admin.getByText('Nome salvato')).toBeVisible()
    await admin.locator('input[type=file]').setInputFiles(path.join(dati, 'logo-prova.png'))
    await expect(admin.getByText('Logo salvato: lo vedono subito tutti')).toBeVisible()

    // parole: "domanda" al posto di "pratica", "bando" al posto di "processo"
    const parola = async (termine, singolare, plurale, genere) => {
      const blocco = admin.locator('fieldset').filter({ hasText: termine })
      await blocco.getByLabel('Singolare').fill(singolare)
      await blocco.getByLabel('Plurale').fill(plurale)
      await blocco.getByLabel('Genere').selectOption(genere)
    }
    await parola('Il caso che si lavora', 'domanda', 'domande', 'F')
    await parola('Il modello da cui nasce', 'bando', 'bandi', 'M')
    await admin.getByRole('button', { name: 'Salva', exact: true }).click()
    await expect(admin.getByText('Glossario salvato')).toBeVisible()

    // pagina di accesso: nome e logo del cliente, titolo della finestra
    const ospite = await (await browser.newContext()).newPage()
    await ospite.goto('/')
    await expect(ospite.getByText(NOME).first()).toBeVisible()
    await expect(ospite.locator('img[src^="/api/info/logo"]').first()).toBeVisible()
    await expect(ospite).toHaveTitle(NOME)
    await ospite.screenshot({ path: 'test-results/accesso-personalizzato.png' })

    // l'operatore avvia una "domanda" scegliendo tra i "bandi"
    await accedi(ospite, 'mario')
    await ospite.getByRole('button', { name: 'Nuova domanda' }).first().click()
    await expect(ospite.getByRole('dialog').getByText('Scegli il tipo di domanda da avviare')).toBeVisible()
    await ospite.screenshot({ path: 'test-results/nuova-domanda.png' })
  } finally {
    await ripristina(request)
  }
})
