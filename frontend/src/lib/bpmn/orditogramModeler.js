import { is } from 'bpmn-js/lib/util/ModelUtil'

/**
 * Personalizzazioni del modeler bpmn-js per Orditogram:
 *  - palette e menu ridotti agli elementi che Orditogram sa eseguire
 *  - attività utente / automatica e parallelo creabili direttamente
 *  - testi in italiano
 */

// ------------------------------------------------------------------ palette

class OrditogramPalette {
  constructor(palette, create, elementFactory) {
    this.create = create
    this.elementFactory = elementFactory
    palette.registerProvider(500, this)
  }

  voce(type, className, title, group, extra = {}) {
    const action = (event) => this.create.start(event, this.elementFactory.createShape({ type, ...extra }))
    return { group, className, title, action: { dragstart: action, click: action } }
  }

  getPaletteEntries() {
    return (entries) => {
      const out = {}
      ;['hand-tool', 'lasso-tool', 'space-tool', 'global-connect-tool', 'tool-separator',
        'create.start-event', 'create.end-event', 'create.exclusive-gateway'].forEach(k => { if (entries[k]) out[k] = entries[k] })
      if (out['create.exclusive-gateway']) out['create.exclusive-gateway'] = { ...out['create.exclusive-gateway'], title: 'Decisione' }
      out['create.parallel-gateway'] = this.voce('bpmn:ParallelGateway', 'bpmn-icon-gateway-parallel', 'Attività in parallelo', 'gateway')
      out['create.user-task'] = this.voce('bpmn:UserTask', 'bpmn-icon-user-task', 'Attività di una persona', 'activity')
      out['create.service-task'] = this.voce('bpmn:ServiceTask', 'bpmn-icon-service-task', 'Attività automatica', 'activity')
      out['create.call-activity'] = this.voce('bpmn:CallActivity', 'bpmn-icon-call-activity',
        'Processo richiamato: apre una pratica di un altro processo pubblicato e aspetta che finisca', 'activity')
      if (entries['create.subprocess-expanded']) out['create.subprocess-expanded'] = { ...entries['create.subprocess-expanded'], title: 'Sottoprocesso: un gruppo di passi con scadenze ed errori comuni' }
      out['create.attesa'] = this.voce('bpmn:IntermediateCatchEvent', 'bpmn-icon-intermediate-event-catch-timer',
        'Attesa: la pratica aspetta un tempo o una data (trascinala sul bordo di un\'attività per farne una scadenza)', 'event',
        { eventDefinitionType: 'bpmn:TimerEventDefinition' })
      out['create.messaggio'] = this.voce('bpmn:IntermediateCatchEvent', 'bpmn-icon-intermediate-event-catch-message',
        'Messaggio in arrivo: la pratica aspetta un segnale da un sistema esterno o da un operatore (sul bordo di un\'attività la fa deviare)', 'event',
        { eventDefinitionType: 'bpmn:MessageEventDefinition' })
      return out
    }
  }
}
OrditogramPalette.$inject = ['palette', 'create', 'elementFactory']

// ------------------------------------------------------------------ menu contestuale

class OrditogramContextPad {
  constructor(contextPad, autoPlace, create, elementFactory) {
    this.autoPlace = autoPlace
    this.create = create
    this.elementFactory = elementFactory
    contextPad.registerProvider(500, this)
  }

  aggiungi(type, className, title, extra = {}) {
    const click = (event, element) => this.autoPlace.append(element, this.elementFactory.createShape({ type, ...extra }))
    const dragstart = (event, element) => this.create.start(event, this.elementFactory.createShape({ type, ...extra }), { source: element })
    return { group: 'model', className, title, action: { click, dragstart } }
  }

  getContextPadEntries(element) {
    return (entries) => {
      const out = {}
      ;['append.end-event', 'append.gateway', 'replace', 'delete', 'connect', 'append.text-annotation']
        .forEach(k => { if (entries[k]) out[k] = entries[k] })
      if (entries['append.append-task']) {
        out['append.user-task'] = this.aggiungi('bpmn:UserTask', 'bpmn-icon-user-task', 'Aggiungi attività di una persona')
        out['append.service-task'] = this.aggiungi('bpmn:ServiceTask', 'bpmn-icon-service-task', 'Aggiungi attività automatica')
        out['append.call-activity'] = this.aggiungi('bpmn:CallActivity', 'bpmn-icon-call-activity', 'Aggiungi processo richiamato')
        out['append.attesa'] = this.aggiungi('bpmn:IntermediateCatchEvent', 'bpmn-icon-intermediate-event-catch-timer',
          'Aggiungi attesa', { eventDefinitionType: 'bpmn:TimerEventDefinition' })
        out['append.messaggio'] = this.aggiungi('bpmn:IntermediateCatchEvent', 'bpmn-icon-intermediate-event-catch-message',
          'Aggiungi messaggio in arrivo', { eventDefinitionType: 'bpmn:MessageEventDefinition' })
      }
      return out
    }
  }
}
OrditogramContextPad.$inject = ['contextPad', 'autoPlace', 'create', 'elementFactory']

// ------------------------------------------------------------------ menu "cambia tipo"

