package com.dropsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlterarSenhaRequest {
    @NotBlank(message = "Senha atual e obrigatoria")
    private String senhaAtual;

    @NotBlank(message = "Nova senha e obrigatoria")
    @Size(min = 6, message = "Nova senha deve ter no minimo 6 caracteres")
    private String novaSenha;

    @NotBlank(message = "Confirmacao e obrigatoria")
    private String confirmarSenha;
}