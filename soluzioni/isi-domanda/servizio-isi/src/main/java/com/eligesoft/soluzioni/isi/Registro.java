package com.eligesoft.soluzioni.isi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static com.eligesoft.soluzioni.isi.Testi.codiceAsse;
import static com.eligesoft.soluzioni.isi.Testi.si;
import static com.eligesoft.soluzioni.isi.Testi.testo;
import static com.eligesoft.soluzioni.isi.Testi.troncaEuro;

/**
 * Registri del bando: domande registrate (con la fotografia), codici identificativi, invii allo sportello, elenchi,
 * termini delle istruttorie. Una sola istanza del servizio: le operazioni che cambiano i registri sono in fila
 * ({@code synchronized}); l'unicità per impresa e annualità e quella dei codici le garantisce anche il database.
 */
@Service
public class Registro {
    static final String ALFABETO_CODICE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // senza 0/O, 1/I (H7)
    private static final DateTimeFormatter ISO_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");
    private static final DateTimeFormatter ISO_S = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final DateTimeFormatter APERTURA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'alle' HH:mm");
    private static final Set<String> STATI = Set.of("DECADUTA", "RINUNCIATA", "DOCUMENTI_INVIATI", "IN_ISTRUTTORIA",
            "AMMESSA", "AMMESSA_PARZIALMENTE", "RESPINTA", "ANNULLATA");
    private static final Map<String, String> STATO_DA_ESITO = Map.of("NCD", "NCD", "IN_POSIZIONE_UTILE",
            "IN_POSIZIONE_UTILE", "NON_AMMISSIBILE_FONDI", "NON_AMMISSIBILE_FONDI", "SUBENTRATA", "SUBENTRATA");

    private final JdbcTemplate db;
    private final Regole regole;
    private final Clock orologio;
    private final boolean calendarioLibero;
    private final SecureRandom caso = new SecureRandom();

    public Registro(JdbcTemplate db, Regole regole, Clock orologio, Impostazioni impostazioni) {
        this.db = db;
        this.regole = regole;
        this.orologio = orologio;
        this.calendarioLibero = impostazioni.sportelloLibero(); // solo sviluppo e collaudo: sportello sempre aperto
    }

    /** Una domanda registrata, come sta nel registro. */
    record Domanda(String numero, String pratica, String regione, String asse, BigDecimal contributo,
                   BigDecimal importoProgetto, String dataIscrizione, BigDecimal fatturato, int ratingLegalita,
                   long tsRegistrazione, String stato, String codice, Long codiceUsatoIl, Long tsInvio,
                   Integer posizione, String versioneRegole, String impronta) {}

    private static final String COLONNE = "numero, pratica, regione, asse, contributo, importo_progetto, data_iscrizione,"
            + " fatturato, rating_legalita, ts_registrazione, stato, codice, codice_usato_il, ts_invio, posizione,"
            + " versione_regole, impronta";

