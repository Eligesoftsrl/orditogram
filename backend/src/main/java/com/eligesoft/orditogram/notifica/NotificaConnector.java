package com.eligesoft.orditogram.notifica;

import com.eligesoft.orditogram.connettore.ConnettoreOrditogram;
import com.eligesoft.orditogram.connettore.SupportoConnettori;
import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.StoricoService;
import org.activiti.api.process.model.IntegrationContext;
import org.activiti.api.process.runtime.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Service task generico di notifica, configurabile direttamente nel BPMN:
 *
 * <serviceTask id="notifica" name="Notifica dirigente" implementation="notificaConnector">
 *   <extensionElements>
 *     <activiti:field name="destinatario" stringValue="iniziatore"/>   iniziatore | username | gruppo:nome (anche più valori separati da virgola)
 *     <activiti:field name="titolo"       stringValue="Pratica accettata"/>
 *     <activiti:field name="messaggio"    stringValue="La pratica ${oggetto} è stata accettata da ${chiusaDa}"/>
 *   </extensionElements>
 * </serviceTask>
 *
 * Nei testi ${nome} viene sostituito con il valore della variabile di processo.
 */
@Component("notificaConnector")
public class NotificaConnector implements Connector, ConnettoreOrditogram {

    @Override public String id() { return "notificaConnector"; }
    @Override public String nome() { return "Invia notifica"; }
    @Override public String descrizione() {
        return "Avvisa una o più persone nella campanella di Orditogram e, se configurata, via email.";
    }
    @Override public List<Parametro> parametri() {
        return List.of(
            new Parametro("destinatario", "A chi", "destinatario", true,
                "Chi ha avviato la pratica, un utente o tutti gli utenti di un gruppo.", "iniziatore", List.of()),
            Parametro.testo("titolo", "Titolo", true, "Es. Pratica accettata: ${oggetto}"),
            Parametro.testoLungo("messaggio", "Messaggio", false, "Puoi inserire i dati della pratica."));
    }

    private static final Logger log = LoggerFactory.getLogger(NotificaConnector.class);

    private final NotificaService notifiche;
    private final StoricoService storico;
    private final SupportoConnettori supporto;
    private final com.eligesoft.orditogram.glossario.GlossarioService glossario;

    public NotificaConnector(NotificaService notifiche, StoricoService storico, SupportoConnettori supporto,
                             com.eligesoft.orditogram.glossario.GlossarioService glossario) {
        this.glossario = glossario;
        this.notifiche = notifiche;
        this.storico = storico;
        this.supporto = supporto;
    }

    @Override
    public IntegrationContext apply(IntegrationContext ctx) {
        Map<String, String> campi = supporto.parametri(ctx);
        Map<String, Object> vars = SupportoConnettori.dati(ctx);
        String pi = ctx.getProcessInstanceId();

        String titolo = SupportoConnettori.sostituisci(campi.getOrDefault("titolo", "Aggiornamento: " + glossario.singolare(com.eligesoft.orditogram.glossario.Termine.PRATICA)), vars);
        String messaggio = SupportoConnettori.sostituisci(campi.getOrDefault("messaggio", ""), vars);

        Set<String> destinatari = new LinkedHashSet<>();
        for (String d : campi.getOrDefault("destinatario", "iniziatore").split(",")) {
            d = d.trim();
            if (d.equals("iniziatore")) supporto.iniziatore(ctx).ifPresent(destinatari::add);
            else destinatari.addAll(notifiche.risolvi(d));
        }

        if (destinatari.isEmpty()) {
            log.warn("Notifica \"{}\" della pratica {}: nessun destinatario trovato", titolo, pi);
        } else {
            notifiche.invia(destinatari, titolo, messaggio, pi);
            storico.registra(pi, PraticaEvento.Tipo.NOTIFICA, titolo,
                List.of(Map.of("label", "Inviata a", "value", String.join(", ", destinatari)),
                        Map.of("label", "Messaggio", "value", messaggio)));
        }
        return ctx;
    }
}
