package com.eligesoft.orditogram;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

/** Protezione CSRF: dal browser le modifiche richiedono X-Requested-With, per tutti i modi di accesso. */
class FiltroRichiesteSessioneTest {

    @AfterEach
    void pulisci() { SecurityContextHolder.clearContext(); }

    private static int stato(String metodo, String... intestazioni) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, "/api/processes/x/start");
        for (int i = 0; i < intestazioni.length; i += 2) req.addHeader(intestazioni[i], intestazioni[i + 1]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new SecurityConfig.FiltroRichiesteSessione().doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    private static void utente() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("mario", "password", AuthorityUtils.NO_AUTHORITIES));
    }

    @Test
    void ilBrowserSenzaIntestazioneVieneFermato() throws Exception {
        utente();
        assertEquals(403, stato("POST", "Cookie", "JSESSIONID=abc"));
        assertEquals(403, stato("POST", "Origin", "https://altro-sito.example"));
        assertEquals(403, stato("DELETE", "Sec-Fetch-Site", "cross-site"));
        assertEquals(200, stato("POST", "Cookie", "JSESSIONID=abc", "X-Requested-With", "XMLHttpRequest"));
        assertEquals(200, stato("GET", "Cookie", "JSESSIONID=abc"));
    }

    @Test
    void integrazioniEUtentiAnonimiPassano() throws Exception {
        utente();
        assertEquals(200, stato("POST"));   // integrazione con Basic, nessun segno di browser
        SecurityContextHolder.getContext().setAuthentication(
            new AnonymousAuthenticationToken("k", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(200, stato("POST", "Origin", "https://sistema.example"));
    }
}
