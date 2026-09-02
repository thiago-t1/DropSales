package com.dropsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AceitarConviteRequest {
    @NotBlank
    @Size(min = 32, max = 128, message = "Token de convite invalido")
    private String token;
}
