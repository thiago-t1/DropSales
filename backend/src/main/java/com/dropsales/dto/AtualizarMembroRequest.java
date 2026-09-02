package com.dropsales.dto;

import com.dropsales.model.PapelEmpresa;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AtualizarMembroRequest {
    @NotNull
    private PapelEmpresa papel;

    @NotNull
    private Boolean ativo;
}
