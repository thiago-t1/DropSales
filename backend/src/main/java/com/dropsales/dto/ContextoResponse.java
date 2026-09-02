package com.dropsales.dto;

import com.dropsales.model.PapelEmpresa;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ContextoResponse {
    private Long usuarioId;
    private String usuarioNome;
    private String usuarioEmail;
    private Long empresaAtualId;
    private Long lojaAtualId;
    private PapelEmpresa papelAtual;
    private List<EmpresaResumo> empresas;

    @Data
    @Builder
    public static class EmpresaResumo {
        private Long id;
        private String nome;
        private String documento;
        private PapelEmpresa papel;
        private List<LojaResumo> lojas;
    }

    @Data
    @Builder
    public static class LojaResumo {
        private Long id;
        private String nome;
        private String timezone;
    }
}
