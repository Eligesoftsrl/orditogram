package com.eligesoft.soluzioni.isi;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eligesoft.soluzioni.isi.Testi.ASSI;
import static com.eligesoft.soluzioni.isi.Testi.centesimi;
import static com.eligesoft.soluzioni.isi.Testi.codiceAsse;
import static com.eligesoft.soluzioni.isi.Testi.codiceVoce;
import static com.eligesoft.soluzioni.isi.Testi.data;
import static com.eligesoft.soluzioni.isi.Testi.dec;
import static com.eligesoft.soluzioni.isi.Testi.euro;
import static com.eligesoft.soluzioni.isi.Testi.min;
import static com.eligesoft.soluzioni.isi.Testi.numero;
import static com.eligesoft.soluzioni.isi.Testi.percento;
import static com.eligesoft.soluzioni.isi.Testi.punti;
import static com.eligesoft.soluzioni.isi.Testi.si;
import static com.eligesoft.soluzioni.isi.Testi.testo;
import static com.eligesoft.soluzioni.isi.Testi.zero;

/**
 * Motore di calcolo del Bando ISI: punteggio, importi, assi ammessi, errori bloccanti, documenti richiesti.
 *
 * <p>Puro (RNF-ARC-02): niente rete, database o interfaccia. Riceve i dati della domanda come li manda Orditogram
 * (testi) e le regole dell'annualità. Ogni risultato porta il dettaglio con il codice della regola.
 */
public final class Calcolo {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private Calcolo() {}

    /** Un bene dell'Asse 5 (fino a due). */
    static final class Bene {
        int n;
        String tipo, misura, fattore, modalita;
        BigDecimal preventivo, listino, ricavoPermuta, annoSostituito, potenzaKw;
        BigDecimal punteggio = ZERO;
        boolean ammesso;
    }

    // ------------------------------------------------------------------ ingresso

    public static Risultato calcola(Map<String, ?> d, Regole regole) {
        Risultato r = new Risultato();
        BigDecimal ula = dec(d.get("ula"));
        r.ula = ula == null ? null : ula.setScale(1, RoundingMode.HALF_UP); // R-ALL-01
        if (r.ula != null && r.ula.compareTo(BigDecimal.ONE) < 0 && si(d.get("attivitaStagionale"))) {
            r.ula = new BigDecimal("1.0"); // RF-11 CA-04
        }
        r.microPiccola = microPiccola(r.ula, d, regole);
        assiAmmessi(d, regole, r);
        controlliImpresa(d, regole, r);

        String asse = codiceAsse(d.get("asse"));
        if (asse.isEmpty()) {
            r.errore(Risultato.PROGETTO, "Scegli l'asse di finanziamento (Scegli asse e progetto)");
            return r;
        }
        if (!r.assiAmmessi.contains(asse)) {
            r.errore(Risultato.PROGETTO, "Asse " + asse + " non ammesso: " + r.assiNonAmmessi.get(asse));
        }

        if (asse.startsWith("5")) {
            r.punteggio = punteggioAsse5(asse, d, regole, r);
        } else {
            BigDecimal tot = ZERO;
            if (!asse.equals("4") || (r.ula != null && r.ula.compareTo(BigDecimal.valueOf(50)) <= 0)) {
                tot = tot.add(puntiDimensione(r.ula, d, regole, r));
            }
            tot = tot.add(puntiRischiosita(asse, d, regole, r));
            tot = tot.add(puntiTipologia(asse, d, regole, r));
            tot = tot.add(puntiComuni(asse, d, regole, r, true));
            tot = tot.add(puntiBonus(asse, d, regole, r));
            r.punteggio = tot;
        }
        r.dettaglioPunteggio.add("Totale: " + punti(r.punteggio));
        if (r.punteggio.compareTo(regole.soglia()) < 0) {
            r.errorePrimo(Risultato.PUNTEGGIO, "Punteggio " + punti(r.punteggio) + " inferiore alla soglia di " + numero(regole.soglia()));
        }

        importi(asse, d, regole, r);
        controlliRegistrazione(asse, d, regole, r);
        r.documenti = documentiRichiesti(asse, d, regole);
        return r;
    }

    // ------------------------------------------------------------------ dimensione e assi

    /** R-DEST-06: meno di 50 ULA e fatturato o totale di bilancio ≤ 10 milioni. */
    static boolean microPiccola(BigDecimal ula, Map<String, ?> d, Regole regole) {
        if (ula == null) return false;
        BigDecimal lim = regole.parametro("microPiccolaFatturatoMassimo");
        List<BigDecimal> valori = fatturatoBilancio(d);
        return ula.compareTo(regole.parametro("microPiccolaUlaMassime")) < 0
                && (valori.isEmpty() || valori.stream().anyMatch(v -> v.compareTo(lim) <= 0));
    }

    private static List<BigDecimal> fatturatoBilancio(Map<String, ?> d) {
        List<BigDecimal> valori = new ArrayList<>();
        for (String campo : List.of("fatturato", "totaleBilancio")) {
            BigDecimal v = dec(d.get(campo));
            if (v != null) valori.add(v);
        }
        return valori;
    }

    static String sezioneAteco(String ateco, Regole regole) {
        String div = ateco.length() >= 2 ? ateco.substring(0, 2) : ateco;
        for (JsonNode s : regole.nodo("sezioniAteco")) {
            if (s.get(0).asText().compareTo(div) <= 0 && div.compareTo(s.get(1).asText()) <= 0) return s.get(2).asText();
        }
        return "";
    }

