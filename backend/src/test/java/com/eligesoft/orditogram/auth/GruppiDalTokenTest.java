package com.eligesoft.orditogram.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GruppiDalTokenTest {

    @Test
    void keycloakConPercorsoCompleto() {
        assertEquals(Set.of("analisti", "operatori"),
            GruppiDalToken.leggi(Map.of("groups", List.of("/ente/analisti", "operatori")), "groups", Map.of()));
    }

    @Test
    void claimAnnidato() {
        assertEquals(Set.of("responsabili", "offline_access"),
            GruppiDalToken.leggi(Map.of("realm_access", Map.of("roles", List.of("responsabili", "offline_access"))), "realm_access.roles", Map.of()));
    }

    @Test
    void entraConMappaGliIdNonMappatiSiScartano() {
        assertEquals(Set.of("analisti"), GruppiDalToken.leggi(
            Map.of("groups", List.of("6f1c2a7e-1111-2222-3333-444455556666", "aaaaaaaa-1111-2222-3333-444455556666")),
            "groups", Map.of("6f1c2a7e-1111-2222-3333-444455556666", "analisti")));
    }

    @Test
    void claimAssente() {
        assertEquals(Set.of(), GruppiDalToken.leggi(Map.of(), "groups", Map.of()));
    }
}
