package com.eligesoft.orditogram.accesso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RuoloRepository extends JpaRepository<Ruolo, Long> {
    Optional<Ruolo> findByCodice(String codice);
    boolean existsByCodice(String codice);
    List<Ruolo> findByCodiceIn(Collection<String> codici);

    /** Ruoli assegnati ad almeno uno dei gruppi indicati (la lista non deve essere vuota). */
    @Query("select distinct r from Ruolo r join r.gruppi g where g in :gruppi")
    List<Ruolo> deiGruppi(@Param("gruppi") Collection<String> gruppi);
}