    /** R-DEST-05 e 9.4.7: i codici valgono come prefisso, conta il più lungo. */
    static String prefissoPiuLungo(String ateco, JsonNode tabella) {
        String trovato = null;
        for (Iterator<String> it = tabella.fieldNames(); it.hasNext(); ) {
            String k = it.next();
            if (ateco.startsWith(k) && (trovato == null || k.length() > trovato.length())) trovato = k;
        }
        return trovato;
    }

    static void assiAmmessi(Map<String, ?> d, Regole regole, Risultato r) {
        String ateco = testo(d.get("ateco"));
        String sogg = testo(d.get("tipoSoggetto"));
        String rapporto = testo(d.get("rapportoAssicurativo"));
        boolean agricola = si(d.get("agricolaPrimaria"));
        boolean mp = r.microPiccola;
        boolean settore4 = prefissoPiuLungo(ateco, regole.nodo("asse4Ateco")) != null;
        Map<String, String> no = r.assiNonAmmessi;
        for (String a : ASSI) {
            boolean asse5 = a.startsWith("5");
            boolean soloAgricoli = List.of("3", "5.1", "5.2").contains(a);
            if (rapporto.isEmpty() || rapporto.equals("Nessuno")) {
                no.put(a, "nessun rapporto assicurativo con INAIL o INPS gestione agricola (R-DEST-12)");
            } else if (sogg.equals("ETS iscritto al RUNTS") && !a.equals("1.1")) {
                no.put(a, "gli ETS iscritti al RUNTS accedono solo all'Asse 1.1 tipologia d (R-DEST-02)");
            } else if (agricola && mp && !soloAgricoli) {
                no.put(a, "le micro e piccole imprese agricole primarie accedono solo agli Assi 3 e 5 (R-DEST-03)");
            } else if (settore4 && mp && List.of("2", "5.1", "5.2").contains(a)) {
                no.put(a, "le micro e piccole imprese dei settori dell'Asse 4 sono escluse dagli Assi 2 e 5 (R-DEST-04)");
            } else if (a.equals("4") && !(mp && settore4)) {
                no.put(a, "riservato alle micro e piccole imprese con ATECO nella tabella dell'Asse 4 (R-DEST-05)");
            } else if (asse5 && !(agricola && mp)) {
                no.put(a, "riservato alle micro e piccole imprese agricole primarie (R-DEST-07)");
            } else if (asse5 && rapporto.equals("INAIL")) {
                no.put(a, "il rapporto assicurativo INAIL non è ammesso per l'Asse 5 (R-DEST-10)");
            } else if (rapporto.equals("INPS gestione agricola") && !ateco.startsWith("01.61")) {
                boolean agricolaPrimaria = agricola && mp && ateco.startsWith("01");
                if (agricolaPrimaria && !soloAgricoli) {
                    no.put(a, "con rapporto INPS agricolo le micro e piccole agricole primarie accedono solo agli Assi 3 e 5 (R-DEST-11)");
                } else if (!agricolaPrimaria && !List.of("1.1", "1.2", "2", "3").contains(a)) {
                    no.put(a, "con rapporto INPS agricolo sono ammessi solo gli Assi 1.1, 1.2, 2 e 3 (R-DEST-11)");
                }
            }
            if (no.containsKey(a)) continue;
            if (a.equals("1.2")) {
                if (dec(d.get("dipendentiImpresa"), ZERO).signum() <= 0 || dec(d.get("dipendentiUnita"), ZERO).signum() <= 0) {
                    no.put(a, "riservato alle imprese con dipendenti, anche nell'unità produttiva (R-REQ-04)");
                } else if (si(d.get("sgslUltimi3Anni"))) {
                    no.put(a, "l'impresa ha adottato un SGSL o MOG nei 3 anni precedenti (R-ALL-11)");
                } else if (si(d.get("concessione12Precedenti"))) {
                    no.put(a, "l'impresa ha avuto una concessione Asse 1.2 nelle 3 edizioni precedenti (R-REQ-14)");
                }
            }
        }
        for (String a : ASSI) if (!no.containsKey(a)) r.assiAmmessi.add(a);
    }

    // ------------------------------------------------------------------ punteggio

    static BigDecimal puntiDimensione(BigDecimal ula, Map<String, ?> d, Regole regole, Risultato r) {
        if (ula == null) return ZERO;
        for (JsonNode f : regole.nodo("dimensioni")) {
            BigDecimal da = Regole.numero(f.get("ulaDa"));
            BigDecimal a = Regole.numeroONull(f.get("ulaA"));
            if (ula.compareTo(da) > 0 && (a == null || ula.compareTo(a) <= 0)) {
                BigDecimal p = Regole.numero(f.get("punti"));
                BigDecimal limite = Regole.numeroONull(f.get("limite"));
                List<BigDecimal> valori = fatturatoBilancio(d);
                if (limite != null && !valori.isEmpty() && valori.stream().allMatch(v -> v.compareTo(limite) > 0)) {
                    BigDecimal ridotti = Regole.numero(f.get("puntiRidotti")); // R-ALL-02, un decimale (H2)
                    r.dettaglioPunteggio.add("Dimensioni: " + punti(ridotti) + " (" + ula.toPlainString()
                            + " ULA, fatturato o bilancio oltre " + euro(limite) + ": " + punti(p) + " × "
                            + numero(regole.parametro("riduzioneDimensione")) + ") [R-ALL-02]");
                    return ridotti;
                }
                r.dettaglioPunteggio.add("Dimensioni: " + punti(p) + " (" + ula.toPlainString() + " ULA) [9.4.2]");
                return p;
            }
        }
        return ZERO;
    }

