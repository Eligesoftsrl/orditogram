package com.eligesoft.orditogram.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CifraturaTest {

    @Test
    void cifraEDecifra() {
        Cifratura c = new Cifratura("una frase lunga e casuale");
        String cifrato = c.cifra("whsec_segreto");
        assertTrue(cifrato.startsWith("enc:v1:"));
        assertNotEquals(cifrato, c.cifra("whsec_segreto"), "IV casuale: ogni cifratura è diversa");
        assertEquals("whsec_segreto", c.decifra(cifrato));
    }

    @Test
    void valoriInChiaroRestanoLeggibili() {
        assertEquals("whsec_vecchio", new Cifratura("x").decifra("whsec_vecchio"));
        Cifratura senza = new Cifratura("");
        assertFalse(senza.attiva());
        assertEquals("in chiaro", senza.cifra("in chiaro"));
    }

    @Test
    void chiaveSbagliataNonDecifra() {
        String cifrato = new Cifratura("giusta").cifra("segreto");
        assertThrows(IllegalStateException.class, () -> new Cifratura("sbagliata").decifra(cifrato));
        assertThrows(IllegalStateException.class, () -> new Cifratura("").decifra(cifrato));
    }
}
