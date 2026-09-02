package com.dropsales.controller;

import com.dropsales.dto.*;
import com.dropsales.service.ConfiguracaoPagamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/configuracoes/pagamentos")
@RequiredArgsConstructor
public class ConfiguracaoPagamentoController {

    private final ConfiguracaoPagamentoService service;

    @GetMapping("/taxas")
    public ResponseEntity<List<ConfiguracaoTaxaResponse>> listarTaxas() {
        return ResponseEntity.ok(service.listarTaxas());
    }

    @PostMapping("/taxas")
    public ResponseEntity<ConfiguracaoTaxaResponse> criarTaxa(
            @Valid @RequestBody ConfiguracaoTaxaRequest request) {
        return ResponseEntity.ok(service.salvarTaxa(null, request));
    }

    @PutMapping("/taxas/{id}")
    public ResponseEntity<ConfiguracaoTaxaResponse> atualizarTaxa(
            @PathVariable Long id,
            @Valid @RequestBody ConfiguracaoTaxaRequest request) {
        return ResponseEntity.ok(service.salvarTaxa(id, request));
    }

    @GetMapping("/adquirentes")
    public ResponseEntity<List<AdquirenteResponse>> listarAdquirentes() {
        return ResponseEntity.ok(service.listarAdquirentes());
    }

    @PostMapping("/adquirentes")
    public ResponseEntity<AdquirenteResponse> criarAdquirente(
            @Valid @RequestBody AdquirenteRequest request) {
        return ResponseEntity.ok(service.criarAdquirente(request));
    }
}
