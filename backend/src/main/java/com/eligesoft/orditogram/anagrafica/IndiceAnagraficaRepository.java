package com.eligesoft.orditogram.anagrafica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IndiceAnagraficaRepository extends JpaRepository<IndiceAnagrafica, Long> {

    /** Un'altra scheda dello stesso tipo con lo stesso valore in un campo chiave. */
    Optional<IndiceAnagrafica> findFirstByTipoIdAndCampoAndChiaveAndSchedaIdNot(Long tipoId, String campo, String chiave, Long schedaId);

    Optional<IndiceAnagrafica> findFirstByTipoIdAndCampoAndChiave(Long tipoId, String campo, String chiave);

    /** Le relazioni verso una scheda: righe dei campi relazione (di qualsiasi tipo) che hanno il suo id. */
    List<IndiceAnagrafica> findByTipoIdInAndCampoInAndValore(Collection<Long> tipi, Collection<String> campi, String valore);

    @Modifying
    @Query("delete from IndiceAnagrafica i where i.schedaId = :scheda")
    void cancellaDellaScheda(@Param("scheda") Long scheda);

    @Modifying
    @Query("delete from IndiceAnagrafica i where i.tipoId = :tipo")
    void cancellaDelTipo(@Param("tipo") Long tipo);
}
