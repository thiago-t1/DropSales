package com.dropsales.dto;

import com.dropsales.model.PapelEmpresa;
import com.dropsales.util.EmailNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConviteEmpresaRequest {
    @NotBlank
    @Email
    @Size(max = 200, message = "Email deve ter no maximo 200 caracteres")
    private String email;

    @NotNull
    private PapelEmpresa papel;

    public void setEmail(String email) {
        this.email = EmailNormalizer.normalize(email);
    }
}
