package com.eligesoft.orditogram.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    List<Webhook> findByAttivoTrue();
    List<Webhook> findAllByOrderByNomeAsc();
    Optional<Webhook> findByNome(String nome);
}
