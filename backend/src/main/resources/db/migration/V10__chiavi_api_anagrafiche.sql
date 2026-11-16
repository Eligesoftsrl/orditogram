-- Anagrafiche a cui una chiave dell'API pubblica può accedere (codici separati da virgola; vuoto = nessuna)
ALTER TABLE chiave_api ADD COLUMN anagrafiche VARCHAR(2000);
