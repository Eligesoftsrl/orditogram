package com.eligesoft.orditogram.connettore;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestiTest {

    @Test
    void sostituisceIDati() {
        assertEquals("Pratica 42 di Mario", Testi.sostituisci("Pratica ${n} di ${nome}", Map.of("n", 42, "nome", "Mario")));
        assertEquals("manca: ", Testi.sostituisci("manca: ${x}", Map.of()));
        assertEquals("$ e \\\\ restano", Testi.sostituisci("${v} restano", Map.of("v", "$ e \\\\")));
    }

    @Test
    void escapeJson() {
        assertEquals("a \\\"b\\\"\\nc\\\\", Testi.escapeJson("a \"b\"\nc\\"));
        assertEquals("{\"t\": \"x\\\"y\"}", Testi.sostituisci("{\"t\": \"${v}\"}", Map.of("v", "x\"y"), Testi::escapeJson));
    }

    @Test
    void dateVecchieInIso() {
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        java.util.Date mezzanotte = java.util.Date.from(java.time.LocalDate.of(2010, 3, 1).atStartOfDay(z).toInstant());
        assertEquals("costituita il 2010-03-01", Testi.sostituisci("costituita il ${d}", Map.of("d", mezzanotte)));
        java.util.Date conOra = java.util.Date.from(java.time.LocalDateTime.of(2027, 3, 31, 18, 0).atZone(z).toInstant());
        assertEquals(java.time.LocalDateTime.of(2027, 3, 31, 18, 0).atZone(z).toOffsetDateTime().toString(), Testi.leggibile(conOra));
        assertEquals("testo", Testi.leggibile("testo"));
    }
}
