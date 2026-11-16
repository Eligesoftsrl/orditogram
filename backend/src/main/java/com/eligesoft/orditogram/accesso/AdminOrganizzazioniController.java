package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.user.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Studio → Organizzazioni: l'anagrafica delle organizzazioni esterne (enti, aziende, studi, istituti) che possono
 * essere parti delle pratiche. I loro utenti sono utenti esterni. Serve il permesso organizzazioni.gestisci.
 */
@RestController
@RequestMapping("/api/admin/organizzazioni")
public class AdminOrganizzazioniController {

    private static final Pattern CODICE = Pattern.compile("[a-z0-9][a-z0-9-]{1,59}");
    /** Righe al massimo in un file da importare. */
    static final int MAX_RIGHE_CSV = 5000;

    public record OrganizzazioneDto(Long id, String codice, String nome, String codiceFiscale, Boolean attiva, long utenti, long referenti) { }

    /** I referenti scelti per un'organizzazione (id degli utenti). */
    public record Referenti(List<Long> utenti) { }

    /** Esito di un'importazione: quante create e aggiornate, e le righe scartate con il motivo. */
    public record EsitoImport(int create, int aggiornate, List<String> scartate) { }

    private final OrganizzazioneRepository organizzazioni;
    private final AppUserRepository utenti;
    private final PraticaParteRepository parti;
    private final RegistroService registro;
    private final ReferentiService referenti;

    public AdminOrganizzazioniController(OrganizzazioneRepository organizzazioni, AppUserRepository utenti,
                                         PraticaParteRepository parti, RegistroService registro, ReferentiService referenti) {
        this.referenti = referenti;
        this.organizzazioni = organizzazioni;
        this.utenti = utenti;
        this.parti = parti;
        this.registro = registro;
    }

    @GetMapping
    public List<OrganizzazioneDto> elenco() {
        Map<Long, Long> conteggio = new HashMap<>();
        utenti.findAll().forEach(u -> u.getOrganizzazioni().forEach(o -> conteggio.merge(o, 1L, Long::sum)));
        return organizzazioni.findByTipoOrderByNomeAsc(Organizzazione.ESTERNA).stream()
            .map(o -> dto(o, conteggio.getOrDefault(o.getId(), 0L)))
            .toList();
    }

    /** Le persone dell'organizzazione e chi ne è referente. */
    @GetMapping("/{id}/persone")
    public List<ReferentiService.Collega> persone(@PathVariable Long id) {
        return referenti.membri(trova(id));
    }

    /** Sceglie i referenti tra gli utenti esterni dell'organizzazione (l'elenco sostituisce quello di prima). */
    @PutMapping("/{id}/referenti")
    @Transactional
    public List<ReferentiService.Collega> referenti(@PathVariable Long id, @RequestBody Referenti scelti) {
        return referenti.impostaReferenti(trova(id), scelti.utenti());
    }

    @PostMapping
    @Transactional
    public OrganizzazioneDto crea(@RequestBody OrganizzazioneDto dto) {
        String codice = codice(dto.codice());
        if (organizzazioni.existsByCodice(codice)) throw bad("Esiste già un'organizzazione o struttura con codice " + codice);
        Organizzazione o = new Organizzazione();
        o.setCodice(codice);
        o.setTipo(Organizzazione.ESTERNA);
        applica(o, dto.nome(), dto.codiceFiscale(), dto.attiva());
        Organizzazione salvata = organizzazioni.save(o);
        registro.registra("ORGANIZZAZIONE_CREATA", salvata.getCodice(), descrivi(salvata));
        return dto(salvata, 0);
    }

