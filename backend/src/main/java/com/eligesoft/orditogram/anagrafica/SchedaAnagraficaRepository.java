package com.eligesoft.orditogram.anagrafica;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SchedaAnagraficaRepository extends JpaRepository<SchedaAnagrafica, Long> {

    long countByTipo(TipoAnagrafica tipo);

    List<SchedaAnagrafica> findByTipo(TipoAnagrafica tipo);

    List<SchedaAnagrafica> findByIdIn(Collection<Long> ids);

    /**
     * Ricerca paginata: stato (null = tutti), testo nel titolo o nei valori ricercabili (null = tutte),
     * campo e valore esatti nell'indice (null = nessun filtro). Il testo arriva già con i caratteri speciali di LIKE
     * protetti (carattere di escape "\").
     */
    @Query("""
        select s from SchedaAnagrafica s
        where s.tipo = :tipo
          and (:stato is null or s.stato = :stato)
          and (:testo is null
               or lower(s.titolo) like :testo escape '\\'
               or exists (select i.id from IndiceAnagrafica i where i.schedaId = s.id and i.valore like :testo escape '\\'))
          and (:campo is null
               or exists (select j.id from IndiceAnagrafica j where j.schedaId = s.id and j.campo = :campo and j.valore = :valore))
        """)
    Page<SchedaAnagrafica> cerca(@Param("tipo") TipoAnagrafica tipo, @Param("stato") String stato,
                                 @Param("testo") String testo, @Param("campo") String campo,
                                 @Param("valore") String valore, Pageable pagina);
}
