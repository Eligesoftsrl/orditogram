package com.eligesoft.orditogram.accesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizzazioneRepository extends JpaRepository<Organizzazione, Long> {
    boolean existsByCodice(String codice);
    Optional<Organizzazione> findByCodice(String codice);
    List<Organizzazione> findByPadreId(Long padreId);
    List<Organizzazione> findByTipoOrderByNomeAsc(String tipo);
}
