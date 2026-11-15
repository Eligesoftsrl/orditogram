package com.eligesoft.orditogram.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autenticazione dell'API pubblica: la chiave arriva nell'intestazione X-API-Key
 * oppure come "Authorization: Bearer iter_...". Niente sessioni né cookie.
 * Non è un @Component: lo aggiunge solo la catena di sicurezza di /api/v1 (SecurityConfig).
 */
public class FiltroChiaveApi extends OncePerRequestFilter {

    private final ChiaviApiService chiavi;
    private final LimiteRichieste limite;

    public FiltroChiaveApi(ChiaviApiService chiavi, LimiteRichieste limite) {
        this.chiavi = chiavi;
        this.limite = limite;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equals(req.getMethod())) {   // preflight CORS: senza chiave
            chain.doFilter(req, res);
            return;
        }
        String valore = valore(req);
        if (valore == null) {
            ErroriApi.scrivi(res, 401, "chiave_mancante",
                "Manca la chiave API: inviala nell'intestazione X-API-Key o come Authorization: Bearer");
            return;
        }
        var identita = chiavi.verifica(valore);
        if (identita.isEmpty()) {
            ErroriApi.scrivi(res, 401, "chiave_non_valida", "Chiave API non valida, scaduta o revocata");
            return;
        }
        long attesa = limite.consuma(identita.get().sistema(), System.currentTimeMillis());
        if (attesa > 0) {
            res.setHeader("Retry-After", String.valueOf(attesa));
            ErroriApi.scrivi(res, 429, "troppe_richieste",
                "Superato il limite di " + limite.perMinuto() + " richieste al minuto: riprova tra " + attesa + " secondi");
            return;
        }
        try {
            chiavi.segnaUso(identita.get(), indirizzo(req));
        } catch (RuntimeException ignorata) {
            // l'annotazione dell'ultimo uso non deve mai bloccare una richiesta
        }
        var contesto = SecurityContextHolder.createEmptyContext();
        contesto.setAuthentication(identita.get());
        SecurityContextHolder.setContext(contesto);
        try {
            chain.doFilter(req, res);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    static String valore(HttpServletRequest req) {
        String k = req.getHeader("X-API-Key");
        if (k != null && !k.isBlank()) return k.trim();
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7) && auth.length() > 7) return auth.substring(7).trim();
        return null;
    }

    static String indirizzo(HttpServletRequest req) {
        String inoltrato = req.getHeader("X-Forwarded-For");
        return inoltrato != null && !inoltrato.isBlank() ? inoltrato.split(",")[0].trim() : req.getRemoteAddr();
    }
}
