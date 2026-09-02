package com.dropsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LojaRequest {
    @NotBlank(message = "Nome da loja e obrigatorio")
    @Size(max = 120, message = "Nome da loja deve ter no maximo 120 caracteres")
    private String nome;

    @Size(max = 60, message = "Fuso horario deve ter no maximo 60 caracteres")
    private String timezone;
}
