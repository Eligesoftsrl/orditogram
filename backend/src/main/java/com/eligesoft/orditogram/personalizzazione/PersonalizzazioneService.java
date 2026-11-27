package com.eligesoft.orditogram.personalizzazione;

import com.eligesoft.orditogram.registro.RegistroService;
import com.eligesoft.orditogram.user.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Nome dell'applicazione e logo del cliente. Senza personalizzazione l'applicazione si chiama Orditogram e
 * mostra il suo marchio. Lo legge chiunque (pagina di accesso compresa, da /api/info); lo cambia solo
 * l'amministratore, e ogni modifica va nel registro.
 */
@Service
public class PersonalizzazioneService {

    public static final String NOME_PREDEFINITO = "Orditogram";
    /** Dimensione massima del logo: basta per un'immagine nitida nell'intestazione. */
    public static final int LOGO_MASSIMO = 512 * 1024;

    /** Lettere, cifre, spazi e poca punteggiatura: niente markup né caratteri di controllo. */
    private static final Pattern NOME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{M}\\p{N} '’.,&()\\-]{0,59}");

    public record Logo(String tipo, byte[] contenuto, long versione) { }

    private final PersonalizzazioneRepository repo;
    private final RegistroService registro;
    private final CurrentUserService utente;

    public PersonalizzazioneService(PersonalizzazioneRepository repo, RegistroService registro, CurrentUserService utente) {
        this.repo = repo;
        this.registro = registro;
        this.utente = utente;
    }

    /** Per l'interfaccia: nome in uso, se è quello predefinito, indirizzo del logo (null = marchio di Orditogram). */
    @Transactional(readOnly = true)
    public Map<String, Object> stato() {
        Personalizzazione p = repo.findById(Personalizzazione.RIGA).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        boolean conNome = p != null && p.getNome() != null;
        m.put("nome", conNome ? p.getNome() : NOME_PREDEFINITO);
        m.put("nomePredefinito", !conNome);
        m.put("logo", p != null && p.getLogo() != null
            ? "/api/info/logo?v=" + p.getAggiornataIl().toInstant().toEpochMilli() : null);
        return m;
    }

    @Transactional(readOnly = true)
    public Optional<Logo> logo() {
        return repo.findById(Personalizzazione.RIGA).filter(p -> p.getLogo() != null)
            .map(p -> new Logo(p.getLogoTipo(), Base64.getDecoder().decode(p.getLogo()),
                p.getAggiornataIl().toInstant().toEpochMilli()));
    }

    /** Nome dell'applicazione; vuoto o "Orditogram" = torna quello predefinito. */
    @Transactional
    public Map<String, Object> salvaNome(String nome) {
        String n = nome == null ? "" : nome.strip().replaceAll("\\s+", " ");
        if (!n.isEmpty() && !NOME.matcher(n).matches())
            throw bad("Il nome va da 1 a 60 caratteri: lettere, cifre, spazi e la punteggiatura più comune");
        Personalizzazione p = riga();
        String nuovo = n.isEmpty() || n.equals(NOME_PREDEFINITO) ? null : n;
        if (!Objects.equals(nuovo, p.getNome())) {
            p.setNome(nuovo);
            salva(p);
            registro.registra("PERSONALIZZAZIONE_MODIFICATA", "nome", nuovo == null ? "nome predefinito" : "nome = " + nuovo);
        }
        return stato();
    }

    /** Logo in PNG, JPEG o WebP (il tipo si legge dal contenuto, non dal nome del file). SVG no: può contenere codice. */
    @Transactional
    public Map<String, Object> salvaLogo(byte[] contenuto) {
        if (contenuto == null || contenuto.length == 0) throw bad("Scegli un'immagine");
        if (contenuto.length > LOGO_MASSIMO) throw bad("Il logo è troppo grande: al massimo 512 KB");
        String tipo = tipoImmagine(contenuto)
            .orElseThrow(() -> bad("Il logo deve essere un'immagine PNG, JPEG o WebP"));
        Personalizzazione p = riga();
        p.setLogo(Base64.getEncoder().encodeToString(contenuto));
        p.setLogoTipo(tipo);
        salva(p);
        registro.registra("PERSONALIZZAZIONE_MODIFICATA", "logo", "logo nuovo (" + tipo + ", " + contenuto.length + " byte)");
        return stato();
    }

    @Transactional
    public Map<String, Object> togliLogo() {
        Personalizzazione p = repo.findById(Personalizzazione.RIGA).orElse(null);
        if (p != null && p.getLogo() != null) {
            p.setLogo(null);
            p.setLogoTipo(null);
            salva(p);
            registro.registra("PERSONALIZZAZIONE_MODIFICATA", "logo", "logo predefinito");
        }
        return stato();
    }

    static Optional<String> tipoImmagine(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return Optional.of("image/png");
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return Optional.of("image/jpeg");
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return Optional.of("image/webp");
        return Optional.empty();
    }

    private Personalizzazione riga() {
        return repo.findById(Personalizzazione.RIGA).orElseGet(Personalizzazione::new);
    }

    private void salva(Personalizzazione p) {
        p.setAggiornataIl(OffsetDateTime.now());
        p.setAggiornataDa(utente.username());
        repo.save(p);
    }

    private static ResponseStatusException bad(String messaggio) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, messaggio);
    }
}
