package com.eligesoft.orditogram.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.TreeSet;

/** Dati dell'utente loggato: usato dal frontend per il login e l'intestazione. */
@RestController
public class MeController {

    private final CurrentUserService currentUser;

    public MeController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me() {
        AppUser u = currentUser.current();
        return Map.of(
            "username", u.getUsername(),
            "fullName", u.getFullName(),
            "groups", new TreeSet<>(u.getGroups()),
            "admin", u.isAdmin()
        );
    }
}
