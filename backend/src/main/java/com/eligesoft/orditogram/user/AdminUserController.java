package com.eligesoft.orditogram.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Gestione utenti: accessibile solo a ROLE_ADMIN (vedi SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final com.eligesoft.orditogram.registro.RegistroService registro;

    /** Dati scambiati col frontend. La password viaggia solo in ingresso. */
    public record UserDto(Long id, String username, String firstName, String lastName, String email,
                          boolean enabled, boolean admin, Set<String> groups, String password) {
        static UserDto of(AppUser u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.isEnabled(), u.isAdmin(), new TreeSet<>(u.getGroups()), null);
        }
    }

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final CurrentUserService currentUser;

    public AdminUserController(AppUserRepository repo, PasswordEncoder encoder, CurrentUserService currentUser,
            com.eligesoft.orditogram.registro.RegistroService registro) {
        this.registro = registro;
        this.repo = repo;
        this.encoder = encoder;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserDto> list() {
        return repo.findAll().stream()
            .sorted(Comparator.comparing(AppUser::getUsername))
            .map(UserDto::of)
            .toList();
    }

    @PostMapping
    @Transactional
    public UserDto create(@RequestBody UserDto dto) {
        if (isBlank(dto.username())) throw bad("Lo username è obbligatorio");
        if (dto.username().trim().toLowerCase(java.util.Locale.ROOT).startsWith("api:"))
            throw bad("Lo username non può iniziare con \"api:\" (riservato ai sistemi esterni)");
        if (isBlank(dto.password())) throw bad("La password è obbligatoria");
        if (repo.existsByUsername(dto.username().trim())) throw bad("Username già esistente");

        AppUser u = new AppUser();
        u.setUsername(dto.username().trim());
        apply(u, dto);
        AppUser salvato = repo.save(u);
        registro.registra("UTENTE_CREATO", salvato.getUsername(), descrivi(salvato));
        return UserDto.of(salvato);
    }

    @PutMapping("/{id}")
    @Transactional
    public UserDto update(@PathVariable Long id, @RequestBody UserDto dto) {
        AppUser u = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean self = u.getUsername().equals(currentUser.username());
        if (self && (!dto.admin() || !dto.enabled()))
            throw bad("Non puoi togliere a te stesso il ruolo admin o disattivarti");
        String prima = descrivi(u);
        apply(u, dto);
        AppUser salvato = repo.save(u);
        String dopo = descrivi(salvato);
        boolean nuovaPassword = !isBlank(dto.password());
        registro.registra("UTENTE_MODIFICATO", salvato.getUsername(),
            (prima.equals(dopo) ? "" : "Prima: " + prima + "\nDopo: " + dopo) + (nuovaPassword ? (prima.equals(dopo) ? "" : "\n") + "Password cambiata" : ""));
        return UserDto.of(salvato);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        AppUser u = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (u.getUsername().equals(currentUser.username())) throw bad("Non puoi eliminare te stesso");
        repo.delete(u);
        registro.registra("UTENTE_ELIMINATO", u.getUsername(), descrivi(u));
    }

    /** Riepilogo leggibile per il registro (mai la password). */
    private static String descrivi(AppUser u) {
        return "gruppi " + new java.util.TreeSet<>(u.getGroups()) + (u.isAdmin() ? ", amministratore" : "")
            + (u.isEnabled() ? "" : ", disattivato");
    }

    private void apply(AppUser u, UserDto dto) {
        u.setFirstName(dto.firstName());
        u.setLastName(dto.lastName());
        u.setEmail(dto.email());
        u.setEnabled(dto.enabled());
        u.setAdmin(dto.admin());
        Set<String> groups = new HashSet<>();
        if (dto.groups() != null) dto.groups().stream()
            .map(String::trim).filter(g -> !g.isEmpty()).forEach(groups::add);
        u.setGroups(groups);
        // password cambiata solo se valorizzata
        if (!isBlank(dto.password())) u.setPassword(encoder.encode(dto.password()));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
