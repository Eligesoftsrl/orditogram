package com.eligesoft.orditogram.notifica;

import com.eligesoft.orditogram.connettore.ConnettoreOrditogram;
import com.eligesoft.orditogram.connettore.SupportoConnettori;
import com.eligesoft.orditogram.documento.DocumentoService;
import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.StoricoService;
import com.eligesoft.orditogram.user.AppUser;
import com.eligesoft.orditogram.user.AppUserRepository;
import jakarta.mail.internet.MimeMessage;
import org.activiti.api.process.model.IntegrationContext;
import org.activiti.api.process.runtime.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Invia un'email, anche a persone esterne (es. il cittadino che ha presentato l'istanza).
 *
 * <serviceTask implementation="emailConnector">
 *   <activiti:field name="destinatari" stringValue="campo:emailRichiedente, gruppo:protocollo"/>
 *   <activiti:field name="oggetto"     stringValue="Richiesta ${numeroProtocollo} ricevuta"/>
 *   <activiti:field name="testo"       stringValue="Gentile ${nomeRichiedente}, ..."/>
 *   <activiti:field name="allegati"    stringValue="Tutti i documenti della pratica"/>
 * </serviceTask>
 *
 * L'email parte solo dopo che la pratica è stata salvata. Senza server email configurato
 * l'invio viene annotato nello storico come "non inviata", senza bloccare la pratica.
 */
@Component("emailConnector")
public class EmailConnector implements Connector, ConnettoreOrditogram {

    static final String ALLEGATI_NESSUNO = "Nessuno";
    static final String ALLEGATI_DOCUMENTI = "Tutti i documenti della pratica";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Logger log = LoggerFactory.getLogger(EmailConnector.class);

    @Override public String id() { return "emailConnector"; }
    @Override public String nome() { return "Invia email"; }
    @Override public String descrizione() {
        return "Scrive un'email a utenti, gruppi o indirizzi presi dai dati della pratica, anche con i documenti allegati.";
    }
    @Override public List<Parametro> parametri() {
        return List.of(
            new Parametro("destinatari", "A chi", "destinatariEmail", true,
                "Chi ha avviato la pratica, utenti, gruppi, un indirizzo fisso o un indirizzo scritto in un campo del form.",
                "iniziatore", List.of()),
            Parametro.testo("oggetto", "Oggetto", true, "Es. La sua richiesta ${numeroProtocollo} è stata accolta"),
            Parametro.testoLungo("testo", "Testo", true, "Puoi inserire i dati della pratica."),
            Parametro.scelta("allegati", "Allegati", ALLEGATI_NESSUNO,
                "Allega l'ultima versione di ogni documento della pratica.", ALLEGATI_NESSUNO, ALLEGATI_DOCUMENTI));
    }

    private final SupportoConnettori supporto;
    private final StoricoService storico;
    private final AppUserRepository users;
    private final NotificaService notifiche;
    private final DocumentoService documenti;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mittente;

    public EmailConnector(SupportoConnettori supporto, StoricoService storico, AppUserRepository users,
                          NotificaService notifiche, DocumentoService documenti,
                          ObjectProvider<JavaMailSender> mailSender,
                          @Value("${app.notifiche.mittente:noreply@localhost}") String mittente) {
        this.supporto = supporto;
        this.storico = storico;
        this.users = users;
        this.notifiche = notifiche;
        this.documenti = documenti;
        this.mailSender = mailSender;
        this.mittente = mittente;
    }

