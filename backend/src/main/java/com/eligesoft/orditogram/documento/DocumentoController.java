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

    public DocumentoController(DocumentoService documenti, FirmaService firme, PraticaAccessService access) {
        this.documenti = documenti;
        this.firme = firme;
        this.access = access;
    }

    @GetMapping("/pratiche/{pi}/documenti")
    public Map<String, Object> elenco(@PathVariable String pi) {
        access.checkView(pi);
        return Map.of(
            "documenti", documenti.elenco(pi),
            "modificabile", access.puoModificareDocumenti(pi));
    }

    /** Nuovo documento: chi ha in carico (o può prendere) un task della pratica con "documenti": "modifica". */
    @PostMapping(value = "/pratiche/{pi}/documenti", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> aggiungi(@PathVariable String pi,
                                        @RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "nota", required = false) String nota) {
        var task = access.checkEditDocumenti(pi);
        var v = documenti.aggiungi(pi, file, nota, task.getName());
        return Map.of("id", v.getId(), "firmaEsito", v.getFirmaEsito());
    }

    @PostMapping(value = "/documenti/{id}/versioni", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> nuovaVersione(@PathVariable Long id,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "nota", required = false) String nota) {
        Documento d = documenti.documento(id);
        var task = access.checkEditDocumenti(d.getProcessInstanceId());
        var v = documenti.nuovaVersione(id, file, nota, task.getName());
        return Map.of("id", v.getId(), "numero", v.getNumero(), "firmaEsito", v.getFirmaEsito());
    }

    /** Il file così come è stato caricato (per un .p7m: la busta firmata). */
    @GetMapping("/documenti/versioni/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        DocumentoVersione v = documenti.versione(id);
        access.checkView(v.getDocumento().getProcessInstanceId());
        return scarica(documenti.contenuto(v), v.getFilename());
    }

    /** Il documento originale estratto da una busta CAdES (.p7m), es. il PDF da leggere. */
    @GetMapping("/documenti/versioni/{id}/contenuto")
    public ResponseEntity<byte[]> contenuto(@PathVariable Long id) {
        DocumentoVersione v = documenti.versione(id);
        access.checkView(v.getDocumento().getProcessInstanceId());
        if (!"CADES".equals(v.getFirmaTipo()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il file non è una busta CAdES");
        try {
            byte[] inner = firme.estraiContenuto(documenti.contenuto(v));
            return scarica(inner, FirmaService.nomeContenuto(v.getFilename()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
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
