package com.eligesoft.orditogram.process;

import com.eligesoft.orditogram.connettore.ConnettoreOrditogram;
import com.eligesoft.orditogram.form.FormService;
import com.fasterxml.jackson.databind.JsonNode;
import org.activiti.bpmn.model.*;
import org.activiti.bpmn.model.Process;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.ProcessDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controlla che un processo sia eseguibile da Orditogram, con messaggi comprensibili per l'analista.
 * Errori: il processo non si può pubblicare. Avvisi: si può pubblicare, ma conviene controllare.
 * Ogni problema indica l'elemento del diagramma a cui si riferisce (per selezionarlo nell'editor).
 */
@Service
public class ProcessValidator {

    /** Dati scritti dalla piattaforma su ogni pratica. */
    public static final List<String> DATI_DI_SISTEMA = List.of("avviataDa", "avviataDaUtente", "chiusaDa", "chiusaIl");

    private static final Set<Class<?>> SUPPORTATI = Set.of(
        StartEvent.class, EndEvent.class, UserTask.class, ServiceTask.class,
        ExclusiveGateway.class, ParallelGateway.class, SequenceFlow.class,
        SubProcess.class, BoundaryEvent.class, IntermediateCatchEvent.class, CallActivity.class);

    private static final Pattern ESPRESSIONE = Pattern.compile("\\$\\{(.*?)}", Pattern.DOTALL);
    private static final Pattern IDENTIFICATORE = Pattern.compile("[a-zA-Z_][\\w]*");
    private static final Set<String> PAROLE_EL = Set.of("and", "or", "not", "eq", "ne", "lt", "gt", "le", "ge",
        "empty", "true", "false", "null", "div", "mod", "instanceof", "execution", "task");

    public record Problema(String messaggio, String elementId) {}

    public record Analisi(String chiave, String nome, List<String> formKeys, Set<String> gruppi,
                          List<String> gruppiAvvio, List<Problema> errori, List<Problema> avvisi) {
        public List<String> erroriTesto() { return errori.stream().map(Problema::messaggio).toList(); }
        public List<String> avvisiTesto() { return avvisi.stream().map(Problema::messaggio).toList(); }
    }

    private final FormService forms;
    private final ApplicationContext ctx;
    private final RepositoryService repositoryService;

    public ProcessValidator(FormService forms, ApplicationContext ctx, RepositoryService repositoryService) {
        this.forms = forms;
        this.ctx = ctx;
        this.repositoryService = repositoryService;
    }

