package com.eligesoft.orditogram.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LimiteRichiesteTest {

    @Test
    void oltreIlLimiteSiAspettaFinoAlMinutoSuccessivo() {
        LimiteRichieste l = new LimiteRichieste(3);
        long t = 60_000L * 16_667 + 20_000;   // 20 secondi dentro un minuto
        assertEquals(0, l.consuma("portale", t));
        assertEquals(0, l.consuma("portale", t));
        assertEquals(0, l.consuma("portale", t));
        assertEquals(40, l.consuma("portale", t));
        assertEquals(0, l.consuma("altro", t), "ogni sistema ha il suo conteggio");
        assertEquals(0, l.consuma("portale", t + 60_000), "nuovo minuto");
    }

    @Test
    void zeroSignificaNessunLimite() {
        LimiteRichieste l = new LimiteRichieste(0);
        for (int i = 0; i < 1000; i++) assertEquals(0, l.consuma("x", 0));
    }
}
