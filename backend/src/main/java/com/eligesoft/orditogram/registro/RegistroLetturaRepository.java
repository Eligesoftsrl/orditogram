package com.eligesoft.orditogram.registro;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface RegistroLetturaRepository extends JpaRepository<RegistroLettura, Long> {

    /** Filtri facoltativi: tipo, esito, utente esatto, oggetto esatto (es. l'id della pratica), periodo (sempre indicato: il controller mette i limiti larghi). */
    @Query("""
        select r from RegistroLettura r
        where (:azione is null or r.azione = :azione)
          and (:esito is null or r.esito = :esito)
          and (:utente is null or lower(r.utente) = :utente)
          and (:oggetto is null or r.oggetto = :oggetto)
          and r.quando >= :dal and r.quando < :al
        order by r.quando desc, r.id desc
        """)
    Page<RegistroLettura> cerca(@Param("azione") String azione, @Param("esito") String esito,
                                @Param("utente") String utente, @Param("oggetto") String oggetto,
                                @Param("dal") OffsetDateTime dal, @Param("al") OffsetDateTime al, Pageable pageable);

    @Modifying
    @Query("delete from RegistroLettura r where r.quando < :limite")
    int eliminaPrimaDi(@Param("limite") OffsetDateTime limite);
}
