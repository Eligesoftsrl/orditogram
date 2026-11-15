package com.eligesoft.orditogram.storico;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PraticaEventoRepository extends JpaRepository<PraticaEvento, Long> {

    List<PraticaEvento> findByProcessInstanceIdOrderByQuandoAscIdAsc(String processInstanceId);

    boolean existsByProcessInstanceIdAndUtente(String processInstanceId, String utente);

    Optional<PraticaEvento> findFirstByProcessInstanceIdAndTipoOrderByIdAsc(String processInstanceId,
                                                                           PraticaEvento.Tipo tipo);

    /** Pratiche in cui l'utente ha fatto almeno un'azione. */
    @Query("select distinct e.processInstanceId from PraticaEvento e where e.utente = :utente")
    Set<String> findPraticheDiUtente(@Param("utente") String utente);
}