    @PutMapping("/{id}")
    @Transactional
    public OrganizzazioneDto modifica(@PathVariable Long id, @RequestBody OrganizzazioneDto dto) {
        Organizzazione o = trova(id);
        String prima = descrivi(o);
        applica(o, dto.nome(), dto.codiceFiscale(), dto.attiva());
        Organizzazione salvata = organizzazioni.save(o);
        String dopo = descrivi(salvata);
        if (!prima.equals(dopo)) registro.registra("ORGANIZZAZIONE_MODIFICATA", salvata.getCodice(), "Prima: " + prima + "\nDopo: " + dopo);
        return dto(salvata, 0);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void elimina(@PathVariable Long id) {
        Organizzazione o = trova(id);
        if (parti.existsByOrganizzazioneId(id))
            throw bad("L'organizzazione è parte di alcune pratiche: disattivala invece di eliminarla");
        if (utenti.findAll().stream().anyMatch(u -> u.getOrganizzazioni().contains(id)))
            throw bad("L'organizzazione ha degli utenti: toglili o disattivala invece di eliminarla");
        organizzazioni.delete(o);
        registro.registra("ORGANIZZAZIONE_ELIMINATA", o.getCodice(), descrivi(o));
    }

    /**
     * Importa da un file CSV (separatore ";" o ",", UTF-8) con le colonne codice;nome;codiceFiscale.
     * La prima riga può essere l'intestazione. Un codice già presente aggiorna nome e codice fiscale.
     */
    @PostMapping(value = "/importa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public EsitoImport importa(@RequestParam("file") MultipartFile file) throws IOException {
        String testo = new String(file.getBytes(), StandardCharsets.UTF_8).replace("﻿", "");
        List<String> righe = testo.lines().toList();
        if (righe.size() > MAX_RIGHE_CSV + 1) throw bad("Il file ha troppe righe (al massimo " + MAX_RIGHE_CSV + ")");
        int create = 0, aggiornate = 0;
        List<String> scartate = new ArrayList<>();
        Set<String> visti = new HashSet<>();
        for (int i = 0; i < righe.size(); i++) {
            String riga = righe.get(i).trim();
            if (riga.isEmpty()) continue;
            String[] c = riga.split(riga.contains(";") ? ";" : ",", -1);
            if (i == 0 && c[0].trim().equalsIgnoreCase("codice")) continue;
            String n = "Riga " + (i + 1) + ": ";
            if (c.length < 2) { scartate.add(n + "servono almeno codice e nome"); continue; }
            String cf = c.length > 2 ? c[2] : null;
            try {
                String codice = codice(c[0]);
                if (!visti.add(codice)) { scartate.add(n + "codice " + codice + " ripetuto nel file"); continue; }
                Optional<Organizzazione> esistente = organizzazioni.findByCodice(codice);
                if (esistente.isPresent() && !esistente.get().isEsterna()) {
                    scartate.add(n + "il codice " + codice + " è di una struttura interna");
                    continue;
                }
                Organizzazione o = esistente.orElseGet(Organizzazione::new);
                o.setCodice(codice);
                o.setTipo(Organizzazione.ESTERNA);
                applica(o, c[1], cf, esistente.map(Organizzazione::isAttiva).orElse(true));
                organizzazioni.save(o);
                if (esistente.isPresent()) aggiornate++; else create++;
            } catch (ResponseStatusException e) {
                scartate.add(n + e.getReason());
            }
        }
        registro.registra("ORGANIZZAZIONI_IMPORTATE", Objects.toString(file.getOriginalFilename(), "file CSV"),
            create + " create, " + aggiornate + " aggiornate, " + scartate.size() + " righe scartate");
        return new EsitoImport(create, aggiornate, scartate);
    }

    private static String codice(String grezzo) {
        String codice = grezzo == null ? "" : grezzo.trim().toLowerCase(Locale.ROOT);
        if (!CODICE.matcher(codice).matches())
            throw bad("Il codice è obbligatorio: da 2 a 60 caratteri tra lettere minuscole, cifre e trattini");
        return codice;
    }

    private static void applica(Organizzazione o, String nome, String codiceFiscale, Boolean attiva) {
        if (nome == null || nome.isBlank() || nome.trim().length() > 200)
            throw bad("Il nome è obbligatorio (al massimo 200 caratteri)");
        o.setNome(nome.trim());
        String cf = codiceFiscale == null ? "" : codiceFiscale.trim().toUpperCase(Locale.ROOT);
        if (cf.length() > 20) throw bad("Codice fiscale o partita IVA troppo lungo");
        o.setCodiceFiscale(cf.isEmpty() ? null : cf);
        o.setAttiva(attiva == null || attiva);
    }

    private Organizzazione trova(Long id) {
        return organizzazioni.findById(id).filter(Organizzazione::isEsterna)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organizzazione non trovata"));
    }

    private OrganizzazioneDto dto(Organizzazione o, long utenti) {
        return new OrganizzazioneDto(o.getId(), o.getCodice(), o.getNome(), o.getCodiceFiscale(), o.isAttiva(), utenti,
            o.getId() == null ? 0 : referenti.numeroReferenti(o.getId()));
    }

    private static String descrivi(Organizzazione o) {
        return o.getNome() + (o.getCodiceFiscale() == null ? "" : ", CF " + o.getCodiceFiscale()) + (o.isAttiva() ? "" : ", disattivata");
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
