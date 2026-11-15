package com.eligesoft.orditogram.api;

import com.eligesoft.orditogram.api.ErroriApi.ErroreApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Errori dell'API pubblica, sempre nel formato { "errore": { "codice", "messaggio", "campi" } }. */
@RestControllerAdvice(assignableTypes = ApiV1Controller.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GestoreErroriApiV1 {

    private static final Logger log = LoggerFactory.getLogger(GestoreErroriApiV1.class);

    @ExceptionHandler(ErroreApi.class)
    public ResponseEntity<Map<String, Object>> api(ErroreApi e) {
        return risposta(e.stato(), e.codice(), e.getMessage(), e.campi());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> stato(ResponseStatusException e) {
        int s = e.getStatusCode().value();
        String codice = switch (s) {
            case 400 -> "richiesta_non_valida";
            case 403 -> "permesso_mancante";
            case 404 -> "non_trovato";
            case 409 -> "conflitto";
            case 413 -> "troppo_grande";
            default -> s >= 500 ? "errore_interno" : "errore";
        };
        return risposta(s, codice, e.getReason() != null ? e.getReason() : "Errore " + s, List.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> richiesta(Exception e) {
        String msg = e instanceof HttpMessageNotReadableException ? "Il corpo della richiesta non è un JSON valido o non ha la forma attesa"
            : e.getMessage();
        return risposta(400, "richiesta_non_valida", msg, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> formato(HttpMediaTypeNotSupportedException e) {
        return risposta(415, "formato_non_supportato", "Usa Content-Type: application/json (o multipart/form-data per i documenti)", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> grande(Exception e) {
        return risposta(413, "troppo_grande", "File troppo grande", List.of());
    }

    @ExceptionHandler(org.activiti.engine.ActivitiException.class)
    public ResponseEntity<Map<String, Object>> motore(org.activiti.engine.ActivitiException e) {
        RuntimeException causa = com.eligesoft.orditogram.ApiExceptionHandler.causaApplicativa(e);
        if (causa != null) return risposta(409, "conflitto", causa.getMessage(), List.of());
        log.warn("Errore del motore nell'API pubblica", e);
        return risposta(500, "errore_processo", "Errore nell'esecuzione del processo: " + e.getMessage(), List.of());
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> conflitto(RuntimeException e) {
        return risposta(409, "conflitto", e.getMessage() != null ? e.getMessage() : "Operazione non consentita", List.of());
    }

    static ResponseEntity<Map<String, Object>> risposta(int stato, String codice, String messaggio, List<Map<String, String>> campi) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("codice", codice);
        err.put("messaggio", messaggio);
        if (campi != null && !campi.isEmpty()) err.put("campi", campi);
        return ResponseEntity.status(stato).body(Map.of("errore", err));
    }
}