    @Override
    public IntegrationContext apply(IntegrationContext ctx) {
        Map<String, String> p = supporto.parametri(ctx);
        Map<String, Object> dati = SupportoConnettori.dati(ctx);
        String pi = ctx.getProcessInstanceId();
        String attivita = supporto.nomeAttivita(ctx);

        String oggetto = SupportoConnettori.sostituisci(p.getOrDefault("oggetto", ""), dati).replaceAll("[\\r\\n]+", " ");
        String testo = SupportoConnettori.sostituisci(p.getOrDefault("testo", ""), dati);
        List<String> mancanti = new ArrayList<>();
        Set<String> indirizzi = indirizzi(p.getOrDefault("destinatari", "iniziatore"), dati, supporto.iniziatore(ctx), mancanti);
        // i documenti si leggono al momento dell'invio, dopo il salvataggio: così ci sono anche
        // quelli caricati insieme all'avvio della pratica
        boolean conAllegati = ALLEGATI_DOCUMENTI.equals(p.get("allegati"));

        List<Map<String, Object>> dettagli = new ArrayList<>();
        dettagli.add(Map.of("label", "A", "value", indirizzi.isEmpty() ? "nessun indirizzo" : String.join(", ", indirizzi)));
        dettagli.add(Map.of("label", "Oggetto", "value", oggetto));
        if (conAllegati)
            dettagli.add(Map.of("label", "Allegati", "value", "Documenti della pratica (ultima versione)"));
        if (!mancanti.isEmpty())
            dettagli.add(Map.of("label", "Senza indirizzo email", "value", String.join(", ", new LinkedHashSet<>(mancanti))));

        JavaMailSender sender = NotificaService.configurato(mailSender.getIfAvailable());
        if (indirizzi.isEmpty()) {
            dettagli.add(Map.of("label", "Esito", "value", "Non inviata: nessun destinatario con un indirizzo email valido"));
            storico.registra(pi, PraticaEvento.Tipo.EMAIL, attivita, dettagli);
            return ctx;
        }
        if (sender == null) {
            dettagli.add(Map.of("label", "Esito", "value", "Non inviata: server email non configurato (spring.mail.host)"));
            storico.registra(pi, PraticaEvento.Tipo.EMAIL, attivita, dettagli);
            return ctx;
        }
        dettagli.add(Map.of("label", "Esito", "value", "Inviata"));
        storico.registra(pi, PraticaEvento.Tipo.EMAIL, attivita, dettagli);

        Runnable invio = () -> {
            try {
                List<DocumentoService.Allegato> allegati = conAllegati ? documenti.allegatiCorrenti(pi) : List.of();
                MimeMessage m = sender.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(m, !allegati.isEmpty(), "UTF-8");
                h.setFrom(mittente);
                h.setTo(indirizzi.toArray(String[]::new));
                h.setSubject(oggetto);
                h.setText(testo, false);
                for (DocumentoService.Allegato a : allegati)
                    h.addAttachment(a.nome(), new ByteArrayResource(a.contenuto()), a.contentType());
                sender.send(m);
            } catch (Exception e) {
                log.warn("Email \"{}\" della pratica {} non inviata: {}", oggetto, pi, e.getMessage());
                try {
                    storico.registraSubito(pi, PraticaEvento.Tipo.ERRORE, "Email non inviata: " + attivita,
                        List.of(Map.of("label", "Motivo", "value", String.valueOf(e.getMessage()))));
                } catch (Exception ignored) {
                    // lo storico non deve mai bloccare il motore
                }
            }
        };
        // l'email parte solo se la pratica viene salvata davvero
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invio.run(); }
            });
        } else {
            invio.run();
        }
        return ctx;
    }

    /**
     * Destinatari separati da virgola:
     *   iniziatore · utente:mario · gruppo:protocollo · campo:emailRichiedente · email:ufficio@ente.it
     * Per compatibilità: un indirizzo scritto direttamente vale come email:, un nome semplice come utente:.
     */
    Set<String> indirizzi(String destinatari, Map<String, Object> dati, Optional<String> iniziatore, List<String> mancanti) {
        Set<String> out = new LinkedHashSet<>();
        for (String d : destinatari.split(",")) {
            d = d.trim();
            if (d.isEmpty()) continue;
            if (d.equals("iniziatore")) {
                iniziatore.ifPresent(u -> emailUtente(u, out, mancanti));
            } else if (d.startsWith("gruppo:")) {
                notifiche.risolvi(d).forEach(u -> emailUtente(u, out, mancanti));
            } else if (d.startsWith("utente:")) {
                emailUtente(d.substring(7).trim(), out, mancanti);
            } else if (d.startsWith("campo:")) {
                String campo = d.substring(6).trim();
                Object v = dati.get(campo);
                if (v != null) for (String e : v.toString().split("[,;\\s]+")) aggiungiEmail(e, out, mancanti, campo);
                else mancanti.add("campo " + campo + " vuoto");
            } else if (d.startsWith("email:")) {
                aggiungiEmail(d.substring(6), out, mancanti, d);
            } else if (d.contains("@")) {
                aggiungiEmail(d, out, mancanti, d);
            } else {
                emailUtente(d, out, mancanti);
            }
        }
        return out;
    }

    private void emailUtente(String username, Set<String> out, List<String> mancanti) {
        Optional<String> e = users.findByUsername(username).map(AppUser::getEmail).filter(x -> x != null && !x.isBlank());
        if (e.isPresent()) out.add(e.get().trim());
        else mancanti.add(username);
    }

    private static void aggiungiEmail(String e, Set<String> out, List<String> mancanti, String origine) {
        e = e.trim();
        if (e.isEmpty()) return;
        if (EMAIL.matcher(e).matches()) out.add(e);
        else mancanti.add(origine + " (\"" + e + "\" non è un indirizzo valido)");
    }
}
