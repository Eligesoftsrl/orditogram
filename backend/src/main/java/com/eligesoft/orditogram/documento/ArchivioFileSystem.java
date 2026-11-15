package com.eligesoft.orditogram.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

/**
 * Archivio su disco. Ogni file è salvato una sola volta, con nome = SHA-256 del contenuto
 * (cartella documenti/ab/abcdef...). I metadati e le versioni stanno su Postgres.
 */
@Service
public class ArchivioFileSystem implements ArchivioDocumenti {

    private final Path root;

    public ArchivioFileSystem(@Value("${app.documenti.dir:./data/documenti}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String salva(byte[] data, String sha256) {
        String relative = sha256.substring(0, 2) + "/" + sha256;
        Path target = root.resolve(relative);
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target.getParent());
                Path tmp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
                Files.write(tmp, data);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            return relative;
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile salvare il file", e);
        }
    }

    @Override
    public byte[] leggi(String riferimento) {
        Path p = root.resolve(riferimento).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Percorso non valido");
        try {
            return Files.readAllBytes(p);
        } catch (IOException e) {
            throw new UncheckedIOException("File non trovato in archivio: " + riferimento, e);
        }
    }
}
