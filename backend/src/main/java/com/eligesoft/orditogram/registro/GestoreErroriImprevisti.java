package com.eligesoft.orditogram.registro;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ultimo gestore degli errori di Spring MVC: riceve solo le eccezioni che nessun altro ha tradotto (quindi un errore
 * 500 vero). Le scrive nel registro degli errori e risponde con un messaggio senza dettagli tecnici ma con il codice
 * per ritrovarle. Accesso negato e autenticazione restano a Spring Security.
 */
@Component
public class GestoreErroriImprevisti implements HandlerExceptionResolver, Ordered {

    private final RegistroErrori errori;
    private final ObjectMapper mapper;

    public GestoreErroriImprevisti(RegistroErrori errori, ObjectMapper mapper) {
        this.errori = errori;
        this.mapper = mapper;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public ModelAndView resolveException(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException || resp.isCommitted()) return null;
        String codice = errori.registra(RegistroErrore.RICHIESTA, ex, req.getMethod() + " " + req.getRequestURI());
        try {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.setCharacterEncoding("UTF-8");
            mapper.writeValue(resp.getOutputStream(), corpo(req.getRequestURI(), codice));
        } catch (IOException e) {
            return null;
        }
        return new ModelAndView();
    }

    public static String messaggio(String codice) {
        return "Si è verificato un errore imprevisto. È stato registrato con il codice " + codice
            + ": comunicalo all'assistenza se il problema continua.";
    }

    /** Il formato dell'API pubblica per /api/v1, quello dell'interfaccia per il resto. */
    static Map<String, Object> corpo(String percorso, String codice) {
        if (percorso != null && percorso.startsWith("/api/v1/")) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("codice", "errore_interno");
            err.put("messaggio", messaggio(codice));
            err.put("riferimento", codice);
            return Map.of("errore", err);
        }
        return Map.of("message", messaggio(codice), "codice", codice);
    }
}
