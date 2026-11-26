import TokenSimulationModule from 'bpmn-js-token-simulation'
import 'bpmn-js-token-simulation/assets/css/bpmn-js-token-simulation.css'
import { gl } from '../glossario.js'

/**
 * Simulazione con i gettoni (bpmn-js-token-simulation, licenza MIT): l'analista vede scorrere la pratica
 * sul diagramma prima di pubblicarlo. I comandi sono quelli di Orditogram (barra e pannello in italiano);
 * dell'estensione restano i gettoni, i contatori e le icone sugli elementi, i cui suggerimenti traduciamo qui.
 */
export const simulazioneModule = TokenSimulationModule

export const EVENTI = {
  modo: 'tokenSimulation.toggleMode',
  play: 'tokenSimulation.playSimulation',
  pausa: 'tokenSimulation.pauseSimulation',
  reset: 'tokenSimulation.resetSimulation',
  traccia: 'tokenSimulation.simulator.trace',
  nuovaScope: 'tokenSimulation.simulator.createScope',
  velocita: 'tokenSimulation.animationSpeedChanged'
}

const TITOLI = {
  'Trigger Event': 'Fai scattare / avvia',
  'Set Sequence Flow': 'Scegli la strada successiva',
  'Add pause point': 'Fermati qui',
  'Remove pause point': 'Non fermarti qui',
  'Play/Pause Simulation': 'Avvia / metti in pausa',
  'Reset Simulation': 'Ricomincia',
  'Toggle Simulation Log': 'Registro'
}

const TESTI = [
  [/^User Task$/, 'Attività di una persona'],
  [/^Service Task$/, 'Attività automatica'],
  [/^Exclusive Gateway$/, 'Decisione'],
  [/^Parallel Gateway$/, 'Attività in parallelo'],
  [/^Start Event$/, 'Inizio'],
  [/^End Event$/, 'Fine'],
  [/^Intermediate Event$/, 'Attesa'],
  [/^Boundary Event$/, 'Evento sul bordo'],
  [/^SubProcess$/, 'Sottoprocesso'],
  [/^Call Activity$/, 'Processo richiamato'],
  [/^Finished$/, 'Conclusa'],
  [/^Not supported$/, 'Non supportato']
]

/** Traduce i suggerimenti e le etichette dell'estensione mentre compaiono sulla tela. */
export function traduciSimulazione(container) {
  const traduci = (root) => {
    root.querySelectorAll?.('[title]').forEach(el => {
      const t = el.getAttribute('title')
      if (TITOLI[t]) el.setAttribute('title', TITOLI[t])
      else if (/^View Process Instance/.test(t)) el.setAttribute('title', `Mostra solo quest${gl.agg('pratica', '')} ${gl('pratica', 'nome')} di prova`)
      else if (/^Set animation speed/.test(t)) el.setAttribute('title', 'Velocità')
    })
    root.querySelectorAll?.('.bts-element-notification .bts-text, .bts-notification .bts-text').forEach(el => {
      for (const [re, it] of TESTI) if (re.test(el.textContent)) { el.textContent = el.textContent.replace(re, it); break }
    })
  }
  const obs = new MutationObserver(muts => muts.forEach(m => m.addedNodes.forEach(n => n.nodeType === 1 && traduci(n.parentNode || n))))
  obs.observe(container, { childList: true, subtree: true })
  traduci(container)
  return () => obs.disconnect()
}

/** Pratiche di prova avviabili: gli eventi di inizio del processo (fuori dai sottoprocessi). */
export function avviaPraticaDiProva(modeler) {
  const simulator = modeler.get('simulator')
  const root = modeler.get('canvas').getRootElement()
  const inizi = modeler.get('elementRegistry').filter(e => e.type === 'bpmn:StartEvent' && e.parent === root)
  for (const inizio of inizi) {
    const sub = simulator.findSubscriptions({ element: inizio })[0]
    if (sub) { simulator.trigger({ event: sub.event, scope: sub.scope }); return true }
  }
  return false
}
