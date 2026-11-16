package com.eligesoft.orditogram.anagrafica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermessoAnagraficaRepository extends JpaRepository<PermessoAnagrafica, Long> {
    List<PermessoAnagrafica> findByTipoIdOrderByAzioneAscTipoSoggettoAscSoggettoAsc(Long tipoId);
    List<PermessoAnagrafica> findByTipoSoggettoAndSoggettoIn(String tipoSoggetto, java.util.Collection<String> soggetti);

    @Modifying
    @Query("delete from PermessoAnagrafica p where p.tipoId = :tipo")
    void cancellaDelTipo(@Param("tipo") Long tipo);
}
