package com.dropsales.dto;

import com.dropsales.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsuarioUpdateRequest {
    @NotBlank(message = "Nome e obrigatorio")
    @Size(max = 150, message = "Nome deve ter no maximo 150 caracteres")
    private String nome;

    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Email invalido")
    @Size(max = 200, message = "Email deve ter no maximo 200 caracteres")
    private String email;

    public void setEmail(String email) {
        this.email = EmailNormalizer.normalize(email);
    }
}
