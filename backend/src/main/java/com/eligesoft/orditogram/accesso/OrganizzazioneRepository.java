package com.eligesoft.orditogram.accesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizzazioneRepository extends JpaRepository<Organizzazione, Long> {
    boolean existsByCodice(String codice);
    List<Organizzazione> findByPadreId(Long padreId);
}
