package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.registro.RegistroService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Studio → Ruoli: ruoli applicativi, i loro permessi e i gruppi che li ricevono. Serve il permesso utenti.gestisci. */
@RestController
@RequestMapping("/api/admin/ruoli")
public class AdminRuoliController {

    private static final Pattern CODICE = Pattern.compile("[a-z0-9][a-z0-9-]{1,59}");

    public record PermessoDto(String codice, String nome, String descrizione) {
        static PermessoDto of(Permesso p) { return new PermessoDto(p.codice(), p.nome(), p.descrizione()); }
    }

    /** Ruolo scambiato col frontend; "bloccato": permessi fissi (amministratore). */
    public record RuoloDto(Long id, String codice, String nome, String descrizione, boolean predefinito,
                           boolean bloccato, Set<String> permessi, Set<String> gruppi) {
        static RuoloDto of(Ruolo r) {
            Set<String> permessi = PermessiService.permessiDel(r).stream().map(Permesso::codice)
                .collect(Collectors.toCollection(TreeSet::new));
            return new RuoloDto(r.getId(), r.getCodice(), r.getNome(), r.getDescrizione(), r.isPredefinito(),
                r.isAmministratore(), permessi, new TreeSet<>(r.getGruppi()));
        }
    }

    private final RuoloRepository ruoli;
    private final ControlliAccesso controlli;
    private final RegistroService registro;

    public AdminRuoliController(RuoloRepository ruoli, ControlliAccesso controlli, RegistroService registro) {
        this.ruoli = ruoli;
        this.controlli = controlli;
        this.registro = registro;
    }

    @GetMapping
    public List<RuoloDto> elenco() {
        return ruoli.findAll().stream()
            .sorted(Comparator.comparing((Ruolo r) -> !r.isAmministratore()).thenComparing(Ruolo::getNome))
            .map(RuoloDto::of).toList();
    }

    /** Il catalogo dei permessi, per costruire i ruoli. */
    @GetMapping("/permessi")
    public List<PermessoDto> permessi() {
        return Arrays.stream(Permesso.values()).map(PermessoDto::of).toList();
    }

    @PostMapping
    @Transactional
    public RuoloDto crea(@RequestBody RuoloDto dto) {
        String codice = dto.codice() == null ? "" : dto.codice().trim().toLowerCase(Locale.ROOT);
        if (!CODICE.matcher(codice).matches())
            throw bad("Il codice è obbligatorio: da 2 a 60 caratteri tra lettere minuscole, cifre e trattini");
        if (ruoli.existsByCodice(codice)) throw bad("Esiste già un ruolo con codice " + codice);
        Ruolo r = new Ruolo();
        r.setCodice(codice);
        applica(r, dto);
        Ruolo salvato = ruoli.save(r);
        registro.registra("RUOLO_CREATO", salvato.getCodice(), descrivi(salvato));
        return RuoloDto.of(salvato);
    }

    @PutMapping("/{id}")
    @Transactional
    public RuoloDto modifica(@PathVariable Long id, @RequestBody RuoloDto dto) {
        Ruolo r = ruoli.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String prima = descrivi(r);
        controlli.verificaEntroIMiei(PermessiService.permessiDel(r));
        if (r.isAmministratore()) {
            // permessi e nome fissi: si sceglie solo quali gruppi lo ricevono
            r.setGruppi(gruppi(dto.gruppi()));
        } else {
            applica(r, dto);
        }
        Ruolo salvato = ruoli.save(r);
        if (r.isAmministratore()) controlli.verificaRestaUnAmministratore();
        String dopo = descrivi(salvato);
        if (!prima.equals(dopo))
            registro.registra("RUOLO_MODIFICATO", salvato.getCodice(), "Prima: " + prima + "\nDopo: " + dopo);
        return RuoloDto.of(salvato);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void elimina(@PathVariable Long id) {
        Ruolo r = ruoli.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (r.isPredefinito()) throw bad("I ruoli predefiniti non si eliminano: puoi cambiarne permessi e gruppi");
        controlli.verificaEntroIMiei(PermessiService.permessiDel(r));
        // le assegnazioni agli utenti spariscono con il ruolo (vincolo ON DELETE CASCADE)
        ruoli.delete(r);
        registro.registra("RUOLO_ELIMINATO", r.getCodice(), descrivi(r));
    }

    private void applica(Ruolo r, RuoloDto dto) {
        if (dto.nome() == null || dto.nome().isBlank()) throw bad("Il nome del ruolo è obbligatorio");
        r.setNome(dto.nome().trim());
        r.setDescrizione(dto.descrizione() == null || dto.descrizione().isBlank() ? null : dto.descrizione().trim());
        Set<String> permessi = new HashSet<>();
        if (dto.permessi() != null) for (String c : dto.permessi()) {
            Permesso p = Permesso.da(c).orElseThrow(() -> bad("Permesso sconosciuto: " + c));
            permessi.add(p.codice());
        }
        controlli.verificaEntroIMiei(permessi.stream().map(c -> Permesso.da(c).orElseThrow())
            .collect(Collectors.toSet()));
        r.setPermessi(permessi);
        r.setGruppi(gruppi(dto.gruppi()));
    }

    private static Set<String> gruppi(Set<String> valori) {
        Set<String> gruppi = new HashSet<>();
        if (valori != null) valori.stream().filter(g -> g != null && !g.isBlank()).map(String::trim)
            .forEach(g -> {
                if (g.length() > 100) throw bad("Nome di gruppo troppo lungo: " + g.substring(0, 20) + "…");
                gruppi.add(g);
            });
        return gruppi;
    }

    /** Riepilogo leggibile per il registro. */
    private static String descrivi(Ruolo r) {
        return r.getNome() + ": permessi " + new TreeSet<>(PermessiService.permessiDel(r).stream().map(Permesso::codice).toList())
            + ", gruppi " + new TreeSet<>(r.getGruppi());
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