const SOSTITUZIONI = new Set([
  'replace-with-user-task', 'replace-with-service-task',
  'replace-with-exclusive-gateway', 'replace-with-parallel-gateway',
  'replace-with-none-start', 'replace-with-none-end',
  'replace-with-expanded-subprocess', 'replace-with-call-activity',
  'replace-with-timer-intermediate-catch',
  'replace-with-timer-boundary', 'replace-with-non-interrupting-timer-boundary', 'replace-with-error-boundary',
  'replace-with-message-intermediate-catch', 'replace-with-message-boundary', 'replace-with-non-interrupting-message-boundary'
])

class OrditogramReplaceMenu {
  constructor(popupMenu) {
    popupMenu.registerProvider('bpmn-replace', 500, this)
  }
  getPopupMenuEntries() {
    return (entries) => Object.fromEntries(Object.entries(entries).filter(([k]) => SOSTITUZIONI.has(k)))
  }
  getPopupMenuHeaderEntries() {
    return () => ({})
  }
}
OrditogramReplaceMenu.$inject = ['popupMenu']

export const orditogramModule = {
  __init__: ['orditogramPalette', 'orditogramContextPad', 'orditogramReplaceMenu'],
  orditogramPalette: ['type', OrditogramPalette],
  orditogramContextPad: ['type', OrditogramContextPad],
  orditogramReplaceMenu: ['type', OrditogramReplaceMenu]
}

// ------------------------------------------------------------------ italiano

const IT = {
  'Activate hand tool': 'Sposta la tela',
  'Activate lasso tool': 'Seleziona più elementi',
  'Activate create/remove space tool': 'Crea o togli spazio',
  'Activate global connect tool': 'Collega due elementi',
  'Create start event': 'Inizio',
  'Create end event': 'Fine',
  'Create gateway': 'Decisione',
  'Append end event': 'Aggiungi fine',
  'Append gateway': 'Aggiungi decisione',
  'Change element': 'Cambia tipo',
  'Connect to other element': 'Collega a un altro elemento',
  'Delete': 'Elimina',
  'Add text annotation': 'Aggiungi una nota',
  'Connect using association': 'Collega la nota',
  'User task': 'Attività di una persona',
  'Service task': 'Attività automatica',
  'Exclusive gateway': 'Decisione',
  'Parallel gateway': 'Attività in parallelo',
  'Start event': 'Inizio',
  'End event': 'Fine',
  'Search in diagram': 'Cerca nel diagramma',
  'Create expanded sub-process': 'Sottoprocesso',
  'Sub-process (expanded)': 'Sottoprocesso',
  'Sub-process': 'Sottoprocesso',
  'Call activity': 'Processo richiamato',
  'Timer intermediate catch event': 'Attesa',
  'Timer boundary event': 'Scadenza (chiude l\'attività)',
  'Timer boundary event (non-interrupting)': 'Sollecito (l\'attività resta aperta)',
  'Error boundary event': 'Se non riesce (errore)',
  'Append timer intermediate catch event': 'Aggiungi attesa',
  'Message intermediate catch event': 'Messaggio in arrivo',
  'Message boundary event': 'Messaggio sul bordo (chiude l\'attività)',
  'Message boundary event (non-interrupting)': 'Messaggio sul bordo (l\'attività resta aperta)'
}

function traduci(template, replacements = {}) {
  const t = IT[template] || template
  return t.replace(/\{(\w+)\}/g, (_, key) => replacements[key] ?? `{${key}}`)
}
export const italianoModule = { translate: ['value', traduci] }

// ------------------------------------------------------------------ utilità

export const ELEMENTI_CON_FORM = (el) => is(el, 'bpmn:StartEvent') || is(el, 'bpmn:UserTask')

/** XML di partenza per un nuovo processo: inizio → attività → fine. */
export function xmlIniziale(chiave, nome) {
  const esc = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;')
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:activiti="http://activiti.org/bpmn"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
             id="Definitions_${chiave}" targetNamespace="http://orditogram.it/processi">
  <process id="${chiave}" name="${esc(nome)}" isExecutable="true">
    <startEvent id="inizio" name="Inizio"/>
    <sequenceFlow id="f1" sourceRef="inizio" targetRef="attivita1"/>
    <userTask id="attivita1" name="Prima attività"/>
    <sequenceFlow id="f2" sourceRef="attivita1" targetRef="fine"/>
    <endEvent id="fine" name="Fine"/>
  </process>
  <bpmndi:BPMNDiagram id="Diagram_${chiave}">
    <bpmndi:BPMNPlane id="Plane_${chiave}" bpmnElement="${chiave}">
      <bpmndi:BPMNShape id="s_inizio" bpmnElement="inizio"><dc:Bounds x="160" y="182" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="s_attivita1" bpmnElement="attivita1"><dc:Bounds x="260" y="160" width="120" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="s_fine" bpmnElement="fine"><dc:Bounds x="452" y="182" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="e_f1" bpmnElement="f1"><di:waypoint x="196" y="200"/><di:waypoint x="260" y="200"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="e_f2" bpmnElement="f2"><di:waypoint x="380" y="200"/><di:waypoint x="452" y="200"/></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

/** "Richiesta ferie" → "richiestaFerie" (identificativo valido per il BPMN). */
export function chiaveDaNome(s) {
  const parole = s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-zA-Z0-9 ]/g, ' ').trim().split(/\s+/).filter(Boolean)
  const k = parole.map((p, i) => i === 0 ? p.toLowerCase() : p[0].toUpperCase() + p.slice(1).toLowerCase()).join('')
  return /^[a-zA-Z_]/.test(k) ? k : k ? 'p' + k : ''
}
