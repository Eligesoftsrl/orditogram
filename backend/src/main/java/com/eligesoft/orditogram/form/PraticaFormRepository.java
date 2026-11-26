package com.eligesoft.orditogram.form;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PraticaFormRepository extends JpaRepository<PraticaForm, Long> {
    Optional<PraticaForm> findByProcessInstanceIdAndFormKey(String processInstanceId, String formKey);
    List<PraticaForm> findByProcessInstanceId(String processInstanceId);
    boolean existsByProcessInstanceId(String processInstanceId);
}
