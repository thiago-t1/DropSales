package com.dropsales.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Nome e obrigatorio")
    private String nome;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6, message = "Senha deve ter no minimo 6 caracteres")
    private String senha;
}
