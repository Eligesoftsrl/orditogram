// Test end-to-end nel browser: avviano backend (H2 in memoria, utenti di prova) e frontend, poi percorrono
// l'applicazione come una persona. `npm run e2e`; in CI il job "e2e" di .github/workflows/ci.yml.
import { defineConfig, devices } from '@playwright/test'

const backend = process.env.E2E_BACKEND ||
  'mvn -B -q spring-boot:run -Dspring-boot.run.useTestClasspath=true ' +
  '-Dspring-boot.run.arguments=--spring.config.additional-location=file:../frontend/e2e/backend.yml'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.js',
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    locale: 'it-IT',
    timezoneId: 'Europe/Rome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: process.env.E2E_CHROMIUM ? { executablePath: process.env.E2E_CHROMIUM } : {}
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: backend,
      cwd: '../backend',
      url: 'http://localhost:8080/actuator/health',
      timeout: 300_000,
      reuseExistingServer: !process.env.CI,
      stdout: 'ignore',
      stderr: 'pipe'
    },
    {
      command: 'npx vite --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 60_000,
      reuseExistingServer: !process.env.CI
    }
  ]
})
