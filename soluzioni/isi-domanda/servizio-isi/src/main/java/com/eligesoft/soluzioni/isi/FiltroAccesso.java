package com.eligesoft.soluzioni.isi;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;

/**
 * Prima di ogni richiesta: errore simulato per il collaudo ({@code ?simula=errore}), corpo al massimo di 1 MB,
 * credenziale Bearer (tranne le pagine pubbliche). Dopo: una riga di log JSON per richiesta.
 */
@Component
public class FiltroAccesso extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger("richieste");
    private static final Set<String> PUBBLICI = Set.of("/salute", "/elenchi", "/calendario");
    private static final long MASSIMO = 1_000_000;

    private final byte[] atteso;

    public FiltroAccesso(Impostazioni impostazioni) {
        this.atteso = Configurazione.vuoto(impostazioni.token()) ? null
                : ("Bearer " + impostazioni.token()).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain catena)
            throws ServletException, IOException {
        long inizio = System.nanoTime();
        String percorso = req.getRequestURI().replaceAll("/+$", "");
        try {
            if ("errore".equals(req.getParameter("simula"))) {
                scrivi(res, 503, "NON_DISPONIBILE", "Servizio non disponibile (simulato)");
            } else if (req.getContentLengthLong() > MASSIMO) {
                scrivi(res, 413, "TROPPO_GRANDE", "Richiesta troppo grande");
            } else if (atteso != null && !PUBBLICI.contains(percorso) && !MessageDigest.isEqual(atteso,
                    String.valueOf(req.getHeader("Authorization")).getBytes(StandardCharsets.UTF_8))) {
                scrivi(res, 401, "NON_AUTORIZZATO", "Credenziale mancante o non valida");
            } else {
                catena.doFilter(req, res);
            }
        } finally {
            LOG.info("{\"metodo\":\"{}\",\"percorso\":\"{}\",\"stato\":{},\"ms\":{}}", req.getMethod(),
                    percorso.replace("\"", ""), res.getStatus(), (System.nanoTime() - inizio) / 1_000_000);
        }
    }

    private static void scrivi(HttpServletResponse res, int stato, String codice, String messaggio) throws IOException {
        res.setStatus(stato);
        res.setContentType("application/json;charset=UTF-8");
        res.getOutputStream().write(Regole.JSON.writeValueAsBytes(
                Map.of("errore", Map.of("codice", codice, "messaggio", messaggio))));
    }
}
