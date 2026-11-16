package com.eligesoft.orditogram.anagrafica;

import com.eligesoft.orditogram.accesso.RuoloRepository;
import com.eligesoft.orditogram.form.FormService;
import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Studio → Anagrafiche: tipi di anagrafica (definizione dei campi) e chi può vederne e modificarne le schede.
 * Ogni modifica va nel registro. Quando cambiano i campi chiave o ricercabili, l'indice delle schede si ricostruisce
 * nella stessa transazione: se un nuovo campo chiave ha valori ripetuti, la modifica è rifiutata.
 */
@Service
public class TipiAnagraficaService {

    private static final Set<String> AZIONI = AccessoAnagrafiche.TUTTE;
    private static final Set<String> SOGGETTI = Set.of(PermessoAnagrafica.GRUPPO, PermessoAnagrafica.RUOLO);

    /** Un tipo come lo scambia lo Studio. */
    public record Tipo(String codice, String nome, String descrizione, JsonNode definizione, Boolean attivo,
                       Integer versione, Long schede, OffsetDateTime modificatoIl, String modificatoDa) { }

    /** Una regola di accesso alle schede del tipo. */
    public record Regola(String azione, String tipoSoggetto, String soggetto) {
        String descrivi() { return azione + " " + tipoSoggetto.toLowerCase(Locale.ROOT) + " " + soggetto; }
    }

    private final TipoAnagraficaRepository tipi;
    private final SchedaAnagraficaRepository schede;
    private final IndiceAnagraficaRepository indice;
    private final PermessoAnagraficaRepository regole;
    private final RuoloRepository ruoli;
    private final FormService forms;
    private final RegistroService registro;
    private final CurrentUserService utente;
    private final ObjectMapper mapper;

