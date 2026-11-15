package com.eligesoft.orditogram.registro;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistroAzioneRepository extends JpaRepository<RegistroAzione, Long> {

    /** Filtri facoltativi: testo (in utente, oggetto, dettagli) e tipo di azione. */
    @Query("""
        select r from RegistroAzione r
        where (:azione is null or r.azione = :azione)
          and (:testo is null
               or lower(coalesce(r.utente, '')) like :testo
               or lower(coalesce(r.utenteNome, '')) like :testo
               or lower(coalesce(r.oggetto, '')) like :testo
               or lower(coalesce(r.dettagli, '')) like :testo)
        order by r.quando desc, r.id desc
        """)
    Page<RegistroAzione> cerca(@Param("azione") String azione, @Param("testo") String testo, Pageable pageable);
}
