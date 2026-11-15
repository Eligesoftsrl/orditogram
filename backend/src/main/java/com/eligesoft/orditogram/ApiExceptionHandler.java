package com.eligesoft.orditogram;

import org.activiti.api.runtime.shared.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Traduce gli errori del motore in risposte leggibili dal frontend. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", "Attività o processo non trovato, oppure non sei autorizzato"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(Exception e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("message", "File troppo grande: il limite è impostato in application.yml (spring.servlet.multipart)"));
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Operazione non consentita"));
    }

    /**
     * Errori sollevati dentro il motore (es. un connettore che non riesce): Activiti li avvolge in una
     * ActivitiException; si restituisce il messaggio originale, comprensibile per l'utente.
     */
    /** La prima IllegalStateException o IllegalArgumentException nella catena delle cause, se c'è. */
    public static RuntimeException causaApplicativa(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause() == c ? null : c.getCause())
            if (c instanceof IllegalStateException || c instanceof IllegalArgumentException) return (RuntimeException) c;
        return null;
    }

    @ExceptionHandler(org.activiti.engine.ActivitiException.class)
    public ResponseEntity<Map<String, String>> motore(org.activiti.engine.ActivitiException e) {
        RuntimeException causa = causaApplicativa(e);
        if (causa != null) return conflict(causa);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("message", "Errore nell'esecuzione del processo: " + e.getMessage()));
    }
}
