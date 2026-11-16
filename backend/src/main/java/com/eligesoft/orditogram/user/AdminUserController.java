package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.accesso.ControlliAccesso;
import com.eligesoft.orditogram.accesso.PermessiService;
import com.eligesoft.orditogram.accesso.Permesso;
import com.eligesoft.orditogram.accesso.Ruolo;
import com.eligesoft.orditogram.accesso.RuoloRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Gestione utenti: serve il permesso utenti.gestisci (vedi SecurityConfig e accesso/ControlliAccesso). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final com.eligesoft.orditogram.registro.RegistroService registro;

    /**
     * Dati scambiati col frontend. La password viaggia solo in ingresso. "ruoli" sono i codici assegnati
     * direttamente; in uscita anche i ruoli che arrivano dai gruppi, i permessi risultanti e "admin".
     */
    public record UserDto(Long id, String username, String firstName, String lastName, String email,
                          boolean enabled, Set<String> ruoli, Set<String> groups, String password,
                          Set<String> ruoliDaGruppi, Set<String> permessi, boolean admin) {
        static UserDto of(AppUser u, PermessiService permessi) {
            Set<String> diretti = u.getRuoli().stream().map(Ruolo::getCodice).collect(Collectors.toCollection(TreeSet::new));
            Set<String> daGruppi = permessi.ruoliDi(u).stream().map(Ruolo::getCodice)
                .filter(c -> !diretti.contains(c)).collect(Collectors.toCollection(TreeSet::new));
            Set<Permesso> suoi = permessi.permessiDi(u);
            return new UserDto(u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.isEnabled(), diretti, new TreeSet<>(u.getGroups()), null, daGruppi,
                suoi.stream().map(Permesso::codice).collect(Collectors.toCollection(TreeSet::new)),
                suoi.containsAll(EnumSet.allOf(Permesso.class)));
        }
    }

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final CurrentUserService currentUser;
    private final RuoloRepository ruoli;
    private final PermessiService permessi;
    private final ControlliAccesso controlli;

    public AdminUserController(AppUserRepository repo, PasswordEncoder encoder, CurrentUserService currentUser,
            com.eligesoft.orditogram.registro.RegistroService registro, RuoloRepository ruoli,
            PermessiService permessi, ControlliAccesso controlli) {
        this.registro = registro;
        this.repo = repo;
        this.encoder = encoder;
        this.currentUser = currentUser;
        this.ruoli = ruoli;
        this.permessi = permessi;
        this.controlli = controlli;
    }

    @GetMapping
    public List<UserDto> list() {
        return repo.findAll().stream()
            .sorted(Comparator.comparing(AppUser::getUsername))
            .map(u -> UserDto.of(u, permessi))
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
        controlli.verificaPuoGestire(u);
        AppUser salvato = repo.save(u);
        registro.registra("UTENTE_CREATO", salvato.getUsername(), descrivi(salvato));
        return UserDto.of(salvato, permessi);
    }

    @PutMapping("/{id}")
    @Transactional
    public UserDto update(@PathVariable Long id, @RequestBody UserDto dto) {
        AppUser u = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean self = u.getUsername().equals(currentUser.username());
        if (self && !dto.enabled()) throw bad("Non puoi disattivare te stesso");
        controlli.verificaPuoGestire(u);
        String prima = descrivi(u);
        apply(u, dto);
        controlli.verificaPuoGestire(u);
        AppUser salvato = repo.save(u);
        controlli.verificaRestaUnAmministratore();
        String dopo = descrivi(salvato);
        boolean nuovaPassword = !isBlank(dto.password());
        registro.registra("UTENTE_MODIFICATO", salvato.getUsername(),
            (prima.equals(dopo) ? "" : "Prima: " + prima + "\nDopo: " + dopo) + (nuovaPassword ? (prima.equals(dopo) ? "" : "\n") + "Password cambiata" : ""));
        return UserDto.of(salvato, permessi);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable Long id) {
        AppUser u = repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (u.getUsername().equals(currentUser.username())) throw bad("Non puoi eliminare te stesso");
        controlli.verificaPuoGestire(u);
        String descrizione = descrivi(u);
        repo.delete(u);
        controlli.verificaRestaUnAmministratore();
        registro.registra("UTENTE_ELIMINATO", u.getUsername(), descrizione);
    }

    /** Riepilogo leggibile per il registro (mai la password). */
    private static String descrivi(AppUser u) {
        return "gruppi " + new TreeSet<>(u.getGroups())
            + ", ruoli " + u.getRuoli().stream().map(Ruolo::getCodice).collect(Collectors.toCollection(TreeSet::new))
            + (u.isEnabled() ? "" : ", disattivato");
    }

    private void apply(AppUser u, UserDto dto) {
        u.setFirstName(dto.firstName());
        u.setLastName(dto.lastName());
        u.setEmail(dto.email());
        u.setEnabled(dto.enabled());
        Set<String> codici = new HashSet<>();
        if (dto.ruoli() != null) dto.ruoli().stream().filter(c -> c != null && !c.isBlank()).map(String::trim).forEach(codici::add);
        List<Ruolo> trovati = ruoli.findByCodiceIn(codici);
        if (trovati.size() != codici.size()) {
            Set<String> noti = trovati.stream().map(Ruolo::getCodice).collect(Collectors.toSet());
            throw bad("Ruolo sconosciuto: " + codici.stream().filter(c -> !noti.contains(c)).sorted().collect(Collectors.joining(", ")));
        }
        u.setRuoli(new HashSet<>(trovati));
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
