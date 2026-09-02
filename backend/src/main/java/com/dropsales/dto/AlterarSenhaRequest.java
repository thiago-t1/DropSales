package com.dropsales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AlterarSenhaRequest {
    @NotBlank(message = "Senha atual e obrigatoria")
    @Size(max = 72, message = "Senha atual deve ter no maximo 72 caracteres")
    private String senhaAtual;

    @NotBlank(message = "Nova senha e obrigatoria")
    @Size(min = 12, max = 72, message = "Nova senha deve ter entre 12 e 72 caracteres")
    private String novaSenha;

    @NotBlank(message = "Confirmacao e obrigatoria")
    @Size(min = 12, max = 72, message = "Confirmacao deve ter entre 12 e 72 caracteres")
    private String confirmarSenha;
}
