package com.dropsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmpresaRequest {
    @NotBlank(message = "Nome da empresa e obrigatorio")
    @Size(max = 160, message = "Nome da empresa deve ter no maximo 160 caracteres")
    private String nome;

    @Size(max = 20, message = "Documento deve ter no maximo 20 caracteres")
    private String documento;

    @Size(max = 120, message = "Nome da unidade deve ter no maximo 120 caracteres")
    private String nomeLoja;
}
