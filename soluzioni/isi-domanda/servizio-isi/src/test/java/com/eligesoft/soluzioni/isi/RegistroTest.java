package com.eligesoft.soluzioni.isi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static com.eligesoft.soluzioni.isi.Prova.domanda;
import static org.assertj.core.api.Assertions.assertThat;

/** Casi di prova del capitolo 11 sui registri del bando (TC-19 … TC-24). */
class RegistroTest {
    private Prova.Orologio orologio;
    private Registro reg;

    @BeforeEach
    void prepara() {
        orologio = new Prova.Orologio();
        reg = Prova.registro(orologio, true);
    }

    private static Map<String, String> registra(Registro reg, String cf, String... altri) {
        List<String> c = new ArrayList<>(List.of("codiceFiscaleImpresa", cf, "asse", "2", "tipologia2", "a", "tassoMedio", "60",
                "partiSociali", "Due o più parti sociali", "rls", "true"));
        c.addAll(List.of(altri));
        return reg.registra(domanda(c.toArray(String[]::new)));
    }

    @Test
    void registrazioneEFotografia() {
        Map<String, String> r = registra(reg, "01234560789", "speseProgetto", "100000", "regione", "Toscana");
        assertThat(r.get("esitoRegistrazione")).isEqualTo("REGISTRATA");
        assertThat(r.get("gruppoSede")).isEqualTo("sede-toscana");
        Registro.Domanda x = reg.domanda(r.get("numeroDomanda"));
        assertThat(x.versioneRegole()).isEqualTo("2025.1");
        assertThat(x.impronta()).hasSize(64);
    }

    @Test
    void nonRegistrabileSottoSoglia() {
        Map<String, String> r = reg.registra(domanda("codiceFiscaleImpresa", "X", "asse", "1.1", "tipologia11", "b",
                "ula", "60", "fatturato", "40000000", "tassoMedio", "10"));
        assertThat(r.get("esitoRegistrazione")).isEqualTo("NON_REGISTRABILE");
    }

    @Test
    void tc19UnaSolaDomanda() {
        Map<String, String> a = registra(reg, "01234560789", "speseProgetto", "100000");
        Map<String, String> b = registra(reg, "01234560789", "speseProgetto", "120000");
        assertThat(a.get("esitoRegistrazione")).isEqualTo("REGISTRATA");
        assertThat(b.get("esitoRegistrazione")).isEqualTo("GIA_REGISTRATA");
        assertThat(b.get("messaggioRegistrazione")).isEqualTo("L'impresa ha già registrato una domanda per questo Avviso");
    }

