package com.eligesoft.orditogram.registro;

import com.eligesoft.orditogram.api.ErroriApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Regole del registro che non hanno bisogno dell'applicazione avviata. */
class RegistroUnitaTest {

    @Test
    void solo403e404SonoLettureNegate() {
        assertThat(AspettoLetture.negata(new AccessDeniedException("no"))).isTrue();
        assertThat(AspettoLetture.negata(new ResponseStatusException(HttpStatus.FORBIDDEN))).isTrue();
        assertThat(AspettoLetture.negata(new ResponseStatusException(HttpStatus.NOT_FOUND))).isTrue();
        assertThat(AspettoLetture.negata(new ErroriApi.ErroreApi(404, "non_trovata", "x"))).isTrue();
        assertThat(AspettoLetture.negata(new ResponseStatusException(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(AspettoLetture.negata(new IllegalStateException("guasto"))).isFalse();
    }

    @Test
    void codiciDiversiEMessaggiSuUnaRiga() {
        assertThat(RegistroErrori.nuovoCodice()).matches("E-[0-9A-F]{8}").isNotEqualTo(RegistroErrori.nuovoCodice());
        String m = RegistroErrori.messaggio(new IllegalStateException("fuori\nriga", new RuntimeException("causa")));
        assertThat(m).isEqualTo("IllegalStateException: fuori riga\nRuntimeException: causa");
    }

    @Test
    @SuppressWarnings("unchecked")
    void formatoDellApiPubblica() {
        assertThat(GestoreErroriImprevisti.corpo("/api/v1/pratiche", "E-1"))
            .containsOnlyKeys("errore");
        assertThat((Map<String, Object>) GestoreErroriImprevisti.corpo("/api/v1/pratiche", "E-1").get("errore"))
            .containsEntry("codice", "errore_interno").containsEntry("riferimento", "E-1");
        assertThat(GestoreErroriImprevisti.corpo("/api/pratiche", "E-1")).containsEntry("codice", "E-1");
    }
}