    public TipiAnagraficaService(TipoAnagraficaRepository tipi, SchedaAnagraficaRepository schede,
                                 IndiceAnagraficaRepository indice, PermessoAnagraficaRepository regole,
                                 RuoloRepository ruoli, FormService forms, RegistroService registro,
                                 CurrentUserService utente, ObjectMapper mapper) {
        this.tipi = tipi;
        this.schede = schede;
        this.indice = indice;
        this.regole = regole;
        this.ruoli = ruoli;
        this.forms = forms;
        this.registro = registro;
        this.utente = utente;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ tipi

    @Transactional(readOnly = true)
    public List<Tipo> elenco() {
        return tipi.findAllByOrderByNomeAsc().stream().map(t -> dto(t, schede.countByTipo(t))).toList();
    }

    @Transactional(readOnly = true)
    public Tipo leggi(String codice) {
        TipoAnagrafica t = trova(codice);
        return dto(t, schede.countByTipo(t));
    }

    @Transactional
    public Tipo crea(Tipo nuovo) {
        if (nuovo == null) throw bad("Mancano i dati del tipo");
        String codice = nuovo.codice() == null ? "" : nuovo.codice().trim();
        if (!DefinizioneAnagrafica.CODICE.matcher(codice).matches())
            throw bad("Il codice deve iniziare con una lettera minuscola e contenere solo minuscole, numeri e trattini (2-60 caratteri)");
        if (tipi.existsByCodice(codice)) throw bad("Esiste già un'anagrafica con codice " + codice);
        DefinizioneAnagrafica def = definizione(codice, nuovo.definizione());
        TipoAnagrafica t = new TipoAnagrafica();
        t.setCodice(codice);
        applica(t, nuovo);
        t.setDefinizione(testo(def.json()));
        t.setVersione(1);
        OffsetDateTime ora = OffsetDateTime.now();
        t.setCreatoIl(ora);
        t.setCreatoDa(utente.username());
        t.setModificatoIl(ora);
        t.setModificatoDa(utente.username());
        TipoAnagrafica salvato = tipi.save(t);
        registro.registra("ANAGRAFICA_CREATA", codice, descrivi(salvato));
        return dto(salvato, 0);
    }

    @Transactional
    public Tipo modifica(String codice, Tipo nuovo) {
        if (nuovo == null) throw bad("Mancano i dati del tipo");
        TipoAnagrafica t = trova(codice);
        String prima = descrivi(t);
        DefinizioneAnagrafica vecchia = DefinizioneAnagrafica.di(leggiJson(t.getDefinizione()));
        applica(t, nuovo);
        long quante = schede.countByTipo(t);
        if (nuovo.definizione() != null && !nuovo.definizione().isNull()) {
            DefinizioneAnagrafica def = definizione(codice, nuovo.definizione());
            String testo = testo(def.json());
            if (!testo.equals(t.getDefinizione())) {
                if (quante > 0) controllaRelazioniInvariate(vecchia, def);
                t.setDefinizione(testo);
                t.setVersione(t.getVersione() + 1);
                if (!vecchia.indice().equals(def.indice())) ricostruisciIndice(t, def);
            }
        }
        t.setModificatoIl(OffsetDateTime.now());
        t.setModificatoDa(utente.username());
        TipoAnagrafica salvato = tipi.save(t);
        String dopo = descrivi(salvato);
        if (!prima.equals(dopo)) registro.registra("ANAGRAFICA_MODIFICATA", codice, "Prima: " + prima + "\nDopo: " + dopo);
        return dto(salvato, quante);
    }

    /** Si elimina solo un tipo senza schede e che nessun altro tipo collega; altrimenti si disattiva. */
    @Transactional
    public void elimina(String codice) {
        TipoAnagrafica t = trova(codice);
        if (schede.countByTipo(t) > 0) throw bad("L'anagrafica ha delle schede: disattivala invece di eliminarla");
        for (TipoAnagrafica altro : tipi.findAll())
            if (!altro.getId().equals(t.getId())
                && DefinizioneAnagrafica.di(leggiJson(altro.getDefinizione())).tipiCollegati().contains(codice))
                throw bad("L'anagrafica " + altro.getNome() + " ha un campo collegato a questa: togli prima il collegamento");
        List<String> usata = formCheLoUsano(codice);
        if (!usata.isEmpty())
            throw bad("Il form " + usata.get(0) + " ha un campo che sceglie da questa anagrafica: toglilo prima");
        regole.cancellaDelTipo(t.getId());
        tipi.delete(t);
        registro.registra("ANAGRAFICA_ELIMINATA", codice, descrivi(t));
    }

    // ------------------------------------------------------------ pacchetti

    /**
     * I tipi da mettere in un pacchetto: quelli indicati e quelli che collegano con i loro campi relazione, a catena.
     * Senza schede e senza regole di accesso, che dipendono dall'ambiente.
     */
    @Transactional(readOnly = true)
    public List<Tipo> perPacchetto(Collection<String> codici) {
        Map<String, Tipo> out = new LinkedHashMap<>();
        Deque<String> daVedere = new ArrayDeque<>(codici);
        while (!daVedere.isEmpty()) {
            String c = daVedere.pop();
            if (out.containsKey(c)) continue;
            tipi.findByCodice(c).ifPresent(t -> {
                JsonNode def = leggiJson(t.getDefinizione());
                out.put(c, new Tipo(t.getCodice(), t.getNome(), t.getDescrizione(), def, null, null, null, null, null));
                daVedere.addAll(DefinizioneAnagrafica.di(def).tipiCollegati());
            });
        }
        return List.copyOf(out.values());
    }

    /**
     * Crea i tipi di un pacchetto che qui non esistono; quelli già presenti restano come sono (la definizione di
     * un'anagrafica in uso non cambia per un'importazione). L'ordine segue le relazioni. Restituisce che cosa è
     * successo, in parole.
     */
    @Transactional
    public String importa(List<Tipo> dalPacchetto) {
        List<Tipo> nuovi = new ArrayList<>(dalPacchetto.stream().filter(t -> t.codice() != null && !tipi.existsByCodice(t.codice())).toList());
        List<String> mantenuti = dalPacchetto.stream().map(Tipo::codice).filter(c -> c != null && tipi.existsByCodice(c)).toList();
        List<String> creati = new ArrayList<>();
        boolean avanti = true;
        while (!nuovi.isEmpty() && avanti) {
            avanti = false;
            for (Iterator<Tipo> it = nuovi.iterator(); it.hasNext(); ) {
                Tipo t = it.next();
                boolean pronto = DefinizioneAnagrafica.di(t.definizione() == null ? mapper.createObjectNode() : t.definizione())
                    .tipiCollegati().stream().allMatch(c -> c.equals(t.codice()) || tipi.existsByCodice(c));
                if (!pronto) continue;
                crea(new Tipo(t.codice(), t.nome(), t.descrizione(), t.definizione(), true, null, null, null, null));
                creati.add(t.codice());
                it.remove();
                avanti = true;
            }
        }
        if (!nuovi.isEmpty())
            throw bad("Anagrafiche del pacchetto collegate ad anagrafiche che mancano: "
                + nuovi.stream().map(Tipo::codice).collect(Collectors.joining(", ")));
        List<String> parti = new ArrayList<>();
        if (!creati.isEmpty()) parti.add("create " + String.join(", ", creati));
        if (!mantenuti.isEmpty()) parti.add("mantenute quelle già presenti: " + String.join(", ", mantenuti));
        return String.join("; ", parti);
    }

    public boolean esiste(String codice) {
        return codice != null && tipi.existsByCodice(codice);
    }

    /** I codici dei tipi che i form indicati usano nei campi anagrafica. */
    public static Set<String> usatiDai(Collection<JsonNode> forms) {
        Set<String> out = new TreeSet<>();
        for (JsonNode f : forms)
            if (f != null) f.path("fields").forEach(c -> {
                if (com.eligesoft.orditogram.form.ValoriForm.ANAGRAFICA.equals(c.path("type").asText()))
                    out.add(c.path("tipoAnagrafica").asText());
            });
        return out;
    }

    // ------------------------------------------------------------ regole di accesso

    @Transactional(readOnly = true)
    public List<Regola> regole(String codice) {
        return regole.findByTipoIdOrderByAzioneAscTipoSoggettoAscSoggettoAsc(trova(codice).getId()).stream()
            .map(r -> new Regola(r.getAzione(), r.getTipoSoggetto(), r.getSoggetto())).toList();
    }

    /** Sostituisce tutte le regole del tipo. Rifiuta azioni e soggetti sconosciuti e ruoli inesistenti. */
    @Transactional
    public List<Regola> sostituisciRegole(String codice, List<Regola> nuove) {
        TipoAnagrafica t = trova(codice);
        Map<String, Regola> uniche = new LinkedHashMap<>();
        for (Regola r : nuove == null ? List.<Regola>of() : nuove) {
            if (r == null) continue;
            String azione = maiuscolo(r.azione()), tipo = maiuscolo(r.tipoSoggetto());
            String soggetto = r.soggetto() == null ? "" : r.soggetto().trim();
            if (!AZIONI.contains(azione)) throw bad("Azione sconosciuta: " + r.azione() + " (vede, modifica, esporta)");
            if (!SOGGETTI.contains(tipo)) throw bad("Indica se la regola vale per un gruppo o per un ruolo");
            if (soggetto.isEmpty() || soggetto.length() > 100) throw bad("Indica il gruppo o il ruolo (al massimo 100 caratteri)");
            uniche.put(azione + "|" + tipo + "|" + soggetto, new Regola(azione, tipo, soggetto));
        }
        Set<String> citati = uniche.values().stream().filter(r -> PermessoAnagrafica.RUOLO.equals(r.tipoSoggetto()))
            .map(Regola::soggetto).collect(Collectors.toCollection(TreeSet::new));
        ruoli.findByCodiceIn(citati).forEach(r -> citati.remove(r.getCodice()));
        if (!citati.isEmpty()) throw bad("Ruolo sconosciuto: " + String.join(", ", citati));

        String prima = descriviRegole(regole(codice));
        regole.cancellaDelTipo(t.getId());
        regole.flush();
        uniche.values().forEach(r -> regole.save(new PermessoAnagrafica(t.getId(), r.azione(), r.tipoSoggetto(), r.soggetto())));
        List<Regola> salvate = regole(codice);
        String dopo = descriviRegole(salvate);
        if (!prima.equals(dopo)) registro.registra("PERMESSI_ANAGRAFICA_MODIFICATI", codice, "Prima: " + prima + "\nDopo: " + dopo);
        return salvate;
    }

    // ------------------------------------------------------------ supporto

    /** I form (Studio e progetto) con un campo anagrafica di questo tipo. */
    private List<String> formCheLoUsano(String codice) {
        List<String> out = new ArrayList<>();
        try {
            for (Map<String, Object> f : forms.list()) {
                String chiave = String.valueOf(f.get("formKey"));
                if (usatiDai(Collections.singletonList(forms.load(chiave))).contains(codice)) out.add(chiave);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Form non leggibili", e);
        }
        return out;
    }

    TipoAnagrafica trova(String codice) {
        return tipi.findByCodice(codice == null ? "" : codice)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Anagrafica non trovata"));
    }

    private DefinizioneAnagrafica definizione(String codice, JsonNode json) {
        if (json == null || json.isNull()) throw bad("Mancano i campi dell'anagrafica");
        try {
            String testo = json.isTextual() ? json.asText() : mapper.writeValueAsString(json);
            return DefinizioneAnagrafica.controlla(forms, codice, testo, tipi::existsByCodice);
        } catch (JsonProcessingException e) {
            throw bad("La definizione dei campi non è un JSON valido");
        } catch (IllegalArgumentException e) {
            throw bad(e.getMessage());
        }
    }

    /** Con schede già salvate, un campo relazione non può cambiare l'anagrafica a cui punta (i dati non tornerebbero). */
    private static void controllaRelazioniInvariate(DefinizioneAnagrafica vecchia, DefinizioneAnagrafica nuova) {
        Map<String, String> prima = new HashMap<>();
        vecchia.indice().stream().filter(DefinizioneAnagrafica.CampoIndice::relazione)
            .forEach(c -> prima.put(c.nome(), c.tipoAnagrafica()));
        for (var c : nuova.indice())
            if (c.relazione() && prima.containsKey(c.nome()) && !prima.get(c.nome()).equals(c.tipoAnagrafica()))
                throw bad("Campo \"" + c.nome() + "\": ci sono già schede, non può puntare a un'altra anagrafica (crea un campo nuovo)");
    }

    /** Ricalcola l'indice di tutte le schede del tipo, controllando che i campi chiave non abbiano valori ripetuti. */
    private void ricostruisciIndice(TipoAnagrafica t, DefinizioneAnagrafica def) {
        List<IndiceAnagrafica> righe = new ArrayList<>();
        Map<String, SchedaAnagrafica> chiavi = new HashMap<>();
        for (SchedaAnagrafica s : schede.findByTipo(t)) {
            for (IndiceAnagrafica r : IndiciScheda.calcola(def, t.getId(), s.getId(), leggiMappa(s.getDati()))) {
                if (r.getChiave() != null) {
                    SchedaAnagrafica altra = chiavi.putIfAbsent(r.getCampo() + "|" + r.getChiave(), s);
                    if (altra != null)
                        throw bad("Campo \"" + r.getCampo() + "\": non può essere chiave, le schede \"" + altra.getTitolo()
                            + "\" e \"" + s.getTitolo() + "\" hanno lo stesso valore");
                }
                righe.add(r);
            }
        }
        indice.cancellaDelTipo(t.getId());
        indice.flush();
        indice.saveAll(righe);
    }

    private void applica(TipoAnagrafica t, Tipo dto) {
        if (dto.nome() != null || t.getNome() == null) {
            String nome = dto.nome() == null ? "" : dto.nome().trim();
            if (nome.isEmpty() || nome.length() > 200) throw bad("Indica il nome (al massimo 200 caratteri)");
            t.setNome(nome);
        }
        if (dto.descrizione() != null) {
            String d = dto.descrizione().trim();
            if (d.length() > 1000) throw bad("La descrizione è troppo lunga (al massimo 1000 caratteri)");
            t.setDescrizione(d.isEmpty() ? null : d);
        }
        if (dto.attivo() != null) t.setAttivo(dto.attivo());
    }

    private Tipo dto(TipoAnagrafica t, long quante) {
        return new Tipo(t.getCodice(), t.getNome(), t.getDescrizione(), leggiJson(t.getDefinizione()), t.isAttivo(),
            t.getVersione(), quante, t.getModificatoIl(), t.getModificatoDa());
    }

    private String descrivi(TipoAnagrafica t) {
        return t.getNome() + (t.isAttivo() ? "" : " (disattivata)") + ", versione " + t.getVersione()
            + ", campi: " + String.join(", ", DefinizioneAnagrafica.di(leggiJson(t.getDefinizione())).nomi());
    }

    private static String descriviRegole(List<Regola> regole) {
        return regole.isEmpty() ? "nessuna regola" : regole.stream().map(Regola::descrivi).sorted().collect(Collectors.joining("; "));
    }

    JsonNode leggiJson(String testo) {
        try {
            return mapper.readTree(testo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Definizione dell'anagrafica non leggibile", e);
        }
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> leggiMappa(String testo) {
        try {
            return mapper.readValue(testo, LinkedHashMap.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dati della scheda non leggibili", e);
        }
    }

    private String testo(JsonNode json) {
        try {
            return mapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String maiuscolo(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
