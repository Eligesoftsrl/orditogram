package com.eligesoft.orditogram.documento;

/**
 * Dove finiscono fisicamente i file. Il resto dell'applicazione (versioni, firme, storico)
 * conosce solo questa interfaccia: per passare a un archivio documentale (es. Alfresco via REST/CMIS,
 * oppure uno storage S3) basta una nuova implementazione, senza toccare processi e frontend.
 */
public interface ArchivioDocumenti {

    /** Salva il contenuto e restituisce un riferimento opaco da conservare nella versione. */
    String salva(byte[] data, String sha256);

    /** Rilegge il contenuto dal riferimento restituito da {@link #salva}. */
    byte[] leggi(String riferimento);
}
