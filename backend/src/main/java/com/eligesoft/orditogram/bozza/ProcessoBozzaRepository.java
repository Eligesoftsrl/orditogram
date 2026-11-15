package com.eligesoft.orditogram.bozza;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessoBozzaRepository extends JpaRepository<ProcessoBozza, Long> {
    Optional<ProcessoBozza> findByChiave(String chiave);
    boolean existsByChiave(String chiave);
}
