package com.eligesoft.orditogram.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ChiaveApiRepository extends JpaRepository<ChiaveApi, Long> {

    List<ChiaveApi> findByPrefisso(String prefisso);

    Optional<ChiaveApi> findByNome(String nome);

    List<ChiaveApi> findAllByOrderByRevocataAscNomeAsc();

    @Modifying
    @Query("update ChiaveApi c set c.ultimoUso = :quando, c.ultimoIndirizzo = :indirizzo where c.id = :id")
    int segnaUso(@Param("id") Long id, @Param("quando") OffsetDateTime quando, @Param("indirizzo") String indirizzo);
}
