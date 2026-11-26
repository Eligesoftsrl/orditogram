package com.eligesoft.orditogram.form;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormVersioneRepository extends JpaRepository<FormVersione, Long> {
    Optional<FormVersione> findByFormKeyAndVersione(String formKey, int versione);
    Optional<FormVersione> findTopByFormKeyOrderByVersioneDesc(String formKey);
    List<FormVersione> findByFormKeyOrderByVersioneDesc(String formKey);
}
