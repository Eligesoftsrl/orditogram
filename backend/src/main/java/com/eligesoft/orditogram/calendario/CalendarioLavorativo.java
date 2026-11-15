package com.eligesoft.orditogram.calendario;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Calendario dei giorni lavorativi dell'ente: giorni della settimana non lavorativi (di norma sabato e domenica),
 * festività nazionali italiane (Pasqua e Lunedì dell'Angelo calcolati) e festività o chiusure dell'ente.
 * Nessuna dipendenza esterna: si prova da solo.
 */
public final class CalendarioLavorativo {

    public record Festivita(LocalDate data, String nome) {}

    private static final Map<MonthDay, String> NAZIONALI = new LinkedHashMap<>();
    static {
        NAZIONALI.put(MonthDay.of(1, 1), "Capodanno");
        NAZIONALI.put(MonthDay.of(1, 6), "Epifania");
        NAZIONALI.put(MonthDay.of(4, 25), "Festa della Liberazione");
        NAZIONALI.put(MonthDay.of(5, 1), "Festa del Lavoro");
        NAZIONALI.put(MonthDay.of(6, 2), "Festa della Repubblica");
        NAZIONALI.put(MonthDay.of(8, 15), "Ferragosto");
        NAZIONALI.put(MonthDay.of(11, 1), "Ognissanti");
        NAZIONALI.put(MonthDay.of(12, 8), "Immacolata Concezione");
        NAZIONALI.put(MonthDay.of(12, 25), "Natale");
        NAZIONALI.put(MonthDay.of(12, 26), "Santo Stefano");
    }

    private final Set<DayOfWeek> nonLavorativi;
    private final boolean nazionali;
    private final Map<MonthDay, String> ricorrenti;   // es. il santo patrono, ogni anno
    private final Map<LocalDate, String> date;        // chiusure di un anno preciso (ponti, eventi)

    public CalendarioLavorativo(Set<DayOfWeek> nonLavorativi, boolean nazionali,
                                Map<MonthDay, String> ricorrenti, Map<LocalDate, String> date) {
        this.nonLavorativi = nonLavorativi.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(nonLavorativi);
        this.nazionali = nazionali;
        this.ricorrenti = new LinkedHashMap<>(ricorrenti);
        this.date = new TreeMap<>(date);
    }

    /** Calendario standard: sabato e domenica non lavorativi, festività nazionali. */
    public static CalendarioLavorativo standard() {
        return new CalendarioLavorativo(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), true, Map.of(), Map.of());
    }

    /**
     * Da configurazione testuale. Festività dell'ente: "06-29 San Pietro e Paolo" (ogni anno)
     * oppure "2026-12-07 Chiusura uffici" (una sola volta). Il testo dopo la data è facoltativo.
     */
    public static CalendarioLavorativo da(Collection<String> giorniNonLavorativi, boolean nazionali, Collection<String> festivita) {
        Set<DayOfWeek> giorni = EnumSet.noneOf(DayOfWeek.class);
        for (String g : giorniNonLavorativi) giorni.add(giorno(g));
        Map<MonthDay, String> ric = new LinkedHashMap<>();
        Map<LocalDate, String> dt = new LinkedHashMap<>();
        for (String riga : festivita) {
            if (riga == null || riga.isBlank()) continue;
            String r = riga.trim();
            int sp = r.indexOf(' ');
            String data = sp < 0 ? r : r.substring(0, sp);
            String nome = sp < 0 ? "Festività dell'ente" : r.substring(sp + 1).trim();
            try {
                if (data.length() == 5) ric.put(MonthDay.parse("--" + data), nome);
                else dt.put(LocalDate.parse(data), nome);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Festività non valida: \"" + riga + "\" (usa MM-GG oppure AAAA-MM-GG)");
            }
        }
        return new CalendarioLavorativo(giorni, nazionali, ric, dt);
    }

    private static DayOfWeek giorno(String g) {
        String s = g.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "LUNEDI", "LUNEDÌ" -> DayOfWeek.MONDAY;
            case "MARTEDI", "MARTEDÌ" -> DayOfWeek.TUESDAY;
            case "MERCOLEDI", "MERCOLEDÌ" -> DayOfWeek.WEDNESDAY;
            case "GIOVEDI", "GIOVEDÌ" -> DayOfWeek.THURSDAY;
            case "VENERDI", "VENERDÌ" -> DayOfWeek.FRIDAY;
            case "SABATO" -> DayOfWeek.SATURDAY;
            case "DOMENICA" -> DayOfWeek.SUNDAY;
            default -> DayOfWeek.valueOf(s);
        };
    }

    // ------------------------------------------------------------ interrogazioni

    public Optional<String> festivita(LocalDate d) {
        if (date.containsKey(d)) return Optional.of(date.get(d));
        MonthDay md = MonthDay.from(d);
        if (ricorrenti.containsKey(md)) return Optional.of(ricorrenti.get(md));
        if (nazionali) {
            if (NAZIONALI.containsKey(md)) return Optional.of(NAZIONALI.get(md));
            LocalDate p = pasqua(d.getYear());
            if (d.equals(p)) return Optional.of("Pasqua");
            if (d.equals(p.plusDays(1))) return Optional.of("Lunedì dell'Angelo");
        }
        return Optional.empty();
    }

    public boolean lavorativo(LocalDate d) {
        return !nonLavorativi.contains(d.getDayOfWeek()) && festivita(d).isEmpty();
    }

    /** Festività dell'anno (non i fine settimana), in ordine di data. */
    public List<Festivita> festivita(int anno) {
        List<Festivita> out = new ArrayList<>();
        for (LocalDate d = LocalDate.of(anno, 1, 1); d.getYear() == anno; d = d.plusDays(1)) {
            LocalDate giorno = d;
            festivita(d).ifPresent(n -> out.add(new Festivita(giorno, n)));
        }
        return out;
    }

    // ------------------------------------------------------------ calcolo dei termini

    /** n giorni lavorativi dopo l'istante dato (stessa ora del giorno). Il giorno di partenza non si conta. */
    public ZonedDateTime aggiungiGiorniLavorativi(ZonedDateTime da, int n) {
        if (n <= 0) return proroga(da);
        ZonedDateTime t = da;
        int contati = 0;
        long sicurezza = 0, limite = (long) n * 7 + 3660;
        while (contati < n) {
            t = t.plusDays(1);
            if (lavorativo(t.toLocalDate())) contati++;
            if (++sicurezza > limite) throw new IllegalStateException("Calendario senza giorni lavorativi");
        }
        return t;
    }

    /**
     * Se il termine cade in un giorno non lavorativo slitta al primo giorno lavorativo successivo
     * (art. 2963 del Codice civile, art. 155 c.p.c.), alla stessa ora.
     */
    public ZonedDateTime proroga(ZonedDateTime t) {
        ZonedDateTime r = t;
        int sicurezza = 0;
        while (!lavorativo(r.toLocalDate())) {
            r = r.plusDays(1);
            if (++sicurezza > 3660) throw new IllegalStateException("Calendario senza giorni lavorativi");
        }
        return r;
    }

    /** Domenica di Pasqua (calendario gregoriano, algoritmo anonimo "Meeus/Jones/Butcher"). */
    public static LocalDate pasqua(int anno) {
        int a = anno % 19, b = anno / 100, c = anno % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mese = (h + l - 7 * m + 114) / 31, giorno = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(anno, mese, giorno);
    }
}
