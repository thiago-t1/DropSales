package com.dropsales.dto;

import com.dropsales.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Email invalido")
    @Size(max = 200, message = "Email deve ter no maximo 200 caracteres")
    private String email;

    @NotBlank(message = "Senha e obrigatoria")
    @Size(max = 72, message = "Senha deve ter no maximo 72 caracteres")
    private String senha;

    public void setEmail(String email) {
        this.email = EmailNormalizer.normalize(email);
    }
}
