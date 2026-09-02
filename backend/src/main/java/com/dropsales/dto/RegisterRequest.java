package com.dropsales.dto;

import com.dropsales.util.EmailNormalizer;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Nome e obrigatorio")
    @Size(max = 150, message = "Nome deve ter no maximo 150 caracteres")
    private String nome;

    @Size(max = 160, message = "Nome da empresa deve ter no maximo 160 caracteres")
    private String nomeEmpresa;

    @NotBlank
    @Email
    @Size(max = 200, message = "Email deve ter no maximo 200 caracteres")
    private String email;

    @NotBlank
    @Size(min = 12, max = 72, message = "Senha deve ter entre 12 e 72 caracteres")
    private String senha;

    public void setEmail(String email) {
        this.email = EmailNormalizer.normalize(email);
    }
}
