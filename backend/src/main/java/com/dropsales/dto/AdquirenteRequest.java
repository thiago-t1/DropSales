package com.dropsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdquirenteRequest {
    @NotBlank
    @Size(max = 100, message = "Nome da adquirente deve ter no maximo 100 caracteres")
    private String nome;
}