    /** formAggiuntivi: form non ancora salvati (es. caricati insieme al BPMN), per nome. */
    public Analisi analizza(Process p, Map<String, JsonNode> formAggiuntivi) {
        List<Problema> errori = new ArrayList<>();
        List<Problema> avvisi = new ArrayList<>();
        List<String> formKeys = new ArrayList<>();
        Set<String> gruppi = new TreeSet<>();

        // --- processo
        if (p.getId() == null || !p.getId().matches("[A-Za-z_][\\w.-]*"))
            errori.add(new Problema("L'identificativo del processo deve iniziare con una lettera e contenere solo lettere, numeri, punto, - e _", null));
        if (p.getName() == null || p.getName().isBlank())
            avvisi.add(new Problema("Il processo non ha un nome: gli utenti vedranno l'identificativo tecnico", null));

        // --- elementi supportati (anche dentro i sottoprocessi)
        List<FlowElement> tutti = tutti(p);
        for (FlowElement el : tutti) {
            if (!SUPPORTATI.contains(el.getClass())) {
                errori.add(new Problema(nome(el) + ": questo tipo di elemento (" + el.getClass().getSimpleName()
                    + ") non è ancora supportato da Orditogram", el.getId()));
                continue;
            }
            if (el instanceof StartEvent || el instanceof EndEvent) {
                if (!((Event) el).getEventDefinitions().isEmpty())
                    errori.add(new Problema(nome(el) + ": gli inizi e le fini con timer, messaggi o segnali non sono ancora supportati", el.getId()));
            } else if (el instanceof IntermediateCatchEvent ev) {
                if (!(unica(ev) instanceof TimerEventDefinition) && !(unica(ev) instanceof MessageEventDefinition))
                    errori.add(new Problema(nome(el) + ": nel flusso sono supportate le attese a tempo e i messaggi in arrivo", el.getId()));
            } else if (el instanceof BoundaryEvent b) {
                EventDefinition def = unica(b);
                if (!(def instanceof TimerEventDefinition) && !(def instanceof ErrorEventDefinition) && !(def instanceof MessageEventDefinition))
                    errori.add(new Problema(nome(el) + ": sul bordo delle attività sono supportati scadenze, solleciti, errori e messaggi", el.getId()));
                if (def instanceof ErrorEventDefinition && !(b.getAttachedToRef() instanceof ServiceTask)
                        && !(b.getAttachedToRef() instanceof SubProcess) && !(b.getAttachedToRef() instanceof CallActivity))
                    avvisi.add(new Problema(nome(el) + ": gli errori arrivano solo dalle attività automatiche o dai sottoprocessi", el.getId()));
            }
            // messaggio: deve avere un nome, che è anche l'indirizzo dell'API (/api/messaggi/{nome})
            if (el instanceof Event ev && unica(ev) instanceof MessageEventDefinition m) {
                String ref = m.getMessageRef();
                if (!notBlank(ref))
                    errori.add(new Problema(nome(el) + ": dai un nome al messaggio atteso", el.getId()));
                else if (!ref.matches("[a-zA-Z][\\w-]*"))
                    errori.add(new Problema(nome(el) + ": il nome del messaggio deve iniziare con una lettera e contenere solo lettere, numeri, - e _", el.getId()));
            }
            // timer: quando scatta
            if (el instanceof Event ev && unica(ev) instanceof TimerEventDefinition t) {
                boolean durata = notBlank(t.getTimeDuration()), data = notBlank(t.getTimeDate()), ciclo = notBlank(t.getTimeCycle());
                if (!durata && !data && !ciclo)
                    errori.add(new Problema(nome(el) + ": imposta dopo quanto tempo o a quale data deve scattare", el.getId()));
                String cal = t.getCalendarName();
                if (notBlank(cal)) {
                    if (!Set.of("lavorativi", "proroga").contains(cal.trim()))
                        errori.add(new Problema(nome(el) + ": il calendario \"" + cal + "\" non esiste (usa giorni lavorativi o proroga se festivo)", el.getId()));
                    else if (ciclo)
                        errori.add(new Problema(nome(el) + ": i giorni lavorativi non si usano con le ripetizioni: scegli \"dopo\" o \"alla data\"", el.getId()));
                }
                if (ciclo && el instanceof BoundaryEvent b && b.isCancelActivity())
                    avvisi.add(new Problema(nome(el) + ": una scadenza che interrompe l'attività scatta una volta sola, la ripetizione non serve", el.getId()));
            }
        }

        // --- inizio e fine
        List<StartEvent> inizi = p.findFlowElementsOfType(StartEvent.class, false);
        if (inizi.isEmpty()) errori.add(new Problema("Manca l'evento di inizio", null));
        if (inizi.size() > 1) inizi.forEach(s -> errori.add(new Problema("C'è più di un evento di inizio: ne serve uno solo", s.getId())));
        if (p.findFlowElementsOfType(EndEvent.class, false).isEmpty())
            errori.add(new Problema("Manca l'evento di fine: la pratica non potrebbe concludersi", null));
        for (SubProcess sp : p.findFlowElementsOfType(SubProcess.class, true)) {
            long ini = sp.getFlowElements().stream().filter(f -> f instanceof StartEvent).count();
            long fin = sp.getFlowElements().stream().filter(f -> f instanceof EndEvent).count();
            if (ini != 1) errori.add(new Problema(nome(sp) + ": dentro il sottoprocesso serve esattamente un inizio", sp.getId()));
            if (fin == 0) errori.add(new Problema(nome(sp) + ": dentro il sottoprocesso serve almeno una fine", sp.getId()));
        }

        // --- collegamenti
        for (FlowElement el : tutti) {
            if (!(el instanceof FlowNode n)) continue;
            boolean entra = !n.getIncomingFlows().isEmpty();
            boolean esce = !n.getOutgoingFlows().isEmpty();
            if (n instanceof BoundaryEvent) { if (!esce) errori.add(new Problema(nome(n) + " non ha frecce in uscita: collega il passo da fare quando scatta", n.getId())); }
            else if (n instanceof StartEvent) { if (!esce) errori.add(new Problema(nome(n) + " non è collegato a nessun passo", n.getId())); }
            else if (n instanceof EndEvent) { if (!entra) errori.add(new Problema(nome(n) + " non è raggiungibile: nessuna freccia arriva qui", n.getId())); }
            else {
                if (!entra) errori.add(new Problema(nome(n) + " non è raggiungibile: nessuna freccia arriva qui", n.getId()));
                if (!esce) errori.add(new Problema(nome(n) + " non ha frecce in uscita: la pratica si fermerebbe qui", n.getId()));
            }
        }

        // --- form
        for (StartEvent s : inizi) if (s.getFormKey() != null && !s.getFormKey().isBlank()) formKeys.add(s.getFormKey());
        for (UserTask t : p.findFlowElementsOfType(UserTask.class, true)) {
            if (t.getFormKey() != null && !t.getFormKey().isBlank()) formKeys.add(t.getFormKey());
            else avvisi.add(new Problema(nome(t) + " non ha un form: l'utente vedrà solo il pulsante Completa", t.getId()));
            gruppi.addAll(t.getCandidateGroups());
            if (t.getCandidateGroups().isEmpty() && t.getCandidateUsers().isEmpty() && (t.getAssignee() == null || t.getAssignee().isBlank()))
                errori.add(new Problema(nome(t) + ": scegli il gruppo che deve lavorare questa attività", t.getId()));
        }
        Map<String, JsonNode> definizioni = new LinkedHashMap<>();
        for (String fk : new LinkedHashSet<>(formKeys)) {
            JsonNode def = formAggiuntivi.containsKey(fk) ? formAggiuntivi.get(fk) : forms.load(fk);
            if (def == null) errori.add(new Problema("Il form \"" + fk + "\" non esiste: crealo o scegline un altro", elementoConForm(p, fk)));
            else definizioni.put(fk, def);
        }

        // dati disponibili nella pratica: campi dei form + dati di sistema
        Set<String> dati = new HashSet<>(DATI_DI_SISTEMA);
        definizioni.values().forEach(def -> dati.addAll(forms.fieldTypes(def).keySet()));
        dati.addAll(datiProdotti(p));
        dati.addAll(datiDaiMessaggi(p));
        dati.addAll(datiDaiProcessiRichiamati(p).keySet());
        // date usate dai timer
        for (FlowElement el : tutti) {
            if (el instanceof Event ev && unica(ev) instanceof TimerEventDefinition t)
                for (String testo : new String[]{t.getTimeDate(), t.getTimeDuration(), t.getTimeCycle()})
                    if (testo != null) for (String d : datiUsati(testo))
                        if (!dati.contains(d))
                            avvisi.add(new Problema(nome(el) + ": usa il dato \"" + d + "\" che nessun form raccoglie", el.getId()));
        }

        // --- attività automatiche e connettori
        Map<String, ConnettoreOrditogram> catalogo = new HashMap<>();
        ctx.getBeansOfType(ConnettoreOrditogram.class).values().forEach(c -> catalogo.put(c.id(), c));
        for (ServiceTask t : p.findFlowElementsOfType(ServiceTask.class, true)) {
            String impl = t.getImplementation();
            if (impl == null || impl.isBlank() || impl.equals("##WebService")) {
                errori.add(new Problema(nome(t) + ": scegli cosa deve fare questa attività automatica", t.getId()));
                continue;
            }
            if (!impl.startsWith("${") && !ctx.containsBean(impl)) {
                errori.add(new Problema(nome(t) + ": il connettore \"" + impl + "\" non esiste in questa installazione", t.getId()));
                continue;
            }
            ConnettoreOrditogram c = catalogo.get(impl);
            if (c == null) continue;
            Map<String, String> valori = new HashMap<>();
            t.getFieldExtensions().forEach(f -> valori.put(f.getFieldName(),
                f.getStringValue() != null ? f.getStringValue() : f.getExpression()));
            Set<String> credenzialiNote = credenzialiConfigurate();
            for (ConnettoreOrditogram.Parametro par : c.parametri()) {
                String v = valori.get(par.nome());
                boolean vuoto = v == null || v.isBlank();
                if (par.obbligatorio() && vuoto && !notBlank(par.predefinito()))
                    errori.add(new Problema(nome(t) + ": manca \"" + par.etichetta() + "\"", t.getId()));
                if (!vuoto && "scelta".equals(par.tipo()) && !par.opzioni().isEmpty() && !par.opzioni().contains(v))
                    errori.add(new Problema(nome(t) + ": \"" + par.etichetta() + "\" ha un valore non previsto (" + v + ")", t.getId()));
                if (!vuoto && "credenziale".equals(par.tipo()) && !credenzialiNote.contains(v.trim()))
                    avvisi.add(new Problema(nome(t) + ": la credenziale \"" + v + "\" non è configurata su questo server", t.getId()));
                if (v != null) for (String d : datiUsati(v))
                    if (!dati.contains(d))
                        avvisi.add(new Problema(nome(t) + ": \"" + par.etichetta() + "\" usa il dato \"" + d + "\" che nessun form raccoglie", t.getId()));
            }
            if (c.puoFallire(valori) && !haGestioneErrore(p, t))
                avvisi.add(new Problema(nome(t) + ": se non riesce la pratica si ferma. Aggiungi \"Se non riesce\" per indicare la strada da seguire", t.getId()));
        }

        // --- processi richiamati (call activity): devono essere già pubblicati
        for (CallActivity c : p.findFlowElementsOfType(CallActivity.class, true)) {
            String chiamato = c.getCalledElement();
            if (!notBlank(chiamato)) {
                errori.add(new Problema(nome(c) + ": scegli il processo da richiamare", c.getId()));
                continue;
            }
            chiamato = chiamato.trim();
            if (chiamato.startsWith("${")) {
                errori.add(new Problema(nome(c) + ": indica il processo da richiamare per nome, non con un'espressione", c.getId()));
                continue;
            }
            if (chiamato.equals(p.getId())) {
                errori.add(new Problema(nome(c) + ": un processo non può richiamare sé stesso", c.getId()));
                continue;
            }
            ProcessDefinition def = ultimaVersione(chiamato);
            if (def == null) {
                errori.add(new Problema(nome(c) + ": il processo \"" + chiamato + "\" non è pubblicato: pubblicalo prima di questo", c.getId()));
                continue;
            }
            Process figlio = repositoryService.getBpmnModel(def.getId()).getMainProcess();
            if (figlio.findFlowElementsOfType(CallActivity.class, true).stream()
                    .anyMatch(x -> p.getId().equals(Optional.ofNullable(x.getCalledElement()).map(String::trim).orElse(""))))
                errori.add(new Problema(nome(c) + ": il processo \"" + chiamato + "\" richiama a sua volta questo processo: le pratiche non finirebbero mai", c.getId()));
            if (!haGestioneErrore(p, c))
                avvisi.add(new Problema(nome(c) + ": se il processo richiamato si ferma per un errore, si ferma anche questa pratica. "
                    + "Puoi aggiungere \"Se non riesce\" per indicare la strada da seguire", c.getId()));
        }

        // --- decisioni
        for (ExclusiveGateway g : p.findFlowElementsOfType(ExclusiveGateway.class, true)) {
            List<SequenceFlow> uscite = g.getOutgoingFlows();
            if (uscite.size() < 2) continue;
            String predefinita = g.getDefaultFlow();
            long senza = uscite.stream()
                .filter(f -> (f.getConditionExpression() == null || f.getConditionExpression().isBlank()) && !f.getId().equals(predefinita))
                .count();
            if (senza > 0)
                errori.add(new Problema(nome(g) + ": ogni freccia in uscita deve avere una condizione, oppure essere la freccia \"altrimenti\"", g.getId()));
            else if (predefinita == null || predefinita.isBlank())
                avvisi.add(new Problema(nome(g) + ": se nessuna condizione è vera la pratica si blocca. Conviene indicare una freccia \"altrimenti\"", g.getId()));
        }
        for (SequenceFlow f : p.findFlowElementsOfType(SequenceFlow.class, true)) {
            if (f.getConditionExpression() == null) continue;
            for (String d : datiUsati(f.getConditionExpression()))
                if (!dati.contains(d))
                    avvisi.add(new Problema("La condizione \"" + Optional.ofNullable(f.getName()).orElse(f.getId())
                        + "\" usa il dato \"" + d + "\" che nessun form raccoglie", f.getId()));
        }

        return new Analisi(p.getId(), p.getName(), new ArrayList<>(new LinkedHashSet<>(formKeys)), gruppi,
            p.getCandidateStarterGroups(), errori, avvisi);
    }

