package com.eligesoft.soluzioni.isi;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.eligesoft.soluzioni.isi.Prova.REGOLE;
import static com.eligesoft.soluzioni.isi.Prova.domanda;
import static org.assertj.core.api.Assertions.assertThat;

/** Casi di prova del capitolo 11 (TC-01 … TC-18) sul motore di calcolo. */
class CalcoloTest {

    private static Risultato calcola(Map<String, Object> d) {
        return Calcolo.calcola(d, REGOLE);
    }

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    @Test
    void tc01Asse2SopraSogliaConAnticipo() {
        Risultato r = calcola(domanda("asse", "2 Rischi infortunistici", "tipologia2", "a) Ancoraggi", "tassoMedio", "60",
                "partiSociali", "Ente bilaterale o Organismo paritetico", "rls", "true", "certIso45001", "true",
                "speseProgetto", "100000", "speseTecniche", "8000", "anticipo", "true"));
        assertThat(r.punteggio).isEqualByComparingTo("201");
        assertThat(r.importo("spesaAmmissibile")).isEqualByComparingTo("108000.00");
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("70200.00");
        assertThat(r.importo("anticipo")).isEqualByComparingTo("35100.00");
        assertThat(r.importo("fideiussione")).isEqualByComparingTo("38610.00");
        assertThat(r.errori).isEmpty();
    }

    @Test
    void tc02SottoSoglia() {
        Risultato r = calcola(domanda("asse", "1.1", "ula", "60", "fatturato", "40000000", "dipendentiImpresa", "60",
                "tassoMedio", "10", "dataIscrizioneCciaa", "2005-01-01", "tipologia11", "b", "speseProgetto", "100000"));
        assertThat(r.punteggio).isEqualByComparingTo("89");
        assertThat(r.registrabile()).isFalse();
        assertThat(r.errori.get(0)).isEqualTo("Punteggio 89 inferiore alla soglia di 130");
    }

