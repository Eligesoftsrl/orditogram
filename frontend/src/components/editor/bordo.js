import { is } from 'bpmn-js/lib/util/ModelUtil'
import { tipoBordo } from './timer.js'

/**
 * Crea sul bordo di un'attività (o di un sottoprocesso) una scadenza, un sollecito o la strada
 * "se non riesce", già collegati a un passo successivo, così il diagramma resta valido.
 *
 *  scadenza  → timer interrompente   → fine "Termine scaduto"
 *  sollecito → timer non interrompente (ripetuto) → notifica a chi deve lavorare l'attività → fine
 *  errore    → evento di errore      → fine "Non riuscita"
 */
export function aggiungiBordo(modeler, host, tipo) {
  const modeling = modeler.get('modeling')
  const ef = modeler.get('elementFactory')
  const moddle = modeler.get('moddle')
  const parent = host.parent
  const n = (host.attachers || []).length

  const attrs = tipo === 'errore'
    ? { type: 'bpmn:BoundaryEvent', eventDefinitionType: 'bpmn:ErrorEventDefinition' }
    : tipo === 'messaggio'
      ? { type: 'bpmn:BoundaryEvent', eventDefinitionType: 'bpmn:MessageEventDefinition', cancelActivity: true }
      : { type: 'bpmn:BoundaryEvent', eventDefinitionType: 'bpmn:TimerEventDefinition', cancelActivity: tipo === 'scadenza' }
  // posti sul bordo inferiore: a destra, a sinistra, al centro (le etichette non si sovrappongono)
  const posti = [host.width - 22, 22, host.width / 2]
  const posizione = { x: host.x + posti[n % 3], y: host.y + host.height }
  const bordo = modeling.createShape(ef.createShape(attrs), posizione, host, { attach: true })

  const def = bordo.businessObject.eventDefinitions[0]
  if (tipo === 'scadenza') {
    modeling.updateModdleProperties(bordo, def, { timeDuration: moddle.create('bpmn:FormalExpression', { body: 'P30D' }) })
  } else if (tipo === 'sollecito') {
    modeling.updateModdleProperties(bordo, def, { timeCycle: moddle.create('bpmn:FormalExpression', { body: 'R3/P5D' }) })
  }
  modeling.updateProperties(bordo, { name: { scadenza: 'Termine', sollecito: 'Sollecito', errore: 'Se non riesce', messaggio: 'Se arriva un messaggio' }[tipo] })

  const sotto = host.y + host.height + 90 + n * 100
  const x = posizione.x
  if (tipo === 'sollecito') {
    const nomeHost = host.businessObject.name || 'attività'
    const gruppo = (host.businessObject.candidateGroups || '').split(',').map(s => s.trim()).filter(Boolean)[0]
    const campi = [
      ['destinatario', gruppo ? `gruppo:${gruppo}` : 'iniziatore'],
      ['titolo', `Sollecito: ${nomeHost}`],
      ['messaggio', `L'attività "${nomeHost}" è ancora da completare. Ti chiediamo di lavorarla al più presto.`]
    ].map(([name, stringValue]) => moddle.create('activiti:Field', { name, stringValue }))
    const notifica = modeling.appendShape(bordo, ef.createShape({ type: 'bpmn:ServiceTask' }), { x, y: sotto + 40 }, parent)
    modeling.updateProperties(notifica, {
      name: 'Invia sollecito',
      implementation: 'notificaConnector',
      extensionElements: moddle.create('bpmn:ExtensionElements', { values: campi })
    })
    const fine = modeling.appendShape(notifica, ef.createShape({ type: 'bpmn:EndEvent' }), { x: x + 170, y: sotto + 40 }, parent)
    modeling.updateProperties(fine, { name: 'Sollecito inviato' })
  } else {
    const fine = modeling.appendShape(bordo, ef.createShape({ type: 'bpmn:EndEvent' }), { x, y: sotto }, parent)
    modeling.updateProperties(fine, { name: { errore: 'Non riuscita', messaggio: 'Chiusa su richiesta' }[tipo] || 'Termine scaduto' })
  }
  return bordo
}

/** Eventi di bordo di un elemento, con il loro tipo. */
export function bordiDi(elemento) {
  return (elemento.attachers || []).map(a => ({ shape: a, tipo: tipoBordo(a.businessObject) }))
}

/** Attività su cui ha senso una strada di errore: automatiche e sottoprocessi. */
export const puoFallire = (el) => is(el, 'bpmn:ServiceTask') || is(el, 'bpmn:SubProcess')
