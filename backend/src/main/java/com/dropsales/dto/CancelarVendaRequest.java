package com.dropsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelarVendaRequest {

    @NotBlank(message = "O motivo do cancelamento e obrigatorio")
    @Size(max = 500, message = "O motivo do cancelamento deve ter no maximo 500 caracteres")
    private String motivo;
}
