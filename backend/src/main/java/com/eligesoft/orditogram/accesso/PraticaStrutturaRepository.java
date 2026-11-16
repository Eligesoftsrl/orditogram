package com.eligesoft.orditogram.accesso;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PraticaStrutturaRepository extends JpaRepository<PraticaStruttura, String> {
    boolean existsByOrganizzazioneId(Long organizzazioneId);
}