    @Test
    void tc19RegistrazioniConcorrenti() throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> f = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                f.add(es.submit(() -> registra(reg, "09999999999", "speseProgetto", "100000").get("esitoRegistrazione")));
            }
            List<String> esiti = new ArrayList<>();
            for (Future<String> x : f) esiti.add(x.get());
            assertThat(Collections.frequency(esiti, "REGISTRATA")).isEqualTo(1);
        } finally {
            es.shutdown();
        }
    }

    @Test
    void codiceNonAmbiguo() {
        String n = registra(reg, "01234560789", "speseProgetto", "100000").get("numeroDomanda");
        String c = reg.codice(n).get("codiceIdentificativo");
        assertThat(c).hasSize(12).doesNotContain("0", "O", "1", "I");
        assertThat(reg.codice(n).get("codiceIdentificativo")).isEqualTo(c); // si rivede finché non è usato
    }

    @Test
    void tc20SportelloETc21CodiceUsato() {
        List<String[]> numeri = new ArrayList<>();
        String[][] imprese = {{"A1", "92307.69"}, {"A2", "76923.08"}, {"A3", "61538.46"}}; // 60.000, 50.000, 40.000 €
        for (String[] i : imprese) {
            String n = registra(reg, i[0], "speseProgetto", i[1], "regione", "Campania").get("numeroDomanda");
            numeri.add(new String[] {n, reg.codice(n).get("codiceIdentificativo")});
        }
        Iterator<Long> ore = List.of(1000120L, 1000050L, 1000300L).iterator();
        for (String[] x : numeri) {
            long t = ore.next();
            orologio.millis = () -> t;
            assertThat(reg.sportello(x[0], x[1]).get("esitoInvio")).isEqualTo("INVIATA");
        }
        // TC-21: secondo invio rifiutato senza dire perché
        assertThat(reg.sportello(numeri.get(0)[0], numeri.get(0)[1]).get("messaggioInvio"))
                .isEqualTo("Codice non valido o già utilizzato");
        assertThat(reg.sportello(numeri.get(0)[0], "ZZZZZZZZZZZZ").get("esitoInvio")).isEqualTo("RIFIUTATO");
        Map<String, Registro.Riga> righe = reg.calcolaElenco("PROVVISORIO").stream()
                .collect(Collectors.toMap(Registro.Riga::numero, r -> r));
        assertThat(numeri.stream().map(x -> righe.get(x[0]).posizione())).containsExactly(2, 1, 3);
        assertThat(numeri.stream().map(x -> righe.get(x[0]).esito()))
                .containsExactly("NON_AMMISSIBILE_FONDI", "IN_POSIZIONE_UTILE", "IN_POSIZIONE_UTILE");
        assertThat(numeri.stream().map(x -> righe.get(x[0]).importo())).containsExactly(60000L, 50000L, 40000L);
        // redistribuzione: decade quella da 50.000 €, il residuo (60.000 €) basta per quella da 60.000 €
        reg.pubblica("PROVVISORIO", null);
        reg.cambiaStato(numeri.get(1)[0], "DECADUTA", "sistema", "");
        Map<String, String> definitivo = reg.calcolaElenco("DEFINITIVO").stream()
                .collect(Collectors.toMap(Registro.Riga::numero, Registro.Riga::esito));
        assertThat(definitivo.get(numeri.get(0)[0])).isEqualTo("SUBENTRATA");
        assertThat(definitivo.get(numeri.get(1)[0])).isEqualTo("DECADUTA");
        assertThat(definitivo.get(numeri.get(2)[0])).isEqualTo("AMMISSIBILE");
    }

    @Test
    void tc22ElencoNcd() {
        for (String cf : List.of("B1", "B2")) { // 2 × 40.000 € = 80.000 su 2.500.000
            registra(reg, cf, "speseProgetto", "61538.46", "regione", "Campania", "asse", "3", "tipologia3", "b");
        }
        List<Registro.Riga> righe = reg.calcolaElenco("NCD");
        assertThat(righe).hasSize(2).allMatch(r -> r.esito().equals("NCD"));
    }

    @Test
    void tc23Decadenza() {
        String n = registra(reg, "C1", "speseProgetto", "100000").get("numeroDomanda");
        assertThat(reg.cambiaStato(n, "DECADUTA", "sistema", "").get("statoDomanda")).isEqualTo("DECADUTA");
    }

    @Test
    void tc24TecnicoNonAmmesso() {
        assertThat(ServiziFinti.incaricoTecnico(Map.of("cfTecnico", "RSSMRA70A01D612X",
                "cfLegaleRappresentante", "rssmra70a01d612x")).get("incaricoAmmesso")).isEqualTo("No");
        assertThat(ServiziFinti.incaricoTecnico(Map.of("cfTecnico", "VRDLGU75B02H501Y", "cfLegaleRappresentante",
                "RSSMRA70A01D612X", "cfSoci", "BNCGNN80C03F205Z, VRDLGU75B02H501Y")).get("incaricoAmmesso")).isEqualTo("No");
        assertThat(ServiziFinti.incaricoTecnico(Map.of("cfTecnico", "VRDLGU75B02H501Y",
                "cfLegaleRappresentante", "RSSMRA70A01D612X")).get("incaricoAmmesso")).isEqualTo("Sì");
    }

    @Test
    void istruttoriaSospesa() {
        Iterator<LocalDate> giorni = List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21))
                .iterator();
        orologio.millis = () -> giorni.next().atTime(12, 0).atZone(ZoneId.of("Europe/Rome")).toInstant().toEpochMilli();
        assertThat(reg.istruttoria("N1", "avvia").get("scadenzaIstruttoria")).isEqualTo("2026-12-30T18:00:00");
        reg.istruttoria("N1", "sospendi");
        Map<String, String> r = reg.istruttoria("N1", "riprendi");
        assertThat(r.get("giorniSospesi")).isEqualTo("20");
        assertThat(r.get("scadenzaIstruttoria")).isEqualTo("2027-01-19T18:00:00");
    }

    @Test
    void sportelloChiuso() {
        Registro chiuso = Prova.registro(orologio, false);
        String n = registra(chiuso, "D1", "speseProgetto", "100000").get("numeroDomanda");
        String c = chiuso.codice(n).get("codiceIdentificativo");
        Map<String, String> r = chiuso.sportello(n, c);
        assertThat(r.get("esitoInvio")).isEqualTo("RIFIUTATO");
        assertThat(r.get("messaggioInvio")).contains("Sportello chiuso");
    }
}
