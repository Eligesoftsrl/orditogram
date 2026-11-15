package com.eligesoft.orditogram.documento;

import com.eligesoft.orditogram.storico.PraticaEvento;
import com.eligesoft.orditogram.storico.StoricoService;
import com.eligesoft.orditogram.user.CurrentUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.*;

/** Caricamento, versionamento e lettura dei documenti di una pratica. */
@Service
public class DocumentoService {

    private static final Set<String> ESTENSIONI = Set.of(
        "pdf", "p7m", "xml", "txt", "csv", "doc", "docx", "xls", "xlsx", "odt", "ods",
        "jpg", "jpeg", "png", "zip", "eml", "msg");

    private final DocumentoRepository documenti;
    private final DocumentoVersioneRepository versioni;
    private final ArchivioDocumenti storage;
    private final FirmaService firme;
    private final StoricoService storico;
    private final CurrentUserService currentUser;
    private final ObjectMapper mapper;

    public DocumentoService(DocumentoRepository documenti, DocumentoVersioneRepository versioni,
                            ArchivioDocumenti storage, FirmaService firme, StoricoService storico,
                            CurrentUserService currentUser, ObjectMapper mapper) {
        this.documenti = documenti;
        this.versioni = versioni;
        this.storage = storage;
        this.firme = firme;
        this.storico = storico;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------ scrittura

    /** Nuovo documento (versione 1). */
    @Transactional
    public DocumentoVersione aggiungi(String processInstanceId, MultipartFile file, String nota, String fase) {
        Documento d = new Documento();
        d.setProcessInstanceId(processInstanceId);
        d.setNome(nomeFile(file));
        d.setCreatoDa(currentUser.username());
        d.setCreatoIl(OffsetDateTime.now());
        d.setVersioneCorrente(1);
        documenti.save(d);

        DocumentoVersione v = creaVersione(d, 1, file, nota, fase);
        storico.registra(processInstanceId, PraticaEvento.Tipo.DOCUMENTO,
            "Documento caricato: " + d.getNome(), dettagli(v));
        return v;
    }

    /** Nuova versione di un documento esistente. */
    @Transactional
    public DocumentoVersione nuovaVersione(Long documentoId, MultipartFile file, String nota, String fase) {
        Documento d = documento(documentoId);
        DocumentoVersione corrente = d.getVersioni().stream()
            .filter(x -> x.getNumero() == d.getVersioneCorrente()).findFirst().orElse(null);
        byte[] bytes = leggi(file);
        if (corrente != null && sha256(bytes).equals(corrente.getSha256()))
            throw bad("Il file è identico alla versione corrente (v" + corrente.getNumero() + ")");

        int numero = d.getVersioneCorrente() + 1;
        DocumentoVersione v = creaVersione(d, numero, file, nota, fase);
        d.setVersioneCorrente(numero);
        documenti.save(d);
        storico.registra(d.getProcessInstanceId(), PraticaEvento.Tipo.DOCUMENTO,
            "Nuova versione (v" + numero + ") di " + d.getNome(), dettagli(v));
        return v;
    }

    private DocumentoVersione creaVersione(Documento d, int numero, MultipartFile file, String nota, String fase) {
        String filename = nomeFile(file);
        byte[] bytes = leggi(file);
        if (bytes.length == 0) throw bad("Il file " + filename + " è vuoto");
        String sha = sha256(bytes);
        FirmaService.Report report = firme.analizza(bytes, filename);

        DocumentoVersione v = new DocumentoVersione();
        v.setDocumento(d);
        v.setNumero(numero);
        v.setFilename(filename);
        v.setContentType(file.getContentType());
        v.setDimensione(bytes.length);
        v.setSha256(sha);
        v.setPercorso(storage.salva(bytes, sha));
        v.setCaricatoDa(currentUser.username());
        v.setCaricatoDaNome(currentUser.fullNameOf(currentUser.username()));
        v.setCaricatoIl(OffsetDateTime.now());
        v.setFase(fase);
        v.setNota(nota == null || nota.isBlank() ? null : nota.trim());
        v.setFirmaTipo(report.tipo());
        v.setFirmaEsito(report.esito());
        try {
            v.setFirmaReport(mapper.writeValueAsString(report));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        d.getVersioni().add(0, v);
        return versioni.save(v);
    }

    // ------------------------------------------------------------ lettura

    @Transactional(readOnly = true)
    public List<Map<String, Object>> elenco(String processInstanceId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Documento d : documenti.findByProcessInstanceIdOrderByIdAsc(processInstanceId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("nome", d.getNome());
            m.put("versioneCorrente", d.getVersioneCorrente());
            m.put("versioni", d.getVersioni().stream().map(this::toMap).toList());
            out.add(m);
        }
        return out;
    }

    /** Un file pronto da allegare (es. a un'email). */
    public record Allegato(String nome, String contentType, byte[] contenuto) {}

    /**
     * Ultima versione di ogni documento della pratica, con il contenuto.
     * Transazione propria: si usa anche dopo il commit (invio delle email).
     */
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public List<Allegato> allegatiCorrenti(String processInstanceId) {
        List<Allegato> out = new ArrayList<>();
        for (Documento d : documenti.findByProcessInstanceIdOrderByIdAsc(processInstanceId)) {
            d.getVersioni().stream()
                .filter(v -> v.getNumero() == d.getVersioneCorrente())
                .findFirst()
                .ifPresent(v -> out.add(new Allegato(v.getFilename(),
                    v.getContentType() != null ? v.getContentType() : "application/octet-stream",
                    storage.leggi(v.getPercorso()))));
        }
        return out;
    }

    public Documento documento(Long id) {
        return documenti.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento non trovato"));
    }

    public DocumentoVersione versione(Long id) {
        return versioni.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Versione non trovata"));
    }

    public byte[] contenuto(DocumentoVersione v) {
        return storage.leggi(v.getPercorso());
    }

    private Map<String, Object> toMap(DocumentoVersione v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("numero", v.getNumero());
        m.put("filename", v.getFilename());
        m.put("dimensione", v.getDimensione());
        m.put("sha256", v.getSha256());
        m.put("caricatoDa", v.getCaricatoDaNome());
        m.put("caricatoIl", v.getCaricatoIl());
        m.put("fase", v.getFase());
        m.put("nota", v.getNota());
        m.put("firmaTipo", v.getFirmaTipo());
        m.put("firmaEsito", v.getFirmaEsito());
        try {
            m.put("firma", v.getFirmaReport() == null ? null : mapper.readTree(v.getFirmaReport()));
        } catch (JsonProcessingException e) {
            m.put("firma", null);
        }
        return m;
    }

    // ------------------------------------------------------------ supporto

    private List<Map<String, Object>> dettagli(DocumentoVersione v) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(Map.of("label", "File", "value", v.getFilename()));
        out.add(Map.of("label", "Firma", "value", switch (v.getFirmaEsito()) {
            case "OK" -> "valida (" + v.getFirmaTipo() + ")";
            case "KO" -> "NON valida (" + v.getFirmaTipo() + ")";
            case "ATTENZIONE" -> "da controllare (" + v.getFirmaTipo() + ")";
            default -> "nessuna";
        }));
        if (v.getNota() != null) out.add(Map.of("label", "Nota", "value", v.getNota()));
        return out;
    }

    private static String nomeFile(MultipartFile file) {
        String n = Optional.ofNullable(file.getOriginalFilename()).orElse("documento");
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();          // niente percorsi
        if (n.isEmpty()) n = "documento";
        int dot = n.lastIndexOf('.');
        String ext = dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ESTENSIONI.contains(ext))
            throw bad("Tipo di file non ammesso: " + n + " (ammessi: " + String.join(", ", new TreeSet<>(ESTENSIONI)) + ")");
        return n;
    }

    private static byte[] leggi(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw bad("Impossibile leggere il file caricato");
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
