package com.dropsales.dto;

import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.StatusConvite;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ConviteEmpresaResponse {
    private Long id;
    private Long empresaId;
    private Long lojaId;
    private String empresaNome;
    private String email;
    private PapelEmpresa papel;
    private StatusConvite status;
    private OffsetDateTime expiraEm;
    private OffsetDateTime criadoEm;
    /** Disponivel somente na resposta de criacao; o servidor armazena apenas o hash. */
    private String token;
}