    /** Dati scritti dai connettori del processo (es. la risposta di un'API). */
    public Set<String> datiProdotti(Process p) {
        Set<String> out = new LinkedHashSet<>();
        Map<String, ConnettoreOrditogram> catalogo = new HashMap<>();
        ctx.getBeansOfType(ConnettoreOrditogram.class).values().forEach(c -> catalogo.put(c.id(), c));
        for (ServiceTask t : p.findFlowElementsOfType(ServiceTask.class, true)) {
            ConnettoreOrditogram c = catalogo.get(t.getImplementation());
            if (c == null) continue;
            Map<String, String> valori = new HashMap<>();
            t.getFieldExtensions().forEach(f -> valori.put(f.getFieldName(),
                f.getStringValue() != null ? f.getStringValue() : f.getExpression()));
            out.addAll(c.datiProdotti(valori));
        }
        return out;
    }

    /**
     * Dati che i processi richiamati (call activity) restituiscono alla pratica: i campi dei loro form, con il tipo.
     * Il processo richiamato riceve tutti i dati della pratica e alla fine li restituisce tutti.
     */
    public Map<String, String> datiDaiProcessiRichiamati(Process p) {
        Map<String, String> out = new LinkedHashMap<>();
        for (CallActivity c : p.findFlowElementsOfType(CallActivity.class, true)) {
            if (!notBlank(c.getCalledElement()) || c.getCalledElement().trim().equals(p.getId())) continue;
            ProcessDefinition def = ultimaVersione(c.getCalledElement().trim());
            if (def == null) continue;
            Process figlio = repositoryService.getBpmnModel(def.getId()).getMainProcess();
            List<String> chiavi = new ArrayList<>();
            figlio.findFlowElementsOfType(StartEvent.class, false).forEach(s -> { if (notBlank(s.getFormKey())) chiavi.add(s.getFormKey()); });
            figlio.findFlowElementsOfType(UserTask.class, true).forEach(t -> { if (notBlank(t.getFormKey())) chiavi.add(t.getFormKey()); });
            for (String fk : chiavi) forms.fieldTypes(forms.load(fk)).forEach(out::putIfAbsent);
            datiProdotti(figlio).forEach(d -> out.putIfAbsent(d, "string"));
        }
        return out;
    }

