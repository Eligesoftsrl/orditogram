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
}
