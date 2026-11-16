package com.eligesoft.orditogram.accesso;

import com.eligesoft.orditogram.registro.RegistroService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Lettura e modifica delle regole per processo, con i controlli e il registro (Studio e pacchetti). */
@Service
public class RegolePermessiProcesso {

    private static final Set<String> AZIONI = Set.of(PermessoProcesso.AVVIA, PermessoProcesso.VEDE, PermessoProcesso.ESPORTA);
    private static final Set<String> TIPI = Set.of(PermessoProcesso.GRUPPO, PermessoProcesso.RUOLO);
    private static final Set<String> AMBITI = Set.of(PermessoProcesso.TUTTE, PermessoProcesso.STRUTTURA,
        PermessoProcesso.STRUTTURA_E_SOTTO);

    /** Una regola come la scambiano Studio e pacchetti. */
    public record Regola(String azione, String tipoSoggetto, String soggetto, String ambito) {
        static Regola of(PermessoProcesso p) {
            return new Regola(p.getAzione(), p.getTipoSoggetto(), p.getSoggetto(), p.getAmbito());
        }

        String descrivi() {
            return azione + " " + tipoSoggetto.toLowerCase(Locale.ROOT) + " " + soggetto
                + (PermessoProcesso.TUTTE.equals(ambito) ? "" : " (" + ambito.toLowerCase(Locale.ROOT) + ")");
        }
    }

    private final PermessoProcessoRepository repo;
    private final RuoloRepository ruoli;
    private final RegistroService registro;

    public RegolePermessiProcesso(PermessoProcessoRepository repo, RuoloRepository ruoli, RegistroService registro) {
        this.repo = repo;
        this.ruoli = ruoli;
        this.registro = registro;
    }

    @Transactional(readOnly = true)
    public List<Regola> elenco(String processo) {
        return repo.findByProcessoOrderByAzioneAscTipoSoggettoAscSoggettoAsc(processo).stream().map(Regola::of).toList();
    }

    /** Sostituisce tutte le regole del processo. Rifiuta azioni, tipi e ambiti sconosciuti e ruoli inesistenti. */
    @Transactional
    public List<Regola> sostituisci(String processo, List<Regola> nuove) {
        if (processo == null || processo.isBlank() || processo.length() > 255) throw bad("Processo non valido");
        List<Regola> pulite = normalizza(nuove);
        String prima = descrivi(elenco(processo));
        repo.deleteByProcesso(processo);
        repo.flush();
        pulite.forEach(r -> repo.save(new PermessoProcesso(processo, r.azione(), r.tipoSoggetto(), r.soggetto(), r.ambito())));
        String dopo = descrivi(pulite);
        if (!prima.equals(dopo))
            registro.registra("PERMESSI_PROCESSO_MODIFICATI", processo, "Prima: " + prima + "\nDopo: " + dopo);
        return elenco(processo);
    }

    /** Ruoli citati dalle regole che non esistono in questa installazione (es. pacchetto da un altro ambiente). */
    public Set<String> ruoliMancanti(List<Regola> regole) {
        Set<String> citati = regole.stream()
            .filter(r -> PermessoProcesso.RUOLO.equals(maiuscolo(r.tipoSoggetto())) && r.soggetto() != null && !r.soggetto().isBlank())
            .map(r -> r.soggetto().trim()).collect(Collectors.toCollection(TreeSet::new));
        ruoli.findByCodiceIn(citati).forEach(r -> citati.remove(r.getCodice()));
        return citati;
    }

    private List<Regola> normalizza(List<Regola> nuove) {
        Map<String, Regola> uniche = new LinkedHashMap<>();
        for (Regola r : nuove == null ? List.<Regola>of() : nuove) {
            String azione = maiuscolo(r.azione());
            String tipo = maiuscolo(r.tipoSoggetto());
            String ambito = r.ambito() == null || r.ambito().isBlank() ? PermessoProcesso.TUTTE : maiuscolo(r.ambito());
            String soggetto = r.soggetto() == null ? "" : r.soggetto().trim();
            if (!AZIONI.contains(azione)) throw bad("Azione sconosciuta: " + r.azione());
            if (!TIPI.contains(tipo)) throw bad("Indica se la regola vale per un gruppo o per un ruolo");
            if (!AMBITI.contains(ambito)) throw bad("Ambito sconosciuto: " + r.ambito());
            if (soggetto.isEmpty() || soggetto.length() > 100) throw bad("Indica il gruppo o il ruolo (al massimo 100 caratteri)");
            if (!PermessoProcesso.VEDE.equals(azione) && !PermessoProcesso.TUTTE.equals(ambito))
                throw bad("L'ambito per struttura vale solo per \"vede tutte le pratiche\"");
            uniche.put(azione + "|" + tipo + "|" + soggetto, new Regola(azione, tipo, soggetto, ambito));
        }
        List<Regola> pulite = new ArrayList<>(uniche.values());
        Set<String> mancanti = ruoliMancanti(pulite);
        if (!mancanti.isEmpty()) throw bad("Ruolo sconosciuto: " + String.join(", ", mancanti));
        return pulite;
    }

    private static String descrivi(List<Regola> regole) {
        return regole.isEmpty() ? "nessuna regola" : regole.stream().map(Regola::descrivi).sorted().collect(Collectors.joining("; "));
    }

    private static String maiuscolo(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
