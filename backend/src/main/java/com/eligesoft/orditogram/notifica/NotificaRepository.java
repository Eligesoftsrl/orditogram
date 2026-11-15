package com.eligesoft.orditogram.notifica;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificaRepository extends JpaRepository<Notifica, Long> {

    List<Notifica> findByUsernameOrderByCreataIlDesc(String username, Pageable page);

    long countByUsernameAndLettaFalse(String username);

    @Modifying
    @Query("update Notifica n set n.letta = true where n.username = :username and n.letta = false")
    int segnaTutteLette(@Param("username") String username);
}
