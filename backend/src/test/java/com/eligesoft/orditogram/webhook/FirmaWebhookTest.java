package com.eligesoft.orditogram.webhook;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class FirmaWebhookTest {

    @Test
    void firmaVerificabileDalRicevente() {
        String segreto = FirmaWebhook.nuovoSegreto();
        String corpo = "{\"tipo\":\"pratica.avviata\",\"pratica\":{\"titolo\":\"Città\"}}";
        String firma = FirmaWebhook.firma(segreto, 1_759_600_000L, corpo);
        assertTrue(firma.matches("t=1759600000,v1=[0-9a-f]{64}"), firma);
        assertTrue(FirmaWebhook.verifica(segreto, firma, corpo, 1_759_600_100L, 300));
    }

    @Test
    void firmaRifiutataSeCambiaQualcosa() {
        String segreto = "whsec_prova";
        String corpo = "{\"a\":1}";
        String firma = FirmaWebhook.firma(segreto, 1000, corpo);
        assertFalse(FirmaWebhook.verifica(segreto, firma, "{\"a\":2}", 1000, 300), "corpo modificato");
        assertFalse(FirmaWebhook.verifica("altro", firma, corpo, 1000, 300), "segreto diverso");
        assertFalse(FirmaWebhook.verifica(segreto, firma, corpo, 1000 + 301, 300), "troppo vecchia");
        assertFalse(FirmaWebhook.verifica(segreto, "t=abc,v1=00", corpo, 1000, 300), "malformata");
    }

    @Test
    void valoreNotoHmacSha256() {
        // RFC 4231, caso 2: chiave "Jefe", dati "what do ya want for nothing?"
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            FirmaWebhook.hmac("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void attesaCrescenteEPoiBasta() {
        assertEquals(Duration.ofMinutes(1), FirmaWebhook.attesaDopo(1));
        assertEquals(Duration.ofMinutes(5), FirmaWebhook.attesaDopo(2));
        assertEquals(Duration.ofHours(24), FirmaWebhook.attesaDopo(FirmaWebhook.MAX_TENTATIVI - 1));
        assertNull(FirmaWebhook.attesaDopo(FirmaWebhook.MAX_TENTATIVI));
        assertNull(FirmaWebhook.attesaDopo(0));
    }
}