    private List<Domanda> domande(String dove, Object... argomenti) {
        return db.query("SELECT " + COLONNE + " FROM domanda WHERE " + dove, (rs, i) -> new Domanda(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), new BigDecimal(rs.getString(5)),
                new BigDecimal(rs.getString(6)), rs.getString(7), Testi.dec(rs.getString(8), BigDecimal.ZERO),
                rs.getInt(9), rs.getLong(10), rs.getString(11), rs.getString(12), (Long) rs.getObject(13, Long.class),
                rs.getObject(14, Long.class), rs.getObject(15, Integer.class), rs.getString(16), rs.getString(17)),
                argomenti);
    }

    Domanda domanda(String numero) {
        List<Domanda> l = domande("numero = ?", numero);
        if (l.isEmpty()) throw new ErroreRichiesta(404, "DOMANDA_NON_TROVATA", "Domanda non trovata");
        return l.get(0);
    }

    long adessoMs() {
        return orologio.millis();
    }

    String isoMs(long ms) {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), orologio.getZone()).format(ISO_MS);
    }

    // -------------------------------------------------------------- registrazione (RF-20)

    public synchronized Map<String, String> registra(Map<String, Object> d) {
        Risultato r = Calcolo.calcola(d, regole);
        if (!r.registrabile()) {
            return mappa("esitoRegistrazione", "NON_REGISTRABILE", "esitoElenco", "", "messaggioRegistrazione", r.errori.get(0));
        }
        String impresa = testo(d.get("codiceFiscaleImpresa")).toUpperCase(Locale.ROOT);
        if (impresa.isEmpty()) throw new ErroreRichiesta(400, "DATI_NON_VALIDI", "Manca il codice fiscale dell'impresa");
        long ts = adessoMs();
        Map<String, Object> dati = new TreeMap<>(d);
        String fotografia;
        try {
            fotografia = Regole.JSON.writeValueAsString(Map.of("dati", dati, "risultato", new TreeMap<>(r.inTesto(regole))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String numero = "ISI" + regole.annualita() + "-" + String.format("%08d", caso.nextInt(100_000_000));
        java.time.LocalDate iscrizione = Testi.data(d.get("dataIscrizioneCciaa"));
        try {
            db.update("INSERT INTO domanda (numero, pratica, annualita, impresa, regione, asse, contributo, importo_progetto,"
                            + " data_iscrizione, fatturato, rating_legalita, ts_registrazione, stato, fotografia, versione_regole,"
                            + " impronta) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    numero, testo(d.get("pratica")), regole.annualita(), impresa, testo(d.get("regione")),
                    codiceAsse(d.get("asse")), r.importo("contributoTotale").toPlainString(),
                    r.importo("spesaAmmissibile").toPlainString(), iscrizione == null ? null : iscrizione.toString(),
                    testo(d.get("fatturato")), si(d.get("ratingLegalita")) ? 1 : 0, ts, "REGISTRATA", fotografia,
                    regole.versione(), sha256(fotografia));
        } catch (DuplicateKeyException e) {
            return mappa("esitoRegistrazione", "GIA_REGISTRATA", "esitoElenco", "",
                    "messaggioRegistrazione", "L'impresa ha già registrato una domanda per questo Avviso");
        }
        transizione(numero, "BOZZA", "REGISTRATA", "impresa", "REGISTRA", ts);
        Map<String, String> out = mappa("esitoRegistrazione", "REGISTRATA", "esitoElenco", "", "numeroDomanda", numero,
                "dataOraRegistrazione", isoMs(ts), "messaggioRegistrazione", "Domanda registrata");
        out.put("versioneRegole", regole.versione());
        out.put("gruppoSede", regole.gruppoSede(testo(d.get("regione"))));
        return out;
    }

    private void transizione(String numero, String da, String a, String chi, String motivo, long ts) {
        db.update("INSERT INTO transizione (numero, da_stato, a_stato, ts, chi, motivo) VALUES (?,?,?,?,?,?)",
                numero, da, a, ts, chi, motivo);
    }

    public synchronized Map<String, String> cambiaStato(String numero, String stato, String chi, String motivo) {
        if (!STATI.contains(stato)) throw new ErroreRichiesta(400, "STATO_NON_VALIDO", "Stato non previsto: " + stato);
        String prima = domanda(numero).stato();
        db.update("UPDATE domanda SET stato = ? WHERE numero = ?", stato, numero);
        transizione(numero, prima, stato, chi, motivo, adessoMs());
        return mappa("statoDomanda", stato);
    }

    // -------------------------------------------------------------- codice identificativo (RF-21)

    public synchronized Map<String, String> codice(String numero) {
        Domanda x = domanda(numero);
        if (x.codice() != null) {
            if (x.codiceUsatoIl() != null) {
                throw new ErroreRichiesta(409, "CODICE_GIA_USATO", "Il codice è già stato inviato allo sportello");
            }
            return mappa("codiceIdentificativo", x.codice());
        }
        if (!x.stato().equals("REGISTRATA")) {
            throw new ErroreRichiesta(409, "STATO_NON_VALIDO", "Il codice si acquisisce solo per una domanda registrata");
        }
        String c;
        while (true) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < 12; i++) s.append(ALFABETO_CODICE.charAt(caso.nextInt(ALFABETO_CODICE.length())));
            c = s.toString();
            try {
                db.update("UPDATE domanda SET codice = ?, stato = 'CODICE_RILASCIATO' WHERE numero = ?", c, numero);
                break;
            } catch (DuplicateKeyException e) {
                // codice già di un'altra domanda: se ne estrae un altro
            }
        }
        transizione(numero, "REGISTRATA", "CODICE_RILASCIATO", "impresa", "Acquisizione del codice", adessoMs());
        return mappa("codiceIdentificativo", c);
    }

    // -------------------------------------------------------------- sportello (RF-25)

    public Map<String, String> sportello(String numero, Object codiceInviato) {
        long ts = adessoMs(); // l'ora si prende per prima, dall'orologio del server (CA-02)
        String codice = testo(codiceInviato).toUpperCase(Locale.ROOT).replace(" ", "");
        if (!calendarioLibero) {
            OffsetDateTime ora = OffsetDateTime.ofInstant(Instant.ofEpochMilli(ts), orologio.getZone());
            OffsetDateTime apre = OffsetDateTime.parse(regole.testo("calendario", "aperturaSportello"));
            OffsetDateTime chiude = OffsetDateTime.parse(regole.testo("calendario", "chiusuraSportello"));
            if (ora.isBefore(apre) || ora.isAfter(chiude)) {
                db.update("INSERT INTO invio_sportello (ts, numero, esito) VALUES (?,?,?)", ts, numero, "FUORI_FINESTRA");
                String prossima = ora.isBefore(apre) ? apre.format(APERTURA) : "nessuna: lo sportello è chiuso";
                return mappa("esitoInvio", "RIFIUTATO", "messaggioInvio", "Sportello chiuso. Prossima apertura: " + prossima);
            }
        }
        synchronized (this) {
            List<Domanda> l = domande("numero = ? AND codice = ?", numero, codice);
            if (l.isEmpty() || l.get(0).codiceUsatoIl() != null || !l.get(0).stato().equals("CODICE_RILASCIATO")) {
                db.update("INSERT INTO invio_sportello (ts, numero, esito) VALUES (?,?,?)", ts, numero, "RIFIUTATO");
                // stesso messaggio per codice inesistente, di altri o già usato (CA-04, CA-05)
                return mappa("esitoInvio", "RIFIUTATO", "messaggioInvio", "Codice non valido o già utilizzato");
            }
            db.update("UPDATE domanda SET ts_invio = ?, codice_usato_il = ?, stato = 'INVIATA_SPORTELLO' WHERE numero = ?",
                    ts, ts, numero);
            db.update("INSERT INTO invio_sportello (ts, numero, esito) VALUES (?,?,?)", ts, numero, "PRESO_IN_CARICO");
            transizione(numero, "CODICE_RILASCIATO", "INVIATA_SPORTELLO", "impresa", "Invio allo sportello", ts);
        }
        return mappa("esitoInvio", "INVIATA", "dataOraInvio", isoMs(ts), "messaggioInvio", "Codice preso in carico il " + isoMs(ts));
    }

    // -------------------------------------------------------------- elenchi (RF-53, RF-54, RF-55)

    /** Una riga di un elenco: posizione per Regione e Asse, importo troncato all'euro, esito. */
    public record Riga(String numero, String pratica, String regione, String asse, Integer posizione, long importo,
                       String esito) {}

    private BigDecimal stanziamento(String regione, String asse) {
        return regole.num("stanziamenti", regione, asse);
    }

    private List<Domanda> conStato(String... stati) {
        String segnaposti = String.join(",", java.util.Collections.nCopies(stati.length, "?"));
        Object[] arg = new Object[stati.length + 1];
        arg[0] = regole.annualita();
        System.arraycopy(stati, 0, arg, 1, stati.length);
        return domande("annualita = ? AND stato IN (" + segnaposti + ") ORDER BY ts_registrazione, numero", arg);
    }

    private static Map<String, List<Domanda>> perRegioneEAsse(List<Domanda> l) {
        return l.stream().collect(Collectors.groupingBy(x -> x.regione() + "\u0000" + x.asse(), LinkedHashMap::new,
                Collectors.toList()));
    }

    /** Ora di arrivo, poi i criteri di precedenza 9.10. */
    static final Comparator<Domanda> PRECEDENZA = Comparator
            .comparingLong((Domanda x) -> x.tsInvio() == null ? Long.MAX_VALUE : x.tsInvio())
            .thenComparingLong(Domanda::tsRegistrazione)
            .thenComparing(Domanda::contributo)
            .thenComparing(Domanda::importoProgetto, Comparator.reverseOrder())
            .thenComparing(x -> x.dataIscrizione() == null ? "9999" : x.dataIscrizione())
            .thenComparingInt(x -> x.fatturato().compareTo(BigDecimal.valueOf(2_000_000)) > 0 ? -x.ratingLegalita() : 0);

    private static Riga riga(Domanda x, Integer posizione, String esito) {
        return new Riga(x.numero(), x.pratica(), x.regione(), x.asse(), posizione, troncaEuro(x.contributo()), esito);
    }

    List<Riga> calcolaElenco(String tipo) {
        List<Riga> righe = new ArrayList<>();
        switch (tipo) {
            case "NCD" -> perRegioneEAsse(conStato("REGISTRATA", "CODICE_RILASCIATO", "INVIATA_SPORTELLO")).values()
                    .forEach(lista -> {
                        BigDecimal somma = lista.stream().map(Domanda::contributo).reduce(BigDecimal.ZERO, BigDecimal::add);
                        Domanda primo = lista.get(0);
                        if (somma.compareTo(stanziamento(primo.regione(), primo.asse())) <= 0) {
                            List<Domanda> ordinate = new ArrayList<>(lista);
                            ordinate.sort(Comparator.comparingLong(Domanda::tsRegistrazione));
                            for (int i = 0; i < ordinate.size(); i++) righe.add(riga(ordinate.get(i), i + 1, "NCD"));
                        }
                    });
            case "PROVVISORIO" -> perRegioneEAsse(conStato("INVIATA_SPORTELLO")).values().forEach(lista -> {
                // mai inviate allo sportello: fuori (CA-03)
                List<Domanda> ordinate = new ArrayList<>(lista);
                ordinate.sort(PRECEDENZA);
                BigDecimal residuo = stanziamento(ordinate.get(0).regione(), ordinate.get(0).asse());
                for (int i = 0; i < ordinate.size(); i++) {
                    Domanda x = ordinate.get(i);
                    if (x.contributo().compareTo(residuo) <= 0) { // H3: chi supera il residuo è escluso, si prosegue
                        residuo = residuo.subtract(x.contributo());
                        righe.add(riga(x, i + 1, "IN_POSIZIONE_UTILE"));
                    } else {
                        righe.add(riga(x, i + 1, "NON_AMMISSIBILE_FONDI"));
                    }
                }
            });
            case "DEFINITIVO" -> perRegioneEAsse(conStato("NCD", "IN_POSIZIONE_UTILE", "SUBENTRATA", "DOCUMENTI_INVIATI",
                    "IN_ISTRUTTORIA", "NON_AMMISSIBILE_FONDI", "DECADUTA", "RINUNCIATA")).values().forEach(lista -> {
                List<Domanda> ordinate = new ArrayList<>(lista);
                ordinate.sort(Comparator.comparingInt((Domanda x) -> x.posizione() == null ? Integer.MAX_VALUE : x.posizione())
                        .thenComparing(PRECEDENZA));
                Set<String> fuori = Set.of("NON_AMMISSIBILE_FONDI", "DECADUTA", "RINUNCIATA");
                BigDecimal impegnato = ordinate.stream().filter(x -> !fuori.contains(x.stato())).map(Domanda::contributo)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal residuo = stanziamento(ordinate.get(0).regione(), ordinate.get(0).asse()).subtract(impegnato);
                for (Domanda x : ordinate) {
                    String esito = switch (x.stato()) {
                        case "DECADUTA", "RINUNCIATA" -> x.stato();
                        default -> "AMMISSIBILE";
                    };
                    if (x.stato().equals("NON_AMMISSIBILE_FONDI")) {
                        if (x.contributo().compareTo(residuo) <= 0) {
                            residuo = residuo.subtract(x.contributo());
                            esito = "SUBENTRATA";
                        } else {
                            esito = "NON_AMMISSIBILE_FONDI";
                        }
                    }
                    righe.add(riga(x, x.posizione(), esito));
                }
            });
            default -> throw new ErroreRichiesta(400, "TIPO_NON_VALIDO", "Tipo di elenco: NCD, PROVVISORIO o DEFINITIVO");
        }
        return righe;
    }

    public synchronized Map<String, String> anteprima(String tipo) {
        List<Riga> righe = calcolaElenco(tipo);
        return mappa("tipoElenco", tipo, "righeElenco", String.valueOf(righe.size()), "riepilogoElenco", testoElenco(righe));
    }

    /** Pubblica l'elenco: ogni domanda prende il suo esito; con {@code avvisa} lo si consegna alle pratiche. */
    public Map<String, String> pubblica(String tipo, BiFunction<Riga, String, Boolean> avvisa) {
        String termine = OffsetDateTime.now(orologio)
                .plusDays(regole.num("calendario", "giorniCaricamentoDocumenti").longValue())
                .truncatedTo(ChronoUnit.SECONDS).format(ISO_S);
        List<Riga> righe;
        synchronized (this) {
            righe = calcolaElenco(tipo);
            for (Riga x : righe) {
                String nuovo = STATO_DA_ESITO.get(x.esito());
                if (nuovo == null) continue;
                String prima = domanda(x.numero()).stato();
                if (!prima.equals(nuovo)) {
                    db.update("UPDATE domanda SET stato = ?, posizione = ?, esito = ? WHERE numero = ?",
                            nuovo, x.posizione(), x.esito(), x.numero());
                    transizione(x.numero(), prima, nuovo, "sistema", "Elenco " + tipo, adessoMs());
                }
            }
            try {
                db.update("INSERT INTO elenco (annualita, tipo, pubblicato_il, righe) VALUES (?,?,?,?)",
                        regole.annualita(), tipo, adessoMs(), Regole.JSON.writeValueAsString(righe));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        int avvisate = 0;
        if (avvisa != null) {
            for (Riga x : righe) {
                if (STATO_DA_ESITO.containsKey(x.esito()) && Boolean.TRUE.equals(avvisa.apply(x, termine))) avvisate++;
            }
        }
        Map<String, String> out = mappa("tipoElenco", tipo, "righeElenco", String.valueOf(righe.size()),
                "praticheAvvisate", String.valueOf(avvisate));
        out.put("riepilogoElenco", testoElenco(righe));
        out.put("termineDocumenti", termine);
        return out;
    }

    /** RF-30: solo i dati pubblicati. */
    public List<Map<String, Object>> elenchiPubblicati() {
        return db.query("SELECT tipo, pubblicato_il, righe FROM elenco ORDER BY pubblicato_il", (rs, i) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("tipo", rs.getString(1));
            e.put("pubblicatoIl", isoMs(rs.getLong(2)));
            try {
                e.put("righe", Regole.JSON.readValue(rs.getString(3), new TypeReference<List<Map<String, Object>>>() {}));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException(ex);
            }
            return e;
        });
    }

    private static String testoElenco(List<Riga> righe) {
        if (righe.isEmpty()) return "Nessuna domanda in questo elenco";
        return righe.stream().map(x -> x.regione() + " · Asse " + x.asse() + " · " + x.posizione() + ". " + x.numero()
                + " · " + String.format(Locale.ITALY, "%,d", x.importo()) + " € · " + x.esito()).collect(Collectors.joining("\n"));
    }

    // -------------------------------------------------------------- istruttoria: 120 giorni con sospensione (RF-57 CA-04)

    public synchronized Map<String, String> istruttoria(String numero, String azione) {
        LocalDate oggi = LocalDate.now(orologio);
        List<String[]> l = statoIstruttoria(numero);
        if (azione.equals("avvia") && l.isEmpty()) {
            db.update("INSERT INTO istruttoria (numero, avviata_il) VALUES (?, ?)", numero, oggi.toString());
        } else if (l.isEmpty()) {
            throw new ErroreRichiesta(404, "ISTRUTTORIA_NON_AVVIATA", "Istruttoria non avviata");
        } else if (azione.equals("sospendi") && l.get(0)[2] == null) {
            db.update("UPDATE istruttoria SET sospesa_dal = ? WHERE numero = ?", oggi.toString(), numero);
        } else if (azione.equals("riprendi") && l.get(0)[2] != null) {
            long giorni = ChronoUnit.DAYS.between(LocalDate.parse(l.get(0)[2]), oggi);
            db.update("UPDATE istruttoria SET giorni_sospesi = giorni_sospesi + ?, sospesa_dal = NULL WHERE numero = ?",
                    giorni, numero);
        } else if (!List.of("avvia", "sospendi", "riprendi").contains(azione)) {
            throw new ErroreRichiesta(404, "NON_TROVATO", "Indirizzo sconosciuto");
        }
        String[] x = statoIstruttoria(numero).get(0);
        LocalDate avvio = LocalDate.parse(x[0]);
        int sospesi = Integer.parseInt(x[1]);
        LocalDate scadenza = avvio.plusDays(regole.parametro("giorniIstruttoria").longValue() + sospesi);
        LocalDate avviso = avvio.plusDays(regole.parametro("giorniAvvisoIstruttoria").longValue() + sospesi);
        Map<String, String> out = mappa("scadenzaIstruttoria", scadenza + "T18:00:00", "avvisoIstruttoria", avviso + "T09:00:00",
                "giorniSospesi", String.valueOf(sospesi));
        out.put("giorniRimanenti", String.valueOf(Math.max(0, ChronoUnit.DAYS.between(oggi, scadenza))));
        return out;
    }

    private List<String[]> statoIstruttoria(String numero) {
        return db.query("SELECT avviata_il, giorni_sospesi, sospesa_dal FROM istruttoria WHERE numero = ?",
                (rs, i) -> new String[] {rs.getString(1), String.valueOf(rs.getInt(2)), rs.getString(3)}, numero);
    }

    // -------------------------------------------------------------- utilità

    static Map<String, String> mappa(String... coppie) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < coppie.length; i += 2) m.put(coppie[i], coppie[i + 1]);
        return m;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
