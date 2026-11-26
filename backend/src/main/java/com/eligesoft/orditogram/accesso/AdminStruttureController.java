package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Studio → Strutture: l'albero delle strutture dell'ente (dipartimento, uffici, filiali).
 * Le persone si mettono nelle strutture dalla pagina Utenti. Serve il permesso utenti.gestisci.
 */
@RestController
@RequestMapping("/api/admin/strutture")
public class AdminStruttureController {

    private static final Pattern CODICE = Pattern.compile("[a-z0-9][a-z0-9-]{1,59}");

    public record StrutturaDto(Long id, String codice, String nome, String codiceFiscale, Long padreId,
                               Boolean attiva, long utenti) { }

    private final OrganizzazioneRepository organizzazioni;
    private final StruttureService strutture;
    private final AppUserRepository utenti;
    private final PraticaStrutturaRepository pratiche;
    private final RegistroService registro;

    public AdminStruttureController(OrganizzazioneRepository organizzazioni, StruttureService strutture,
                                    AppUserRepository utenti, PraticaStrutturaRepository pratiche, RegistroService registro) {
        this.organizzazioni = organizzazioni;
        this.strutture = strutture;
        this.utenti = utenti;
        this.pratiche = pratiche;
        this.registro = registro;
    }

    @GetMapping
    public List<StrutturaDto> elenco() {
        Map<Long, Long> conteggio = new HashMap<>();
        utenti.findAll().forEach(u -> u.getOrganizzazioni().forEach(o -> conteggio.merge(o, 1L, Long::sum)));
        return organizzazioni.findAll().stream()
            .filter(o -> Organizzazione.STRUTTURA.equals(o.getTipo()))
            .sorted(Comparator.comparing(Organizzazione::getNome, String.CASE_INSENSITIVE_ORDER))
            .map(o -> dto(o, conteggio.getOrDefault(o.getId(), 0L)))
            .toList();
    }

    @PostMapping
    @Transactional
    public StrutturaDto crea(@RequestBody StrutturaDto dto) {
        String codice = dto.codice() == null ? "" : dto.codice().trim().toLowerCase(Locale.ROOT);
        if (!CODICE.matcher(codice).matches())
            throw bad("Il codice è obbligatorio: da 2 a 60 caratteri tra lettere minuscole, cifre e trattini");
        if (organizzazioni.existsByCodice(codice)) throw bad("Esiste già una struttura con codice " + codice);
        Organizzazione o = new Organizzazione();
        o.setCodice(codice);
        applica(o, dto);
        Organizzazione salvata = organizzazioni.save(o);
        registro.registra("STRUTTURA_CREATA", salvata.getCodice(), descrivi(salvata));
        return dto(salvata, 0);
    }

    @PutMapping("/{id}")
    @Transactional
    public StrutturaDto modifica(@PathVariable Long id, @RequestBody StrutturaDto dto) {
        Organizzazione o = trova(id);
        String prima = descrivi(o);
        applica(o, dto);
        Organizzazione salvata = organizzazioni.save(o);
        String dopo = descrivi(salvata);
        if (!prima.equals(dopo)) registro.registra("STRUTTURA_MODIFICATA", salvata.getCodice(), "Prima: " + prima + "\nDopo: " + dopo);
        return dto(salvata, 0);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void elimina(@PathVariable Long id) {
        Organizzazione o = trova(id);
        if (!organizzazioni.findByPadreId(id).isEmpty()) throw bad("La struttura ha strutture sotto: spostale o eliminale prima");
        if (pratiche.existsByOrganizzazioneId(id))
            throw bad("La struttura è competente per alcune pratiche: disattivala invece di eliminarla");
        // le appartenenze delle persone spariscono con la struttura (vincolo ON DELETE CASCADE)
        organizzazioni.delete(o);
        registro.registra("STRUTTURA_ELIMINATA", o.getCodice(), descrivi(o));
    }

    private void applica(Organizzazione o, StrutturaDto dto) {
        if (dto.nome() == null || dto.nome().isBlank() || dto.nome().trim().length() > 200)
            throw bad("Il nome della struttura è obbligatorio (al massimo 200 caratteri)");
        o.setNome(dto.nome().trim());
        String cf = dto.codiceFiscale() == null ? "" : dto.codiceFiscale().trim().toUpperCase(Locale.ROOT);
        if (cf.length() > 20) throw bad("Codice fiscale troppo lungo");
        o.setCodiceFiscale(cf.isEmpty() ? null : cf);
        if (dto.padreId() != null) {
            Organizzazione padre = trova(dto.padreId());
            if (!Organizzazione.STRUTTURA.equals(padre.getTipo())) throw bad("La struttura sopra non è una struttura interna");
            if (o.getId() != null && strutture.creaCiclo(o.getId(), padre.getId()))
                throw bad("Una struttura non può stare sotto sé stessa o sotto una delle sue");
        }
        o.setPadreId(dto.padreId());
        o.setAttiva(dto.attiva() == null || dto.attiva());
    }

    private Organizzazione trova(Long id) {
        return organizzazioni.findById(id)
            .filter(o -> Organizzazione.STRUTTURA.equals(o.getTipo()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Struttura non trovata"));
    }

    private static StrutturaDto dto(Organizzazione o, long utenti) {
        return new StrutturaDto(o.getId(), o.getCodice(), o.getNome(), o.getCodiceFiscale(), o.getPadreId(), o.isAttiva(), utenti);
    }

    private static String descrivi(Organizzazione o) {
        return o.getNome() + (o.getPadreId() == null ? "" : ", sotto la struttura " + o.getPadreId())
            + (o.isAttiva() ? "" : ", disattivata");
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
