package com.eligesoft.orditogram.registro;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RegistroErroreRepository extends JpaRepository<RegistroErrore, Long> {

    Optional<RegistroErrore> findByCodice(String codice);

    /** Filtri facoltativi: origine e testo (codice, utente, richiesta, tipo, messaggio). */
    @Query("""
        select r from RegistroErrore r
        where (:origine is null or r.origine = :origine)
          and (:testo is null
               or lower(r.codice) like :testo
               or lower(coalesce(r.utente, '')) like :testo
               or lower(coalesce(r.richiesta, '')) like :testo
               or lower(coalesce(r.tipo, '')) like :testo
               or lower(coalesce(r.messaggio, '')) like :testo)
        order by r.quando desc, r.id desc
        """)
    Page<RegistroErrore> cerca(@Param("origine") String origine, @Param("testo") String testo, Pageable pageable);

    @Modifying
    @Query("delete from RegistroErrore r where r.quando < :limite")
    int eliminaPrimaDi(@Param("limite") OffsetDateTime limite);
}
