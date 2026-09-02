package com.dropsales.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdquirenteResponse {
    private Long id;
    private String nome;
    private boolean ativo;
}
