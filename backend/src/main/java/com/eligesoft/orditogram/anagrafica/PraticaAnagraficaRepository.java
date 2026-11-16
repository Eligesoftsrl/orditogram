package com.eligesoft.orditogram.anagrafica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PraticaAnagraficaRepository extends JpaRepository<PraticaAnagrafica, Long> {
    List<PraticaAnagrafica> findByProcessInstanceIdOrderByCampoAsc(String processInstanceId);
    Optional<PraticaAnagrafica> findByProcessInstanceIdAndCampo(String processInstanceId, String campo);
    List<PraticaAnagrafica> findBySchedaIdOrderByCollegataIlDesc(Long schedaId);
}
