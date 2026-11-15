package com.eligesoft.orditogram.integrazione;

import com.eligesoft.orditogram.connettore.ConnettoreOrditogram;
import com.eligesoft.orditogram.connettore.SupportoConnettori;
import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.StoricoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.api.process.model.IntegrationContext;
import org.activiti.api.process.runtime.connector.Connector;
import org.activiti.engine.delegate.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Chiama un servizio REST esterno, configurato interamente dall'analista nell'editor:
 *
 * <serviceTask implementation="apiConnector">
 *   <activiti:field name="metodo"       stringValue="POST"/>
 *   <activiti:field name="url"          stringValue="https://protocollo.ente.it/api/registrazioni"/>
 *   <activiti:field name="credenziale"  stringValue="protocollo"/>        nome configurato sul server
 *   <activiti:field name="intestazioni" stringValue="X-Ente: comune-demo"/>
 *   <activiti:field name="corpo"        stringValue='{"oggetto": "${oggetto}"}'/>
 *   <activiti:field name="risposta"     stringValue="numeroProtocollo = numero&#10;dataProtocollo = data"/>
 *   <activiti:field name="inErrore"     stringValue="Segui la strada di errore"/>
 * </serviceTask>
 *
 * I valori letti dalla risposta diventano dati della pratica, utilizzabili nei form, nei testi e nelle decisioni.
 * In caso di errore la pratica può seguire la freccia dell'evento di errore sul bordo dell'attività
 * (codice ERRORE_API) oppure fermarsi, lasciando l'attività da ripetere.
 */
@Component("apiConnector")
public class ApiConnector implements Connector, ConnettoreOrditogram {

    public static final String CODICE_ERRORE = "ERRORE_API";
    static final String ERRORE_STRADA = "Segui la strada di errore";
    static final String ERRORE_BLOCCA = "Blocca e riprova più tardi";

    private static final Logger log = LoggerFactory.getLogger(ApiConnector.class);

    @Override public String id() { return "apiConnector"; }
    @Override public String nome() { return "Chiama un servizio (API REST)"; }
    @Override public String descrizione() {
        return "Invia una richiesta a un sistema esterno (protocollo, anagrafe, pagamenti…) e salva nella pratica i valori della risposta.";
    }
    @Override public List<Parametro> parametri() {
        return List.of(
            Parametro.scelta("metodo", "Metodo", "GET", "GET legge, POST crea, PUT/PATCH aggiornano, DELETE elimina.",
                "GET", "POST", "PUT", "PATCH", "DELETE"),
            Parametro.testo("url", "Indirizzo", true, "Es. https://servizio.ente.it/api/pratiche/${codice}"),
            new Parametro("credenziale", "Credenziale", "credenziale", false,
                "Configurata sul server dall'amministratore: token e password non finiscono nel diagramma.", "", List.of()),
            Parametro.di("codice", "intestazioni", "Intestazioni", false, "Una per riga, es. X-Ente: comune-demo"),
            Parametro.di("codice", "corpo", "Corpo della richiesta", false,
                "Per POST, PUT e PATCH. Es. {\"oggetto\": \"${oggetto}\"}"),
            Parametro.di("mappatura", "risposta", "Dati da salvare dalla risposta", false,
                "Una riga per dato: numeroProtocollo = numero. Percorsi come esito.codice o elenco[0].id; @stato è il codice HTTP."),
            new Parametro("timeout", "Attesa massima (secondi)", "testo", false, "Oltre questo tempo la chiamata è un errore.", "30", List.of()),
            Parametro.scelta("inErrore", "Se la chiamata non riesce", ERRORE_STRADA,
                "Strada di errore: la pratica prosegue sulla freccia dell'evento di errore (se non c'è, si ferma). "
                    + "Blocca: l'operazione viene annullata e si potrà ripetere.",
                ERRORE_STRADA, ERRORE_BLOCCA));
    }

    @Override
    public Set<String> datiProdotti(Map<String, String> valori) {
        return ClienteApi.datiDaMappatura(valori.get("risposta"));
    }

    @Override
    public boolean puoFallire(Map<String, String> valori) {
        return !ERRORE_BLOCCA.equals(valori.get("inErrore"));
    }