    static BigDecimal puntiRischiosita(String asse, Map<String, ?> d, Regole regole, Risultato r) {
        if (asse.equals("4")) {
            String pref = prefissoPiuLungo(testo(d.get("ateco")), regole.nodo("asse4Ateco"));
            BigDecimal v = pref == null ? ZERO : regole.num("asse4Ateco", pref);
            r.dettaglioPunteggio.add("Rischiosità: " + punti(v) + " (ATECO " + testo(d.get("ateco")) + ", prefisso "
                    + (pref == null ? "None" : pref) + ") [9.4.7]");
            return v;
        }
        LocalDate iscr = data(d.get("dataIscrizioneCciaa"));
        if (iscr != null && !iscr.isBefore(LocalDate.parse(regole.testo("parametri", "dataIscrizioneRischiositaFissa")))) {
            BigDecimal v = regole.parametro("rischiositaFissa");
            r.dettaglioPunteggio.add("Rischiosità: " + numero(v) + " (iscritta alla CCIAA dal 31/12/2023) [9.4.3]");
            return v;
        }
        BigDecimal nonDefinita = regole.num("rischiositaNonDefinita");
        BigDecimal tasso = dec(d.get("tassoMedio"));
        if (tasso == null) {
            r.dettaglioPunteggio.add("Rischiosità: " + numero(nonDefinita) + " (tasso non definito) [9.4.3]");
            return nonDefinita;
        }
        for (JsonNode f : regole.nodo("fasceTasso")) {
            if (Regole.numero(f.get("da")).compareTo(tasso) <= 0 && tasso.compareTo(Regole.numero(f.get("a"))) <= 0) {
                BigDecimal v = Regole.numero(f.get("punti"));
                r.dettaglioPunteggio.add("Rischiosità: " + numero(v) + " (tasso " + punti(tasso) + " ‰) [9.4.3]");
                return v;
            }
        }
        r.dettaglioPunteggio.add("Rischiosità: " + numero(nonDefinita) + " (tasso " + punti(tasso) + " ‰ fuori tabella) [9.4.3]");
        return nonDefinita;
    }

    static BigDecimal puntiTipologia(String asse, Map<String, ?> d, Regole regole, Risultato r) {
        JsonNode tab = regole.nodo("tipologie", asse);
        if (asse.equals("4")) {
            List<String> scelte = new ArrayList<>();
            for (String k : List.of("a", "b", "c")) if (si(d.get("tipologia4" + k))) scelte.add(k);
            if (scelte.isEmpty()) {
                r.errore(Risultato.PROGETTO, "Scegli almeno una tipologia di intervento dell'Asse 4 (Scegli asse e progetto)");
                return ZERO;
            }
            String k = scelte.get(0);
            for (String c : scelte) {
                if (Regole.numero(tab.get(c).get(1)).compareTo(Regole.numero(tab.get(k).get(1))) > 0) k = c; // R-ALL-05
            }
            BigDecimal v = Regole.numero(tab.get(k).get(1));
            r.dettaglioPunteggio.add("Tipologia: " + numero(v) + " (tipologia " + k + ", la più alta tra "
                    + String.join(", ", scelte) + ") [R-ALL-05]");
            return v;
        }
        String k = codiceVoce(d.get("tipologia" + asse.replace(".", "")));
        if (k.isEmpty() || !tab.has(k)) {
            r.errore(Risultato.PROGETTO, "Scegli la tipologia di intervento dell'Asse " + asse + " (Scegli asse e progetto)");
            return ZERO;
        }
        if (testo(d.get("tipoSoggetto")).equals("ETS iscritto al RUNTS") && !(asse.equals("1.1") && k.equals("d"))) {
            r.errore(Risultato.PROGETTO, "Gli ETS iscritti al RUNTS possono scegliere solo l'Asse 1.1 tipologia d (R-DEST-02)");
        }
        BigDecimal v = Regole.numero(tab.get(k).get(1));
        r.dettaglioPunteggio.add("Tipologia: " + numero(v) + " (tipologia " + k + ") [9.4.6]");
        return v;
    }

    static BigDecimal puntiComuni(String asse, Map<String, ?> d, Regole regole, Risultato r, boolean scrivi) {
        BigDecimal tot = ZERO;
        String ps = testo(d.get("partiSociali"));
        if (ps.isEmpty()) ps = "Nessuna";
        BigDecimal v = regole.num("partiSociali", ps);
        tot = tot.add(v);
        if (scrivi && v.signum() != 0) r.dettaglioPunteggio.add("Parti sociali: " + numero(v) + " (" + ps + ") [9.4.4]");
        if (si(d.get("rls"))) {
            v = regole.num("rls", asse);
            tot = tot.add(v);
            if (scrivi) r.dettaglioPunteggio.add("RLS/RLST: " + numero(v) + " [9.4.4]");
        }
        boolean isoAggiuntivo = List.of("1.1", "4").contains(asse)
                && testo(d.get("aggiuntivo" + asse.replace(".", ""))).contains("45001");
        for (Iterator<Map.Entry<String, JsonNode>> it = regole.nodo("certificazioni").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode c = e.getValue();
            if (!si(d.get(e.getKey())) || !contiene(c.get("assi"), asse)) continue;
            if (e.getKey().equals("certIso45001") && isoAggiuntivo) {
                if (scrivi) {
                    r.avvisi.add("Certificazione ISO 45001: 0 punti, perché hai scelto l'intervento aggiuntivo "
                            + "«Adozione SGSL ISO 45001» (R-ALL-06)");
                }
                continue;
            }
            BigDecimal p = Regole.numero(c.get("punti"));
            tot = tot.add(p);
            if (scrivi) r.dettaglioPunteggio.add("Certificazione " + c.get("nome").asText() + ": " + numero(p) + " [9.4.5]");
        }
        return tot;
    }