    private ProcessDefinition ultimaVersione(String chiave) {
        if (!chiave.matches("[A-Za-z_][\\w.-]*")) return null;
        return repositoryService.createProcessDefinitionQuery().processDefinitionKey(chiave).latestVersion().singleResult();
    }

    /** Nomi delle credenziali configurate sul server (app.credenziali). */
    private Set<String> credenzialiConfigurate() {
        Set<String> out = new HashSet<>();
        ctx.getBeansOfType(com.eligesoft.orditogram.integrazione.Credenziali.class).values()
            .forEach(c -> out.addAll(c.getCredenziali().keySet()));
        return out;
    }

    /** C'è un evento di errore sul bordo dell'attività o di un sottoprocesso che la contiene? */
    private static boolean haGestioneErrore(Process p, FlowElement el) {
        List<BoundaryEvent> bordi = p.findFlowElementsOfType(BoundaryEvent.class, true);
        while (el != null) {
            String id = el.getId();
            if (bordi.stream().anyMatch(b -> id.equals(b.getAttachedToRefId())
                    && b.getEventDefinitions().stream().anyMatch(d -> d instanceof ErrorEventDefinition)))
                return true;
            el = el.getSubProcess();
        }
        return false;
    }

    /**
     * Controlli che richiedono il modello intero: il nome di ogni messaggio usato da un evento
     * (è il nome che il motore usa per l'attesa e che finisce nell'indirizzo /api/messaggi/{nome}).
     */
    public static List<Problema> problemiMessaggi(BpmnModel model) {
        List<Problema> out = new ArrayList<>();
        for (Process p : model.getProcesses()) {
            for (FlowElement el : tutti(p)) {
                if (!(el instanceof Event ev) || !(unica(ev) instanceof MessageEventDefinition m)) continue;
                String ref = m.getMessageRef();
                if (ref == null || ref.isBlank() || !model.containsMessageId(ref)) continue;
                String nome = model.getMessage(ref).getName();
                if (nome == null || !nome.matches("[a-zA-Z][\\w-]*"))
                    out.add(new Problema(nome(el) + ": il messaggio \"" + (nome == null ? ref : nome)
                        + "\" deve avere un nome senza spazi, che inizi con una lettera", el.getId()));
            }
        }
        return out;
    }

