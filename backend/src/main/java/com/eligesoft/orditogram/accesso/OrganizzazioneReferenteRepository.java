package com.eligesoft.orditogram.accesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrganizzazioneReferenteRepository extends JpaRepository<OrganizzazioneReferente, Long> {
    List<OrganizzazioneReferente> findByOrganizzazioneId(Long organizzazioneId);
    List<OrganizzazioneReferente> findByUserId(Long userId);
    boolean existsByOrganizzazioneIdAndUserId(Long organizzazioneId, Long userId);
    long countByOrganizzazioneId(Long organizzazioneId);
}