    static BigDecimal puntiBonus(String asse, Map<String, ?> d, Regole regole, Risultato r) {
        if (!contiene(regole.nodo("bonusAtecoAssi"), asse)) return ZERO;
        String ateco = testo(d.get("ateco"));
        for (JsonNode p : regole.nodo("bonusAtecoRegionale", testo(d.get("regione")))) {
            if (ateco.startsWith(p.asText())) {
                BigDecimal v = regole.num("bonusAtecoPunti");
                r.dettaglioPunteggio.add("Bonus ATECO regionale: " + numero(v) + " [9.4.8]");
                return v;
            }
        }
        return ZERO;
    }

    static List<Bene> beniAsse5(Map<String, ?> d) {
        List<Bene> beni = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            if (i == 2 && !si(d.get("secondoBene"))) break;
            Bene b = new Bene();
            b.n = i;
            b.tipo = testo(d.get("bene" + i + "Tipo"));
            b.misura = codiceVoce(d.get("bene" + i + "Misura"));
            b.fattore = codiceVoce(d.get("bene" + i + "Fattore"));
            b.preventivo = dec(d.get("bene" + i + "Preventivo"), ZERO);
            b.listino = dec(d.get("bene" + i + "Listino"));
            b.modalita = testo(d.get("bene" + i + "Modalita"));
            b.ricavoPermuta = dec(d.get("bene" + i + "RicavoPermuta"), ZERO);
            b.annoSostituito = dec(d.get("bene" + i + "AnnoSostituito"));
            b.potenzaKw = dec(d.get("bene" + i + "PotenzaKw"));
            beni.add(b);
        }
        return beni;
    }

    private static boolean combinazioneAmmessa(Regole regole, String a, String b) {
        for (JsonNode c : regole.nodo("combinazioniBeniAsse5")) {
            String x = c.get(0).asText(), y = c.get(1).asText();
            if ((x.equals(a) && y.equals(b)) || (x.equals(b) && y.equals(a))) return true;
        }
        return false;
    }

    static BigDecimal punteggioAsse5(String asse, Map<String, ?> d, Regole regole, Risultato r) {
        BigDecimal soglia = regole.soglia();
        BigDecimal comuni = puntiComuni(asse, d, regole, r, false);
        List<Bene> beni = beniAsse5(d);
        if (beni.size() == 2 && !combinazioneAmmessa(regole, beni.get(0).tipo, beni.get(1).tipo)) {
            r.errore(Risultato.PROGETTO, "Combinazione di beni non ammessa: " + beni.get(0).tipo + " + " + beni.get(1).tipo + " (R-ALL-07)");
        }
        BigDecimal migliore = ZERO;
        for (Bene b : beni) {
            JsonNode m = regole.nodo("misureAsse5", b.misura);
            JsonNode f = regole.nodo("fattoriAsse5", b.fattore);
            if (b.misura.isEmpty() || b.fattore.isEmpty() || m.isMissingNode() || f.isMissingNode()) {
                r.errore(Risultato.PROGETTO, "Bene " + b.n + ": scegli misura e fattore di rischio (R-ALL-07)");
                continue;
            }
            if (b.tipo.equals("Mietitrebbiatrice") && contiene(regole.nodo("fattoriNonMietitrebbiatrice"), b.fattore)) {
                r.errore(Risultato.PROGETTO, "Bene " + b.n + ": per una mietitrebbiatrice i fattori 2c e 2d non sono ammessi (R-ALL-08)");
            }
            BigDecimal pm = Regole.numero(m.get(1)), pf = Regole.numero(f.get(1));
            BigDecimal tot = pm.add(pf).add(comuni);
            b.punteggio = tot;
            b.ammesso = tot.compareTo(soglia) >= 0;
            r.punteggioBeni.add(tot);
            r.dettaglioPunteggio.add("Bene " + b.n + " (" + (b.tipo.isEmpty() ? "tipo non indicato" : b.tipo) + "): misura "
                    + b.misura + " " + numero(pm) + " + fattore " + b.fattore + " " + numero(pf) + " + criteri comuni "
                    + punti(comuni) + " = " + punti(tot) + " → " + (b.ammesso ? "ammesso" : "sotto soglia") + " [9.4.1]");
            if (tot.compareTo(migliore) > 0) migliore = tot;
        }
        r.beni = beni;
        return migliore;
    }

    // ------------------------------------------------------------------ importi

    private static BigDecimal[] macchine(Map<String, ?> d, Regole regole, Risultato r) {
        BigDecimal tot = ZERO;
        int n = 0;
        for (int i = 1; i <= 2; i++) {
            if (i == 2 && !si(d.get("secondaMacchina"))) break;
            BigDecimal prev = dec(d.get("macchina" + i + "Preventivo"));
            BigDecimal lis = dec(d.get("macchina" + i + "Listino"));
            if (prev == null || lis == null) continue;
            n++;
            BigDecimal base = lis;
            if (si(d.get("macchina" + i + "Trattore"))) {
                BigDecimal acc = dec(d.get("macchina" + i + "ListinoAccessori"), ZERO);
                BigDecimal accMax = centesimi(lis.multiply(regole.parametro("accessoriTrattorePercentuale")));
                if (acc.compareTo(accMax) > 0) {
                    r.avvisi.add("Macchina " + i + ": listino accessori ridotto a " + euro(accMax) + ", 30% del trattore base (R-CALC-03)");
                    acc = accMax;
                }
                base = lis.add(acc);
            }
            BigDecimal tetto = centesimi(base.multiply(regole.parametro("macchinaPercentualeListino")));
            BigDecimal amm = min(prev, tetto); // R-CALC-02
            if (amm.compareTo(prev) < 0) {
                r.avvisi.add("Macchina " + i + ": spesa ridotta a " + euro(amm) + ", l'80% del listino (R-CALC-02)");
            }
            r.dettaglioImporti.add("Macchina " + i + ": min(preventivo " + euro(prev) + "; 80% listino " + euro(tetto)
                    + ") = " + euro(amm) + " [R-CALC-02]");
            tot = tot.add(amm);
        }
        return new BigDecimal[] {tot, BigDecimal.valueOf(n)};
    }

    /** Spese di progetto e se sono solo macchine (per il tetto delle spese tecniche). */
    private record Spese(BigDecimal totale, boolean soloMacchine) {}

    private static Spese speseProgetto(String asse, Map<String, ?> d, Regole regole, Risultato r) {
        BigDecimal altre = dec(d.get("speseProgetto"), ZERO);
        BigDecimal[] m = List.of("1.1", "2", "4").contains(asse) ? macchine(d, regole, r) : new BigDecimal[] {ZERO, ZERO};
        boolean soloMacchine = m[1].signum() > 0 && altre.signum() == 0;
        BigDecimal tot = altre.add(m[0]);
        if (asse.equals("3")) {
            BigDecimal euroMqCop = regole.parametro("asse3CoperturaEuroMq");
            BigDecimal euroMqCon = regole.parametro("asse3ControsoffittoEuroMq");
            BigDecimal cop = dec(d.get("speseCopertura"), ZERO);
            BigDecimal copMax = dec(d.get("mqCopertura"), ZERO).multiply(euroMqCop);
            if (cop.compareTo(copMax) > 0) {
                r.avvisi.add("Spese per la copertura ridotte a " + euro(copMax) + ": massimo " + numero(euroMqCop) + " €/mq (R-ALL-09)");
                cop = copMax;
            }
            BigDecimal con = dec(d.get("speseControsoffitto"), ZERO);
            BigDecimal conMax = dec(d.get("mqControsoffitto"), ZERO).multiply(euroMqCon);
            if (con.compareTo(conMax) > 0) {
                r.avvisi.add("Spese per il controsoffitto ridotte a " + euro(conMax) + ": massimo " + numero(euroMqCon) + " €/mq (R-ALL-09)");
                con = conMax;
            }
            BigDecimal acc = dec(d.get("speseAccessorie"), ZERO);
            BigDecimal accMax = centesimi(cop.multiply(regole.parametro("asse3AccessoriePercentuale")));
            if (acc.compareTo(accMax) > 0) {
                r.avvisi.add("Spese per parapetti, ancoraggi e scale ridotte a " + euro(accMax) + ": 10% della copertura (R-ALL-09)");
                acc = accMax;
            }
            if (!zero(cop) || !zero(con) || !zero(acc)) {
                r.dettaglioImporti.add("Asse 3: copertura " + euro(cop) + " + controsoffitto " + euro(con) + " + accessorie "
                        + euro(acc) + " [R-ALL-09]");
            }
            tot = tot.add(cop).add(con).add(acc);
        }
        return new Spese(tot, soloMacchine);
    }

    private static BigDecimal speseTecniche(String asse, BigDecimal base, boolean soloMacchine, Map<String, ?> d,
                                            Regole regole, Risultato r) {
        BigDecimal tec = dec(d.get("speseTecniche"), ZERO);
        BigDecimal per = dec(d.get("costoPerizia"), ZERO);
        BigDecimal periziaMassimo = regole.parametro("periziaMassimo");
        if (per.compareTo(periziaMassimo) > 0) {
            r.avvisi.add("Costo della perizia ridotto a " + euro(periziaMassimo) + ": massimo previsto (R-ALL-03)");
            per = periziaMassimo;
        }
        if (asse.equals("1.2")) {
            if (!zero(tec) || !zero(per)) r.avvisi.add("Asse 1.2: le spese tecniche non sono ammesse (R-ALL-03)");
            return ZERO;
        }
        if (asse.startsWith("5")) {
            if (!zero(tec)) r.avvisi.add("Asse 5: l'unica spesa tecnica ammessa è la perizia (R-ALL-03)");
            return per;
        }
        BigDecimal perc, mass;
        if (soloMacchine && List.of("1.1", "2", "4").contains(asse)) {
            perc = regole.parametro("speseTecnicheSoloMacchinePercentuale");
            mass = regole.parametro("speseTecnicheSoloMacchineMassimo");
        } else {
            perc = regole.parametro("speseTecnichePercentuale");
            mass = regole.parametro("speseTecnicheMassimo");
        }
        BigDecimal richieste = tec.add(per);
        BigDecimal tetto = min(centesimi(base.multiply(perc)), mass);
        if (richieste.compareTo(tetto) > 0) {
            r.avvisi.add("Spese tecniche ridotte a " + euro(tetto) + ": massimo " + percento(perc)
                    + "% delle spese di progetto e non oltre " + euro(mass) + " (R-ALL-03)");
            return tetto;
        }
        return richieste;
    }

    /** Valore parametrico dell'Asse 1.2 (R-ALL-10): complessità dal macrosettore, fascia dai dipendenti. */
    private static Object[] valoreParametrico(Map<String, ?> d, Regole regole) {
        String ateco = testo(d.get("ateco"));
        String cod = sezioneAteco(ateco, regole) + ateco; // es. "C16.23"
        String compl = "";
        int lungh = -1;
        for (Iterator<Map.Entry<String, JsonNode>> it = regole.nodo("macrosettori12").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            for (JsonNode c : e.getValue()) {
                String s = c.asText();
                if (cod.startsWith(s) && s.length() > lungh) {
                    compl = e.getKey();
                    lungh = s.length();
                }
            }
        }
        if (compl.isEmpty()) compl = "BASSA";
        BigDecimal dip = dec(d.get("dipendentiImpresa"), ZERO);
        for (JsonNode f : regole.nodo("valoreParametrico12")) {
            BigDecimal a = Regole.numeroONull(f.get("a"));
            if (dip.compareTo(Regole.numero(f.get("da"))) >= 0 && (a == null || dip.compareTo(a) <= 0)) {
                return new Object[] {Regole.numero(f.get(compl)), compl};
            }
        }
        return new Object[] {null, compl};
    }

    static void importi(String asse, Map<String, ?> d, Regole regole, Risultato r) {
        JsonNode info = regole.nodo("assi", asse);
        BigDecimal perc = Regole.numero(info.get("percentuale"));
        BigDecimal massimo = regole.parametro("contributoMassimo");
        BigDecimal spesaAmm;
        BigDecimal contributo;

        if (asse.startsWith("5")) {
            if (asse.equals("5.2") && !(si(d.get("giovaneEta")) && si(d.get("giovaneFormazione")) && si(d.get("giovaneCapoAzienda")))) {
                perc = Regole.numero(info.get("percentualeSenzaRequisiti"));
                r.avvisi.add("Asse 5.2: manca almeno un requisito del giovane agricoltore, il contributo è al 65% (R-CALC-06)");
            }
            BigDecimal quota = perc.compareTo(new BigDecimal("0.80")) == 0
                    ? regole.parametro("asse5QuotaImpresaGiovani") : regole.parametro("asse5QuotaImpresa");
            BigDecimal spesaTot = ZERO;
            contributo = ZERO;
            for (Bene b : r.beni) {
                if (!b.ammesso) {
                    r.dettaglioImporti.add("Bene " + b.n + ": escluso dal contributo, punteggio sotto soglia [RF-13 CA-02]");
                    continue;
                }
                BigDecimal spesa = b.preventivo;
                if (b.listino != null) {
                    spesa = min(spesa, centesimi(b.listino.multiply(regole.parametro("macchinaPercentualeListino"))));
                }
                BigDecimal c = centesimi(spesa.multiply(perc));
                StringBuilder riga = new StringBuilder("Bene " + b.n + ": spesa " + euro(spesa) + " × " + percento(perc)
                        + "% = " + euro(c));
                if (b.modalita.equals("Permuta")) {
                    BigDecimal carico = centesimi(spesa.multiply(quota));
                    if (b.ricavoPermuta.compareTo(carico) > 0) {
                        BigDecimal rid = b.ricavoPermuta.subtract(carico);
                        c = c.subtract(rid);
                        riga.append(", ridotto di ").append(euro(rid)).append(" perché la permuta (")
                                .append(euro(b.ricavoPermuta)).append(") supera la quota a carico (")
                                .append(euro(carico)).append(") [R-CALC-07]");
                    }
                }
                r.dettaglioImporti.add(riga + " = " + euro(c));
                spesaTot = spesaTot.add(spesa);
                contributo = contributo.add(c);
            }
            BigDecimal tec = speseTecniche(asse, spesaTot, false, d, regole, r);
            if (!zero(tec)) {
                BigDecimal cTec = centesimi(tec.multiply(perc));
                r.dettaglioImporti.add("Perizia: " + euro(tec) + " × " + percento(perc) + "% = " + euro(cTec));
                contributo = contributo.add(cTec);
            }
            spesaAmm = spesaTot.add(tec);
        } else {
            Spese s = speseProgetto(asse, d, regole, r);
            BigDecimal base = s.totale();
            if (asse.equals("1.2")) {
                Object[] vp = valoreParametrico(d, regole);
                if (vp[0] != null) {
                    BigDecimal v = (BigDecimal) vp[0];
                    BigDecimal tetto = v.multiply(regole.parametro("asse12ConsulenzaEuro"))
                            .add(v.multiply(regole.parametro("asse12CertificazioneEuro")));
                    r.dettaglioImporti.add("Asse 1.2: valore parametrico " + punti(v) + " (complessità " + vp[1]
                            + ") → spesa massima " + euro(tetto) + " [R-ALL-10]");
                    if (base.compareTo(tetto) > 0) {
                        r.avvisi.add("Spese di progetto ridotte a " + euro(tetto) + ": massimo dell'Asse 1.2 (R-ALL-10)");
                        base = tetto;
                    }
                }
            }
            BigDecimal tec = speseTecniche(asse, base, s.soloMacchine(), d, regole, r);
            spesaAmm = centesimi(base.add(tec)); // R-CALC-01
            r.dettaglioImporti.add("Spesa ammissibile: progetto " + euro(base) + " + spese tecniche " + euro(tec) + " = "
                    + euro(spesaAmm) + " [R-CALC-01]");
            contributo = centesimi(spesaAmm.multiply(perc));
            r.dettaglioImporti.add("Contributo principale: " + euro(spesaAmm) + " × " + percento(perc) + "% = "
                    + euro(contributo) + " [R-CALC-04]");
        }

        if (contributo.compareTo(massimo) > 0) {
            r.avvisi.add("Contributo ridotto a " + euro(massimo) + ": massimo previsto dall'Avviso (R-CALC-04)");
            contributo = massimo;
        }
        boolean minimoOk = contributo.compareTo(regole.parametro("contributoMinimo")) >= 0;
        if (asse.equals("1.2") && dec(d.get("dipendentiImpresa"), ZERO).compareTo(regole.parametro("dipendentiSenzaMinimoAsse12")) < 0) {
            minimoOk = true; // H1: meno di 50 dipendenti, nessun minimo
        }
        if (!minimoOk) {
            r.errore(Risultato.PUNTEGGIO, "Contributo " + euro(contributo) + " inferiore al minimo di " + euro(regole.parametro("contributoMinimo")) + " (R-CALC-04)");
        }

        // intervento aggiuntivo (R-CALC-05)
        BigDecimal agg = ZERO;
        String scelta = testo(d.get("aggiuntivo" + asse.replace(".", "")));
        if (!scelta.isEmpty() && !scelta.equals("Nessuno") && !info.path("aggiuntivi").isEmpty()) {
            BigDecimal spesaAgg = dec(d.get("speseAggiuntivo"), ZERO);
            agg = centesimi(spesaAgg.multiply(regole.parametro("aggiuntivoPercentuale")));
            BigDecimal tetto = min(regole.parametro("aggiuntivoMassimo"), massimo.subtract(contributo), contributo);
            if (agg.compareTo(tetto) > 0) {
                r.avvisi.add("Contributo aggiuntivo ridotto a " + euro(tetto) + " (R-CALC-05)");
                agg = tetto;
            }
            r.dettaglioImporti.add("Contributo aggiuntivo (" + scelta + "): " + euro(spesaAgg) + " × 80% → " + euro(agg) + " [R-CALC-05]");
        }
        BigDecimal totale = contributo.add(agg);

        // anticipo e fideiussione (R-CALC-08, R-CALC-09)
        BigDecimal anticipo = ZERO;
        if (si(d.get("anticipo"))) {
            if (si(d.get("noleggioPattoAcquisto"))) {
                r.avvisi.add("Con il noleggio con patto d'acquisto l'anticipo non è richiedibile (R-CALC-08)");
            } else if (r.microPiccola && info.path("anticipo70").asBoolean()) {
                anticipo = centesimi(totale.multiply(regole.parametro("anticipoPercentualeMicroPiccole")));
            } else if (totale.compareTo(regole.parametro("anticipoSoglia")) >= 0) {
                anticipo = centesimi(totale.multiply(regole.parametro("anticipoPercentuale")));
            } else {
                r.avvisi.add("Anticipo non spettante: contributo inferiore a " + euro(regole.parametro("anticipoSoglia")) + " (R-CALC-08)");
            }
        }
        BigDecimal fide = centesimi(anticipo.multiply(regole.parametro("fideiussioneMoltiplicatore")));
        if (!zero(anticipo)) {
            r.dettaglioImporti.add("Anticipo " + euro(anticipo) + ", fideiussione " + euro(fide) + " [R-CALC-08, R-CALC-09]");
        }
        r.importi.put("spesaAmmissibile", spesaAmm);
        r.importi.put("contributoPrincipale", contributo);
        r.importi.put("contributoAggiuntivo", agg);
        r.importi.put("contributoTotale", totale);
        r.importi.put("anticipo", anticipo);
        r.importi.put("fideiussione", fide);
    }

    // ------------------------------------------------------------------ controlli, dichiarazioni, documenti

    static LocalDate giorniLavorativiPrima(LocalDate fine, int n, Set<String> festivita) {
        LocalDate g = fine;
        int contati = 0;
        while (contati < n) {
            g = g.minusDays(1);
            if (g.getDayOfWeek() != DayOfWeek.SATURDAY && g.getDayOfWeek() != DayOfWeek.SUNDAY
                    && !festivita.contains(g.toString())) {
                contati++;
            }
        }
        return g;
    }

    /** Requisiti dell'impresa, controllati anche prima che sia scelto l'asse (passo «Controlla i dati dell'impresa»). */
    static void controlliImpresa(Map<String, ?> d, Regole regole, Risultato r) {
        // RF-03: associazione al portale almeno 5 giorni lavorativi prima della chiusura
        LocalDate chiusura = OffsetDateTime.parse(regole.testo("calendario", "chiusuraCompilazione")).toLocalDate();
        Set<String> festivita = new HashSet<>();
        regole.nodo("festivita").forEach(f -> festivita.add(f.asText()));
        LocalDate limite = giorniLavorativiPrima(chiusura, regole.parametro("giorniLavorativiAssociazionePortale").intValue(), festivita);
        LocalDate ass = data(d.get("dataAssociazionePortale"));
        if (ass != null && ass.isAfter(limite)) {
            r.errore(Risultato.IMPRESA, "Registrazione al portale avvenuta oltre il termine previsto dall'art. 12 dell'Avviso");
        }
        // forza lavoro minima (R-REQ-01)
        if (r.ula != null && r.ula.compareTo(BigDecimal.ONE) < 0 && !si(d.get("dittaSenzaDipendenti"))
                && !si(d.get("attivitaStagionale"))) {
            r.errore(Risultato.IMPRESA, "Forza lavoro inferiore a 1 ULA (R-REQ-01)");
        }
        if (r.assiAmmessi.isEmpty()) {
            r.errore(Risultato.IMPRESA, "Con questi dati nessun asse è ammesso per l'impresa: controlla dimensione, ATECO e rapporto assicurativo");
        }
    }

    static void controlliRegistrazione(String asse, Map<String, ?> d, Regole regole, Risultato r) {
        // dichiarazioni (RF-16)
        boolean agricola2135 = si(d.get("agricolaPrimaria"));
        boolean sostituzione = !zero(dec(d.get("macchina1AnnoSostituito")));
        for (int i = 1; i <= 2; i++) {
            if (List.of("Rottamazione", "Permuta").contains(testo(d.get("bene" + i + "Modalita")))) sostituzione = true;
        }
        for (JsonNode x : regole.nodo("dichiarazioni")) {
            JsonNode assi = x.get("assi");
            if (!(assi.isTextual() && assi.asText().equals("tutti")) && !contiene(assi, asse)) continue;
            if (x.path("soloConSostituzione").asBoolean() && !sostituzione) continue;
            if (x.path("nonAgricole2135").asBoolean() && agricola2135) continue;
            if (!si(d.get(x.get("campo").asText()))) {
                r.errore(Risultato.DICHIARAZIONI, "Manca la dichiarazione " + x.get("codice").asText() + " (Firma le dichiarazioni)");
            }
        }
        String prec = testo(d.get("finanziamentiPrecedenti"));
        if (!prec.isEmpty() && !prec.equals("Nessuno") && !asse.equals("1.2")
                && !codiceAsse(d.get("assePrecedente")).equals("1.2") && !si(d.get("rinunciaPec"))) {
            r.errore(Risultato.DICHIARAZIONI, "Finanziamento ISI precedente: serve la rinuncia via PEC (R-REQ-13)");
        }
        if (!si(d.get("nessunaSpesaNonAmmessa"))) {
            r.errore(Risultato.PUNTEGGIO, "Conferma che il piano economico non contiene spese non ammesse (Criteri di punteggio e piano economico)");
        }
        // 9.6: beni sostituiti
        for (Bene b : r.beni) {
            if (b.tipo.equals("Trattore") && b.annoSostituito != null) {
                if (b.annoSostituito.compareTo(BigDecimal.valueOf(2013)) >= 0) {
                    r.errore(Risultato.PROGETTO, "Bene " + b.n + ": trattori immessi sul mercato dal 2013 non sono sostituibili (9.6)");
                } else if (b.annoSostituito.compareTo(BigDecimal.valueOf(2005)) < 0 && b.modalita.equals("Permuta")) {
                    r.errore(Risultato.PROGETTO, "Bene " + b.n + ": trattori immessi prima del 2005 solo con rottamazione (9.6)");
                }
            }
            if (b.tipo.equals("Trattore") && b.potenzaKw != null && b.potenzaKw.compareTo(BigDecimal.valueOf(130)) > 0) {
                r.errore(Risultato.PROGETTO, "Bene " + b.n + ": potenza massima 130 kW (9.6)");
            }
        }
        if (asse.equals("4") && testo(d.get("macchina1Modalita")).equals("Permuta")) {
            r.errore(Risultato.PROGETTO, "Asse 4: il bene sostituito va rottamato, la permuta non è ammessa (9.6)");
        }
    }

    static List<String> documentiRichiesti(String asse, Map<String, ?> d, Regole regole) {
        boolean ets = testo(d.get("tipoSoggetto")).startsWith("ETS");
        boolean asse5 = asse.startsWith("5");
        String ps = testo(d.get("partiSociali"));
        Map<String, Boolean> cond = Map.ofEntries(
                Map.entry("sempre", true),
                Map.entry("perizia", !asse.equals("1.2")),
                Map.entry("asse12", asse.equals("1.2")),
                Map.entry("nonEts", !ets),
                Map.entry("ets", ets),
                Map.entry("asse5", asse5),
                Map.entry("partiSociali", !ps.isEmpty() && !ps.equals("Nessuna")),
                Map.entry("rls", si(d.get("rls"))),
                Map.entry("dpi", asse.equals("2") && testo(d.get("aggiuntivo2")).contains("DPI")),
                Map.entry("macchine", !zero(dec(d.get("macchina1Preventivo"))) || asse5),
                Map.entry("catastrofale", !si(d.get("agricolaPrimaria"))));
        List<String> out = new ArrayList<>();
        for (JsonNode x : regole.nodo("documenti")) {
            if (cond.getOrDefault(x.get("quando").asText(), false)) out.add(x.get("nome").asText());
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = regole.nodo("certificazioni").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            if (si(d.get(e.getKey())) && contiene(e.getValue().get("assi"), asse)) {
                out.add(e.getValue().get("documento").asText());
            }
        }
        return out;
    }

    private static boolean contiene(JsonNode lista, String valore) {
        if (lista == null || !lista.isArray()) return false;
        for (JsonNode x : lista) if (x.asText().equals(valore)) return true;
        return false;
    }
}