    /** Dati portati dai messaggi in arrivo, dichiarati nel BPMN con <orditogram:datiMessaggio>a, b</orditogram:datiMessaggio>. */
    public static Set<String> datiDaiMessaggi(Process p) {
        Set<String> out = new LinkedHashSet<>();
        for (FlowElement el : tutti(p)) {
            if (!(el instanceof Event ev) || !(unica(ev) instanceof MessageEventDefinition)) continue;
            List<ExtensionElement> ext = el.getExtensionElements().getOrDefault("datiMessaggio", List.of());
            for (ExtensionElement e : ext)
                if (e.getElementText() != null)
                    for (String d : e.getElementText().split("[,\\s]+"))
                        if (d.matches("[a-zA-Z][\\w-]*")) out.add(d);
        }
        return out;
    }

    /** Tutti gli elementi del processo, compresi quelli dentro i sottoprocessi. */
    private static List<FlowElement> tutti(FlowElementsContainer c) {
        List<FlowElement> out = new ArrayList<>();
        for (FlowElement el : c.getFlowElements()) {
            out.add(el);
            if (el instanceof SubProcess sp) out.addAll(tutti(sp));
        }
        return out;
    }

    private static EventDefinition unica(Event e) {
        return e.getEventDefinitions().size() == 1 ? e.getEventDefinitions().get(0) : null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Nomi dei dati usati dentro le espressioni ${...} di un testo. */
    static Set<String> datiUsati(String testo) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = ESPRESSIONE.matcher(testo);
        while (m.find()) {
            String expr = m.group(1).replaceAll("'[^']*'|\"[^\"]*\"", " ");   // via le stringhe tra apici
            Matcher id = IDENTIFICATORE.matcher(expr);
            while (id.find()) {
                String parola = id.group();
                // salta proprietà e metodi (es. x.size()), numeri e parole riservate
                int prima = id.start() - 1;
                if (prima >= 0 && expr.charAt(prima) == '.') continue;
                if (!PAROLE_EL.contains(parola)) out.add(parola);
            }
        }
        return out;
    }

