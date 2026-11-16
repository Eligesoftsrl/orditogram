package com.eligesoft.orditogram.anagrafica;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VersioneSchedaRepository extends JpaRepository<VersioneScheda, Long> {
    List<VersioneScheda> findBySchedaIdOrderByNumeroDesc(Long schedaId);
    long countBySchedaId(Long schedaId);
}
