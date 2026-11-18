package com.eligesoft.orditogram.storico;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndicatoriPraticheTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");

    @Test
    void avanzamentoStimatoNonArrivaAlCentoPerUnaPraticaInCorso() {
        assertEquals(0, IndicatoriPratiche.avanzamento(0, 4));
        assertEquals(50, IndicatoriPratiche.avanzamento(2, 4));
        assertEquals(95, IndicatoriPratiche.avanzamento(4, 4));
        assertEquals(0, IndicatoriPratiche.avanzamento(3, 0), "modello senza attività utente");
    }

    @Test
    void settimanaDiUnaData() {
        LocalDate prima = LocalDate.of(2026, 9, 28);   // lunedì
        assertEquals(0, IndicatoriPratiche.settimana(giorno(2026, 9, 28), prima, ROMA));
        assertEquals(0, IndicatoriPratiche.settimana(giorno(2026, 10, 4), prima, ROMA));
        assertEquals(1, IndicatoriPratiche.settimana(giorno(2026, 10, 5), prima, ROMA));
        assertEquals(7, IndicatoriPratiche.settimana(giorno(2026, 11, 22), prima, ROMA));
        assertEquals(-1, IndicatoriPratiche.settimana(giorno(2026, 11, 23), prima, ROMA), "oltre l'ultima settimana");
        assertEquals(-1, IndicatoriPratiche.settimana(giorno(2026, 9, 27), prima, ROMA), "prima della prima settimana");
        assertEquals(-1, IndicatoriPratiche.settimana(null, prima, ROMA));
    }

    private static Date giorno(int a, int m, int g) {
        return Date.from(LocalDate.of(a, m, g).atTime(12, 0).atZone(ROMA).toInstant());
    }
}