    private static String elementoConForm(Process p, String formKey) {
        for (StartEvent s : p.findFlowElementsOfType(StartEvent.class, false)) if (formKey.equals(s.getFormKey())) return s.getId();
        for (UserTask t : p.findFlowElementsOfType(UserTask.class, true)) if (formKey.equals(t.getFormKey())) return t.getId();
        return null;
    }

    private static String nome(FlowElement el) {
        String n = el.getName() != null && !el.getName().isBlank() ? el.getName() : null;
        String tipo = el instanceof StartEvent ? "L'inizio"
            : el instanceof EndEvent ? "La fine"
            : el instanceof UserTask ? "L'attività"
            : el instanceof ServiceTask ? "L'attività automatica"
            : el instanceof Gateway ? "La decisione"
            : el instanceof SubProcess ? "Il sottoprocesso"
            : el instanceof CallActivity ? "Il processo richiamato"
            : el instanceof BoundaryEvent b ? (b.getEventDefinitions().stream().anyMatch(d -> d instanceof ErrorEventDefinition)
                ? "La gestione errore"
                : b.getEventDefinitions().stream().anyMatch(d -> d instanceof MessageEventDefinition) ? "Il messaggio sul bordo"
                : b.isCancelActivity() ? "La scadenza" : "Il sollecito")
            : el instanceof IntermediateCatchEvent ic && unica(ic) instanceof MessageEventDefinition ? "Il messaggio in arrivo"
            : el instanceof IntermediateCatchEvent ? "L'attesa"
            : "L'elemento";
        return n != null ? tipo + " \"" + n + "\"" : tipo + " senza nome";
    }
}
