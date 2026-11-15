package com.eligesoft.orditogram.webhook;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ConsegnaWebhookRepository extends JpaRepository<ConsegnaWebhook, Long> {

    /**
     * Consegne pronte per l'invio, bloccate per questa istanza di Orditogram. Con più istanze ognuna prende righe
     * diverse (SKIP LOCKED su PostgreSQL: il timeout -2 di Hibernate). I webhook sospesi restano in attesa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
        select c from ConsegnaWebhook c
        where c.stato in ('IN_CODA', 'DA_RIPETERE') and c.prossimoTentativo <= :ora
          and c.webhookId in (select w.id from Webhook w where w.attivo = true)
        order by c.prossimoTentativo asc, c.id asc
        """)
    List<ConsegnaWebhook> daInviare(@Param("ora") OffsetDateTime ora, Pageable pagina);

    @Query("""
        select c from ConsegnaWebhook c
        where (:webhookId is null or c.webhookId = :webhookId)
          and (:stato is null or c.stato = :stato)
          and (:tipo is null or c.tipo = :tipo)
          and (:pratica is null or c.pratica = :pratica)
        order by c.id desc
        """)
    Page<ConsegnaWebhook> cerca(@Param("webhookId") Long webhookId, @Param("stato") String stato,
                                @Param("tipo") String tipo, @Param("pratica") String pratica, Pageable pagina);

    /** Consegne rimaste "in invio" (es. Orditogram si è fermato durante la chiamata): si riprovano. */
    @Modifying
    @Query("update ConsegnaWebhook c set c.stato = 'DA_RIPETERE', c.prossimoTentativo = :ora where c.stato = 'IN_INVIO' and c.ultimoTentativo < :limite")
    int sblocca(@Param("limite") OffsetDateTime limite, @Param("ora") OffsetDateTime ora);

    @Modifying
    @Query("delete from ConsegnaWebhook c where c.stato in ('CONSEGNATA', 'FALLITA') and c.creata < :limite")
    int eliminaVecchie(@Param("limite") OffsetDateTime limite);

    @Modifying
    @Query("delete from ConsegnaWebhook c where c.webhookId = :webhookId")
    int eliminaDelWebhook(@Param("webhookId") Long webhookId);

    long countByWebhookIdAndStato(Long webhookId, String stato);

    @Query("select max(c.ultimoTentativo) from ConsegnaWebhook c where c.webhookId = :webhookId and c.stato = 'CONSEGNATA'")
    OffsetDateTime ultimaConsegna(@Param("webhookId") Long webhookId);

    ConsegnaWebhook findFirstByWebhookIdAndUltimoTentativoNotNullOrderByUltimoTentativoDesc(Long webhookId);
}
