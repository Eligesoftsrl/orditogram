package com.eligesoft.orditogram.anagrafica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TipoAnagraficaRepository extends JpaRepository<TipoAnagrafica, Long> {
    Optional<TipoAnagrafica> findByCodice(String codice);
    boolean existsByCodice(String codice);
    List<TipoAnagrafica> findAllByOrderByNomeAsc();
}
