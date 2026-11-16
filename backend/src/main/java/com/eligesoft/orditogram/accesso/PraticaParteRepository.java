package com.eligesoft.orditogram.accesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PraticaParteRepository extends JpaRepository<PraticaParte, Long> {
    List<PraticaParte> findByProcessInstanceIdOrderByIdAsc(String processInstanceId);
    Optional<PraticaParte> findByProcessInstanceIdAndRuolo(String processInstanceId, String ruolo);
    boolean existsByOrganizzazioneId(Long organizzazioneId);
}
