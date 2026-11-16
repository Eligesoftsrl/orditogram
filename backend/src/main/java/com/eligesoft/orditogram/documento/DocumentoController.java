package com.eligesoft.orditogram.documento;

import com.eligesoft.orditogram.storico.PraticaAccessService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Documenti delle pratiche: elenco, upload, nuove versioni, download, estrazione da busta p7m. */
@RestController
@RequestMapping("/api")
public class DocumentoController {

    private final DocumentoService documenti;
    private final FirmaService firme;
    private final PraticaAccessService access;
    private final com.eligesoft.orditogram.user.CurrentUserService currentUser;
    private final com.eligesoft.orditogram.form.FormService forms;

    public DocumentoController(DocumentoService documenti, FirmaService firme, PraticaAccessService access,
                               com.eligesoft.orditogram.user.CurrentUserService currentUser,
                               com.eligesoft.orditogram.form.FormService forms) {
        this.currentUser = currentUser;
        this.forms = forms;
        this.documenti = documenti;
        this.firme = firme;
        this.access = access;
    }

    @GetMapping("/pratiche/{pi}/documenti")
    public Map<String, Object> elenco(@PathVariable String pi) {
        access.checkView(pi);
        boolean esterno = currentUser.esterno();
        return Map.of(
            "documenti", documenti.elenco(pi, esterno),
            "modificabile", access.puoModificareDocumenti(pi),
            // chi può modificare i documenti (personale) decide anche quali vedono le parti
            "condivisibile", !esterno && access.puoModificareDocumenti(pi));
    }

    /** Nuovo documento: chi ha in carico (o può prendere) un task della pratica con "documenti": "modifica". */
    @PostMapping(value = "/pratiche/{pi}/documenti", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> aggiungi(@PathVariable String pi,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "nota", required = false) String nota) {
        var task = access.checkEditDocumenti(pi);
        var form = forms.load(task.getFormKey());
        boolean perLeParti = form != null && "parti".equals(form.path("documentiVisibilita").asText());
        var v = documenti.aggiungi(pi, file, nota, task.getName(), perLeParti);
        return Map.of("id", v.getId(), "firmaEsito", v.getFirmaEsito());
    }

    @PostMapping(value = "/documenti/{id}/versioni", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> nuovaVersione(@PathVariable Long id,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "nota", required = false) String nota) {
        Documento d = documenti.documento(id);
        controllaVisibile(d);
        var task = access.checkEditDocumenti(d.getProcessInstanceId());
        var v = documenti.nuovaVersione(id, file, nota, task.getName());
        return Map.of("id", v.getId(), "numero", v.getNumero(), "firmaEsito", v.getFirmaEsito());
    }

    /** Il documento diventa visibile alle parti (utenti esterni) o torna solo interno. Solo per il personale. */
    @PostMapping("/documenti/{id}/visibilita")
    public Map<String, Object> visibilita(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Documento d = documenti.documento(id);
        access.checkView(d.getProcessInstanceId());
        if (currentUser.esterno()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operazione non consentita");
        access.checkEditDocumenti(d.getProcessInstanceId());
        boolean parti = Documento.PARTI.equals(body.get("visibilita"));
        documenti.cambiaVisibilita(id, parti);
        return Map.of("id", id, "visibilita", parti ? Documento.PARTI : Documento.INTERNA);
    }

    /** Il file così come è stato caricato (per un .p7m: la busta firmata). */
    @GetMapping("/documenti/versioni/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        DocumentoVersione v = documenti.versione(id);
        access.checkView(v.getDocumento().getProcessInstanceId());
        controllaVisibile(v.getDocumento());
        return scarica(documenti.contenuto(v), v.getFilename());
    }

    /** Il documento originale estratto da una busta CAdES (.p7m), es. il PDF da leggere. */
    @GetMapping("/documenti/versioni/{id}/contenuto")
    public ResponseEntity<byte[]> contenuto(@PathVariable Long id) {
        DocumentoVersione v = documenti.versione(id);
        access.checkView(v.getDocumento().getProcessInstanceId());
        controllaVisibile(v.getDocumento());
        if (!"CADES".equals(v.getFirmaTipo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il file non è una busta CAdES");
        try {
            byte[] inner = firme.estraiContenuto(documenti.contenuto(v));
            return scarica(inner, FirmaService.nomeContenuto(v.getFilename()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    /** Un utente esterno vede solo i documenti visibili alle parti; per gli altri risponde come se non esistessero. */
    private void controllaVisibile(Documento d) {
        if (currentUser.esterno() && !d.visibileAlleParti())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento non trovato");
    }

    private static ResponseEntity<byte[]> scarica(byte[] data, String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        MediaType type = lower.endsWith(".pdf") ? MediaType.APPLICATION_PDF
            : lower.endsWith(".xml") ? MediaType.APPLICATION_XML
            : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
            .contentType(type)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
            .body(data);
    }
}
