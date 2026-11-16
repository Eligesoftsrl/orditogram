package com.eligesoft.orditogram.accesso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermessoProcessoRepository extends JpaRepository<PermessoProcesso, Long> {
    List<PermessoProcesso> findByProcessoOrderByAzioneAscTipoSoggettoAscSoggettoAsc(String processo);
    List<PermessoProcesso> findByAzione(String azione);
    boolean existsByProcessoAndAzione(String processo, String azione);
    void deleteByProcesso(String processo);
}