    private final SupportoConnettori supporto;
    private final StoricoService storico;
    private final Credenziali credenziali;
    private final ObjectMapper mapper;

    public ApiConnector(SupportoConnettori supporto, StoricoService storico, Credenziali credenziali, ObjectMapper mapper) {
        this.supporto = supporto;
        this.storico = storico;
        this.credenziali = credenziali;
        this.mapper = mapper;
    }

    @Override
    public IntegrationContext apply(IntegrationContext ctx) {
        Map<String, String> p = supporto.parametri(ctx);
        String pi = ctx.getProcessInstanceId();
        String attivita = supporto.nomeAttivita(ctx);
        // senza un evento di errore sul bordo non c'è una strada da seguire: ci si ferma
        boolean blocca = ERRORE_BLOCCA.equals(p.get("inErrore")) || !supporto.haGestioneErrore(ctx);

        ClienteApi.Richiesta richiesta = null;
        try {
            richiesta = ClienteApi.prepara(p, SupportoConnettori.dati(ctx));
            String nomeCred = p.get("credenziale");
            Credenziali.Credenziale cred = null;
            if (nomeCred != null && !nomeCred.isBlank()) {
                cred = credenziali.trova(nomeCred).orElseThrow(() ->
                    new ClienteApi.ErroreApi("Credenziale \"" + nomeCred + "\" non configurata sul server", 0, null));
            }
            long t0 = System.currentTimeMillis();
            ClienteApi.Risposta r = ClienteApi.esegui(richiesta, cred);
            long ms = System.currentTimeMillis() - t0;
            if (!r.ok())
                throw new ClienteApi.ErroreApi("Il servizio ha risposto con il codice " + r.stato()
                    + (r.corpo().isBlank() ? "" : ": " + ClienteApi.estratto(r.corpo())), r.stato(), null);

            Map<String, String> valori = ClienteApi.estrai(p.get("risposta"), r, mapper);
            // Activiti rifiuta valori che sembrano espressioni (${...}): si neutralizzano
            valori.replaceAll((k, v) -> v == null ? "" : v.replace("${", "$\u200B{"));
            valori.forEach(ctx::addOutBoundVariable);

            List<Map<String, Object>> dettagli = new ArrayList<>();
            dettagli.add(Map.of("label", "Richiesta", "value", richiesta.metodo() + " " + richiesta.url()));
            dettagli.add(Map.of("label", "Esito", "value", r.stato() + " in " + ms + " ms"));
            valori.forEach((k, v) -> dettagli.add(Map.of("label", k, "value", ClienteApi.estratto(v))));
            storico.registra(pi, PraticaEvento.Tipo.CHIAMATA_API, attivita, dettagli);
            return ctx;
        } catch (ClienteApi.ErroreApi e) {
            log.warn("Chiamata API \"{}\" della pratica {} non riuscita: {}", attivita, pi, e.getMessage());
            List<Map<String, Object>> dettagli = new ArrayList<>();
            if (richiesta != null) dettagli.add(Map.of("label", "Richiesta", "value", richiesta.metodo() + " " + richiesta.url()));
            dettagli.add(Map.of("label", "Motivo", "value", e.getMessage()));
            dettagli.add(Map.of("label", "Cosa succede", "value", blocca
                ? "Operazione annullata: si potrà ripetere quando il servizio torna disponibile"
                : "La pratica segue la strada prevista in caso di errore"));
            if (blocca) {
                // l'operazione in corso viene annullata: l'evento va salvato a parte
                safe(() -> storico.registraSubito(pi, PraticaEvento.Tipo.ERRORE, attivita, dettagli));
                throw new IllegalStateException("\"" + attivita + "\" non è riuscita: " + e.getMessage()
                    + ". Riprova più tardi.", e);
            }
            storico.registra(pi, PraticaEvento.Tipo.ERRORE, attivita, dettagli);
            throw new BpmnError(CODICE_ERRORE, e.getMessage());
        }
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Exception e) { log.warn("Evento non registrato nello storico: {}", e.getMessage()); }
    }
}
