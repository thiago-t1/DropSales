package com.dropsales.controller;

import com.dropsales.dto.ContextoResponse;
import com.dropsales.dto.EmpresaRequest;
import com.dropsales.dto.LojaRequest;
import com.dropsales.service.EmpresaService;
import com.dropsales.service.TenantContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contexto")
@RequiredArgsConstructor
public class ContextoController {

    private final TenantContextService tenantContextService;
    private final EmpresaService empresaService;

    @GetMapping
    public ResponseEntity<ContextoResponse> obter() {
        return ResponseEntity.ok(tenantContextService.obterContexto());
    }

    @PostMapping("/empresas")
    public ResponseEntity<ContextoResponse.EmpresaResumo> criarEmpresa(
            @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.ok(empresaService.criarEmpresa(request));
    }

    @PutMapping("/empresa")
    public ResponseEntity<ContextoResponse.EmpresaResumo> atualizarEmpresa(
            @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.ok(empresaService.atualizarEmpresa(request));
    }

    @PostMapping("/lojas")
    public ResponseEntity<ContextoResponse.LojaResumo> criarLoja(
            @Valid @RequestBody LojaRequest request) {
        return ResponseEntity.ok(empresaService.criarLoja(request));
    }
}
