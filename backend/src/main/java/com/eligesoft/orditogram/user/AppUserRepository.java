package com.eligesoft.orditogram.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
    Optional<AppUser> findByOidcSoggetto(String oidcSoggetto);
    /** Utenti creati da un referente, ancora da collegare a un'identità del provider. */
    java.util.List<AppUser> findByEmailIgnoreCaseAndCreatoDaIsNotNullAndOidcSoggettoIsNull(String email);
    @org.springframework.data.jpa.repository.Query("select u from AppUser u join u.organizzazioni o where o = :organizzazioneId order by u.lastName, u.firstName, u.username")
    java.util.List<AppUser> membriDi(@org.springframework.data.repository.query.Param("organizzazioneId") Long organizzazioneId);
    long countByCreatoDaAndCreatoIlAfter(String creatoDa, java.time.OffsetDateTime dopo);
}
