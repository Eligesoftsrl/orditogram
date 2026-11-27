package com.eligesoft.orditogram.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UtenteProvaRepository extends JpaRepository<UtenteProva, String> {
    List<UtenteProva> findAllByOrderByOrdineAscUsernameAsc();
    List<UtenteProva> findByOrigineOrderByOrdineAsc(String origine);
}
