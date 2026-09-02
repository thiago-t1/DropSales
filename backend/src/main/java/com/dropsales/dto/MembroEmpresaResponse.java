package com.dropsales.dto;

import com.dropsales.model.PapelEmpresa;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class MembroEmpresaResponse {
    private Long id;
    private Long usuarioId;
    private String nome;
    private String email;
    private PapelEmpresa papel;
    private boolean ativo;
    private boolean usuarioAtual;
    private OffsetDateTime desde;
}