    @Test
    void tc03RiduzioneFatturato() {
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "a", "fatturato", "3000000"));
        assertThat(r.dettaglioPunteggio.get(0)).contains("Dimensioni: 30");
    }

    @Test
    void tc04ContributoSottoMinimo() {
        Risultato r = calcola(domanda("asse", "3", "tipologia3", "b", "speseProgetto", "6000", "tassoMedio", "100",
                "rls", "true", "partiSociali", "Due o più parti sociali"));
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("3900.00");
        assertThat(String.join(" ", r.errori)).contains("inferiore al minimo");
    }

    @Test
    void tc05Asse12SenzaMinimo() {
        Risultato r = calcola(domanda("asse", "1.2", "ateco", "47.11", "ula", "20", "dipendentiImpresa", "20",
                "dipendentiUnita", "20", "tipologia12", "a", "tassoMedio", "100", "rls", "true",
                "partiSociali", "Due o più parti sociali", "speseProgetto", "5000"));
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("4000.00");
        assertThat(String.join(" ", r.errori)).doesNotContain("inferiore al minimo");
    }

    @Test
    void tc06MassimaleEAggiuntivo() {
        Risultato r = calcola(domanda("asse", "1.1", "tipologia11", "d", "speseProgetto", "190000",
                "aggiuntivo11", "SGSL ISO 45001", "speseAggiuntivo", "15000"));
        assertThat(r.importo("contributoPrincipale")).isEqualByComparingTo("123500.00");
        assertThat(r.importo("contributoAggiuntivo")).isEqualByComparingTo("6500.00");
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("130000.00");
    }

    @Test
    void tc07ContributoOltreMassimo() {
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "a", "speseProgetto", "250000"));
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("130000");
        assertThat(r.avvisi).anyMatch(a -> a.contains("130.000,00 €"));
    }

    @Test
    void tc08MacchinaOltre80Listino() {
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "b", "macchina1Preventivo", "50000", "macchina1Listino", "55000"));
        assertThat(r.importo("spesaAmmissibile")).isEqualByComparingTo("44000.00");
    }

    @Test
    void tc09SpeseTecnicheOltre10() {
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "a", "speseProgetto", "60000", "speseTecniche", "9000"));
        assertThat(r.importo("spesaAmmissibile")).isEqualByComparingTo("66000.00");
    }

    @Test
    void tc10FasciaUlaAlConfine() {
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "a", "ula", "10.44"));
        assertThat(r.ula).isEqualTo(n("10.4"));
        assertThat(r.dettaglioPunteggio.get(0)).contains("Dimensioni: 45");
        r = calcola(domanda("asse", "2", "tipologia2", "a", "ula", "10,0"));
        assertThat(r.dettaglioPunteggio.get(0)).contains("Dimensioni: 50");
    }

    @Test
    void tc11AgricolaPrimaria() {
        Risultato r = calcola(domanda("ateco", "01.11", "agricolaPrimaria", "true", "rapportoAssicurativo",
                "INPS gestione agricola", "ula", "3", "fatturato", "300000"));
        assertThat(r.assiAmmessi).containsExactly("3", "5.1", "5.2");
        assertThat(r.assiNonAmmessi.get("1.1")).contains("R-DEST-03");
    }

    @Test
    void tc12Asse4Ateco() {
        Risultato r = calcola(domanda("ateco", "25.11"));
        assertThat(r.assiAmmessi).doesNotContain("4");
        assertThat(r.assiNonAmmessi.get("4")).contains("R-DEST-05");
        r = calcola(domanda("ateco", "16.23", "asse", "4", "tipologia4a", "true"));
        assertThat(r.assiAmmessi).contains("4");
        assertThat(String.join(" ", r.dettaglioPunteggio)).contains("Rischiosità: 40");
    }

    @Test
    void tc13Asse4PiuTipologie() {
        Risultato r = calcola(domanda("ateco", "16.23", "asse", "4", "tipologia4a", "true", "tipologia4c", "true"));
        assertThat(String.join(" ", r.dettaglioPunteggio)).contains("Tipologia: 80");
    }

    @Test
    void tc14IncompatibilitaIso45001() {
        Risultato r = calcola(domanda("asse", "1.1", "tipologia11", "a", "aggiuntivo11", "SGSL ISO 45001", "certIso45001", "true"));
        assertThat(String.join(" ", r.dettaglioPunteggio)).doesNotContain("ISO 45001:2023: 5");
        assertThat(r.avvisi).anyMatch(a -> a.contains("R-ALL-06"));
    }

    @Test
    void tc15Asse5DueBeni() {
        // La specifica dà al bene 2 120 punti, ma i criteri comuni (parti sociali 18) valgono per ogni bene:
        // 60 + 60 + 18 = 138. Si verifica la regola come scritta in 9.4 (domanda aperta dell'analisi).
        Risultato r = calcola(domanda("ateco", "01.11", "agricolaPrimaria", "true", "rapportoAssicurativo",
                "INPS gestione agricola", "ula", "3", "fatturato", "300000", "asse", "5.1",
                "partiSociali", "Ente bilaterale o Organismo paritetico", "bene1Tipo", "Trattore", "bene1Misura", "2a",
                "bene1Fattore", "1a", "bene1Preventivo", "40000", "bene1Modalita", "Rottamazione", "bene1AnnoSostituito", "2008",
                "secondoBene", "true", "bene2Tipo", "Macchina con motore", "bene2Misura", "2f", "bene2Fattore", "3a",
                "bene2Preventivo", "20000"));
        assertThat(r.punteggioBeni).usingElementComparator(BigDecimal::compareTo).containsExactly(n("158"), n("138"));
        assertThat(r.errori).isEmpty();
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("39000.00"); // 65% di 40.000 + 20.000
    }

    @Test
    void tc15BisBeneSottoSogliaEscluso() {
        Risultato r = calcola(domanda("ateco", "01.11", "agricolaPrimaria", "true", "rapportoAssicurativo",
                "INPS gestione agricola", "ula", "3", "fatturato", "300000", "asse", "5.1",
                "bene1Tipo", "Trattore", "bene1Misura", "2a", "bene1Fattore", "1a", "bene1Preventivo", "40000",
                "bene1Modalita", "Rottamazione", "bene1AnnoSostituito", "2008", "secondoBene", "true",
                "bene2Tipo", "Macchina senza motore", "bene2Misura", "2f", "bene2Fattore", "3a", "bene2Preventivo", "20000"));
        assertThat(r.punteggioBeni).usingElementComparator(BigDecimal::compareTo).containsExactly(n("140"), n("120"));
        assertThat(r.errori).isEmpty();
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("26000.00");
    }

    @Test
    void tc16Asse52RequisitoMancante() {
        Risultato r = calcola(domanda("ateco", "01.11", "agricolaPrimaria", "true", "rapportoAssicurativo",
                "INPS gestione agricola", "ula", "3", "fatturato", "300000", "asse", "5.2", "giovaneEta", "true",
                "giovaneCapoAzienda", "true", "bene1Tipo", "Trattore", "bene1Misura", "2a", "bene1Fattore", "1a",
                "bene1Preventivo", "40000"));
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("26000.00"); // 65%
        assertThat(r.avvisi).anyMatch(a -> a.contains("R-CALC-06"));
    }

    @Test
    void tc17Permuta() {
        Risultato r = calcola(domanda("ateco", "01.11", "agricolaPrimaria", "true", "rapportoAssicurativo",
                "INPS gestione agricola", "ula", "3", "fatturato", "300000", "asse", "5.1", "bene1Tipo", "Trattore",
                "bene1Misura", "2a", "bene1Fattore", "2a", "bene1Preventivo", "40000", "bene1Modalita", "Permuta",
                "bene1RicavoPermuta", "18000", "bene1AnnoSostituito", "2008", "rls", "true"));
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("22000.00");
    }

    @Test
    void tc18AnticipoAlConfine() {
        // 30.000 € esatti: spesa 46.153,85 × 65% = 30.000,00
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "a", "speseProgetto", "46153.85", "anticipo", "true"));
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("30000.00");
        assertThat(r.importo("anticipo")).isEqualByComparingTo("15000.00");
        r = calcola(domanda("asse", "2", "tipologia2", "a", "speseProgetto", "46152.31", "anticipo", "true"));
        assertThat(r.importo("contributoTotale")).isEqualByComparingTo("29999.00");
        assertThat(r.importo("anticipo")).isEqualByComparingTo("0");
    }

    @Test
    void registrazioneAlPortaleTardiva() {
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "a", "dataAssociazionePortale", "2026-05-27",
                "speseProgetto", "100000"));
        assertThat(r.errori).contains("Registrazione al portale avvenuta oltre il termine previsto dall'art. 12 dell'Avviso");
    }

    @Test
    void dichiarazioneMancante() {
        Map<String, Object> d = domanda("asse", "2", "tipologia2", "a", "speseProgetto", "100000");
        d.remove("dichR18");
        assertThat(calcola(d).errori).contains("Manca la dichiarazione R-REQ-18 (passo 7)");
    }

    @Test
    void etsRunts() {
        assertThat(calcola(domanda("tipoSoggetto", "ETS iscritto al RUNTS")).assiAmmessi).isEqualTo(List.of("1.1"));
    }

    @Test
    void dateDaiConnettori() {
        Risultato r = calcola(domanda("asse", "2", "tipologia2", "a", "dataIscrizioneCciaa", "Thu Jan 04 00:00:00 CET 2024"));
        assertThat(String.join(" ", r.dettaglioPunteggio)).contains("Rischiosità: 4 (iscritta alla CCIAA dal 31/12/2023)");
    }

    @Test
    void importoTroncato() {
        assertThat(Testi.troncaEuro(n("70200.99"))).isEqualTo(70200);
    }

    @Test
    void testiPerOrditogram() {
        Map<String, String> t = calcola(domanda("asse", "2", "tipologia2", "a", "tassoMedio", "60", "speseProgetto", "100000",
                "speseTecniche", "8000")).inTesto(REGOLE);
        assertThat(t.get("contributoTotaleTesto")).isEqualTo("70.200,00 €");
        assertThat(t.get("registrabile")).isEqualTo("Sì");
    }
}
