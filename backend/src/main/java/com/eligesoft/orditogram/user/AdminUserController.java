package com.eligesoft.orditogram.user;

import com.eligesoft.orditogram.accesso.ControlliAccesso;
import com.eligesoft.orditogram.accesso.OrganizzazioneRepository;
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
                          boolean enabled, Set<String> ruoli, Set<String> groups, Set<Long> organizzazioni,
                          String password, Set<String> ruoliDaGruppi, Set<String> permessi, boolean admin, String tipo) {
        static UserDto of(AppUser u, PermessiService permessi) {
            Set<String> diretti = u.getRuoli().stream().map(Ruolo::getCodice).collect(Collectors.toCollection(TreeSet::new));
            Set<String> daGruppi = permessi.ruoliDi(u).stream().map(Ruolo::getCodice)
                .filter(c -> !diretti.contains(c)).collect(Collectors.toCollection(TreeSet::new));
            Set<Permesso> suoi = permessi.permessiDi(u);
            return new UserDto(u.getId(), u.getUsername(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.isEnabled(), diretti, new TreeSet<>(u.getGroups()), new TreeSet<>(u.getOrganizzazioni()),
                null, daGruppi,
                suoi.stream().map(Permesso::codice).collect(Collectors.toCollection(TreeSet::new)),
                suoi.containsAll(EnumSet.allOf(Permesso.class)), u.getTipo());
        }
    }

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final CurrentUserService currentUser;
    private final RuoloRepository ruoli;
    private final PermessiService permessi;
    private final ControlliAccesso controlli;
    private final OrganizzazioneRepository organizzazioni;

    public AdminUserController(AppUserRepository repo, PasswordEncoder encoder, CurrentUserService currentUser,
            com.eligesoft.orditogram.registro.RegistroService registro, RuoloRepository ruoli,
            PermessiService permessi, ControlliAccesso controlli, OrganizzazioneRepository organizzazioni) {
        this.organizzazioni = organizzazioni;
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
        // gruppi, ruoli e strutture allargano ciò che si vede e si lavora: per sé li cambia solo l'amministratore
        if (self && !currentUser.isAdmin() && cambiaAccessi(u, dto))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "I tuoi gruppi, ruoli e strutture li cambia un amministratore");
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
        return (u.isEsterno() ? "utente esterno, " : "") + "gruppi " + new TreeSet<>(u.getGroups())
            + (u.getOrganizzazioni().isEmpty() ? "" : ", strutture " + new TreeSet<>(u.getOrganizzazioni()))
            + ", ruoli " + u.getRuoli().stream().map(Ruolo::getCodice).collect(Collectors.toCollection(TreeSet::new))
            + (u.isEnabled() ? "" : ", disattivato");
    }

    private static boolean cambiaAccessi(AppUser u, UserDto dto) {
        Set<String> gruppi = dto.groups() == null ? Set.of() : dto.groups().stream().map(String::trim)
            .filter(g -> !g.isEmpty()).collect(Collectors.toSet());
        Set<String> ruoliNuovi = dto.ruoli() == null ? Set.of() : dto.ruoli().stream().filter(c -> c != null && !c.isBlank())
            .map(String::trim).collect(Collectors.toSet());
        Set<String> ruoliAttuali = u.getRuoli().stream().map(Ruolo::getCodice).collect(Collectors.toSet());
        Set<Long> strutture = dto.organizzazioni() == null ? Set.of() : dto.organizzazioni();
        return !gruppi.equals(u.getGroups()) || !ruoliNuovi.equals(ruoliAttuali) || !strutture.equals(u.getOrganizzazioni())
            || !tipo(dto).equals(u.getTipo());
    }

    private static String tipo(UserDto dto) {
        return dto.tipo() == null || dto.tipo().isBlank() ? AppUser.INTERNO : dto.tipo().trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void apply(AppUser u, UserDto dto) {
        String tipo = tipo(dto);
        if (!AppUser.INTERNO.equals(tipo) && !AppUser.ESTERNO.equals(tipo)) throw bad("Il tipo dell'utente è INTERNO o ESTERNO");
        boolean esterno = AppUser.ESTERNO.equals(tipo);
        u.setTipo(tipo);
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
        // un utente esterno non ha mai ruoli né permessi di amministrazione
        if (esterno && !trovati.isEmpty()) throw bad("Un utente esterno non può avere ruoli");
        u.setRuoli(new HashSet<>(trovati));
        Set<Long> strutture = dto.organizzazioni() == null ? new HashSet<>() : new HashSet<>(dto.organizzazioni());
        strutture.remove(null);
        var trovate = organizzazioni.findAllById(strutture);
        if (trovate.size() != strutture.size()) throw bad("Struttura o organizzazione sconosciuta");
        // il personale sta nelle strutture dell'ente, gli utenti esterni nelle organizzazioni esterne
        if (trovate.stream().anyMatch(o -> o.isEsterna() != esterno))
            throw bad(esterno ? "Un utente esterno può appartenere solo a organizzazioni esterne"
                : "Un utente interno può appartenere solo a strutture dell'ente");
        u.setOrganizzazioni(strutture);
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
