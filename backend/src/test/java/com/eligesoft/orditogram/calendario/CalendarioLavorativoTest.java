package com.eligesoft.orditogram.calendario;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CalendarioLavorativoTest {

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    private final CalendarioLavorativo cal = CalendarioLavorativo.da(List.of("sabato", "DOMENICA"), true,
        List.of("06-29 Santi Pietro e Paolo", "2026-12-07 Ponte"));

    private static ZonedDateTime il(int a, int m, int g, int ora) {
        return ZonedDateTime.of(a, m, g, ora, 0, 0, 0, ROMA);
    }

    @Test
    void pasqua() {
        assertEquals(LocalDate.of(2024, 3, 31), CalendarioLavorativo.pasqua(2024));
        assertEquals(LocalDate.of(2025, 4, 20), CalendarioLavorativo.pasqua(2025));
        assertEquals(LocalDate.of(2026, 4, 5), CalendarioLavorativo.pasqua(2026));
        assertEquals(LocalDate.of(2038, 4, 25), CalendarioLavorativo.pasqua(2038));
    }

    @Test
    void festivita() {
        assertEquals(14, cal.festivita(2026).size());
        assertFalse(cal.lavorativo(LocalDate.of(2026, 4, 6)), "Lunedì dell'Angelo");
        assertEquals(Optional.of("Santi Pietro e Paolo"), cal.festivita(LocalDate.of(2027, 6, 29)));
        assertTrue(cal.lavorativo(LocalDate.of(2026, 10, 7)));
    }

    @Test
    void giorniLavorativi() {
        // ven 4/12 + 3: lun 7 ponte, mar 8 Immacolata -> mer 9, gio 10, ven 11
        assertEquals(il(2026, 12, 11, 10), cal.aggiungiGiorniLavorativi(il(2026, 12, 4, 10), 3));
        assertEquals(LocalDate.of(2026, 11, 12), cal.aggiungiGiorniLavorativi(il(2026, 10, 1, 9), 30).toLocalDate());
        // il cambio dell'ora non sposta l'orario
        assertEquals(9, cal.aggiungiGiorniLavorativi(il(2026, 10, 23, 9), 3).getHour());
    }

    @Test
    void proroga() {
        // 6/11 + 30 = domenica 6/12 -> 7 ponte, 8 festa -> 9/12
        assertEquals(LocalDate.of(2026, 12, 9), cal.proroga(il(2026, 11, 6, 9).plusDays(30)).toLocalDate());
        assertEquals(LocalDate.of(2026, 10, 7), cal.proroga(il(2026, 10, 7, 9)).toLocalDate());
    }

    @Test
    void configurazioneErrata() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> CalendarioLavorativo.da(List.of(), true, List.of("31/12")));
        assertTrue(e.getMessage().contains("31/12"));
    }
}
