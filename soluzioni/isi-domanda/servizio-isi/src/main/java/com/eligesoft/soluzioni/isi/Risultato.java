package com.eligesoft.soluzioni.isi;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.eligesoft.soluzioni.isi.Testi.euro;
import static com.eligesoft.soluzioni.isi.Testi.numero;
import static com.eligesoft.soluzioni.isi.Testi.punti;

/** Esito del calcolo: ogni valore porta nel dettaglio il codice della regola che lo ha prodotto. */
public class Risultato {
    public List<String> assiAmmessi = new ArrayList<>();
    public Map<String, String> assiNonAmmessi = new LinkedHashMap<>();
    public BigDecimal ula;
    public boolean microPiccola;
    public BigDecimal punteggio = BigDecimal.ZERO;
    public List<BigDecimal> punteggioBeni = new ArrayList<>();
    public List<String> dettaglioPunteggio = new ArrayList<>();
    public Map<String, BigDecimal> importi = new LinkedHashMap<>();
    public List<String> dettaglioImporti = new ArrayList<>();
    public List<String> avvisi = new ArrayList<>();
    public List<String> errori = new ArrayList<>();
    public List<String> documenti = new ArrayList<>();
    /** Beni dell'Asse 5 letti dalla domanda (servono anche agli importi e ai controlli). */
    List<Calcolo.Bene> beni = new ArrayList<>();

    public boolean registrabile() {
        return errori.isEmpty();
    }

    public BigDecimal importo(String nome) {
        return importi.getOrDefault(nome, BigDecimal.ZERO);
    }

    /** Valori per Orditogram: testi brevi, numeri con il punto per le condizioni, testi lunghi a righe. */
    public Map<String, String> inTesto(Regole regole) {
        Map<String, String> t = new LinkedHashMap<>();
        String soglia = numero(regole.soglia());
        t.put("versioneRegole", regole.versione());
        t.put("punteggio", numero(punteggio));
        t.put("punteggioTesto", punti(punteggio));
        t.put("soglia", soglia);
        t.put("registrabile", registrabile() ? "Sì" : "No");
        t.put("microPiccola", microPiccola ? "Sì" : "No");
        t.put("ula", ula == null ? "" : ula.toPlainString());
        t.put("contributoTotale", importo("contributoTotale").toPlainString());
        t.put("contributoTotaleTesto", euro(importi.get("contributoTotale")));
        t.put("contributoPrincipale", importo("contributoPrincipale").toPlainString());
        t.put("contributoAggiuntivo", importo("contributoAggiuntivo").toPlainString());
        t.put("spesaAmmissibile", importo("spesaAmmissibile").toPlainString());
        t.put("anticipo", importo("anticipo").toPlainString());
        t.put("fideiussione", importo("fideiussione").toPlainString());
        t.put("assiAmmessi", assiAmmessi.isEmpty() ? "nessuno" : String.join(", ", assiAmmessi));
        t.put("assiNonAmmessi", assiNonAmmessi.entrySet().stream()
                .map(e -> "Asse " + e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")));
        t.put("riepilogo", "Punteggio " + punti(punteggio) + " su soglia " + soglia + " · contributo "
                + euro(importi.get("contributoTotale")) + " · "
                + (registrabile() ? "registrabile" : errori.size() + " errori bloccanti"));
        t.put("dettaglioPunteggio", String.join("\n", dettaglioPunteggio));
        t.put("dettaglioImporti", String.join("\n", dettaglioImporti));
        t.put("avvisiCalcolo", avvisi.isEmpty() ? "Nessun avviso" : String.join("\n", avvisi));
        t.put("erroriBloccanti", errori.isEmpty() ? "Nessun errore bloccante" : String.join("\n", errori));
        t.put("documentiRichiesti", documenti.stream().map(x -> "• " + x).collect(Collectors.joining("\n")));
        return t;
    }
}
