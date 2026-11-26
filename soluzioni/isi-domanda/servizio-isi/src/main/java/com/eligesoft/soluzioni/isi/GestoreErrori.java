package com.eligesoft.soluzioni.isi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

/** Errori di chi chiama con il loro codice; gli imprevisti rispondono 500 con il solo codice {@code E-…}. */
@RestControllerAdvice
public class GestoreErrori {
    private static final Logger LOG = LoggerFactory.getLogger(GestoreErrori.class);
    private final SecureRandom caso = new SecureRandom();

    @ExceptionHandler(ErroreRichiesta.class)
    ResponseEntity<Map<String, Object>> richiesta(ErroreRichiesta e) {
        return risposta(e.stato(), e.codice(), e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> json(HttpMessageNotReadableException e) {
        return risposta(400, "JSON_NON_VALIDO", "Il corpo non è un JSON valido");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> sconosciuto(NoResourceFoundException e) {
        return risposta(404, "NON_TROVATO", "Indirizzo sconosciuto");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> imprevisto(Exception e) {
        byte[] b = new byte[4];
        caso.nextBytes(b);
        String codice = "E-" + HexFormat.of().withUpperCase().formatHex(b);
        LOG.error("Errore imprevisto {}", codice, e);
        return risposta(500, codice, "Errore imprevisto");
    }

    private static ResponseEntity<Map<String, Object>> risposta(int stato, String codice, String messaggio) {
        return ResponseEntity.status(stato).body(Map.of("errore", Map.of("codice", codice, "messaggio", messaggio)));
    }
}
