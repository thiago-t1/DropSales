package com.dropsales.controller;

import com.dropsales.dto.*;
import com.dropsales.exception.BusinessException;
import com.dropsales.service.VendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
public class VendaController {

    private final VendaService vendaService;

    /**
     * Registra uma nova venda.
     * Requer autenticacao JWT valida — o email do usuario logado e extraido do token.
     */
    @PostMapping
    public ResponseEntity<VendaResponse> registrar(
            @Valid @RequestBody VendaRequest request,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Autenticacao invalida ou expirada.");
        }
        return ResponseEntity.ok(vendaService.registrarVenda(
                request,
                idempotencyKey));
    }

    /**
     * Lista todas as vendas em ordem decrescente de data.
     */
    @GetMapping
    public ResponseEntity<List<VendaResponse>> listar(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Autenticacao invalida ou expirada.");
        }
        return ResponseEntity.ok(vendaService.listarVendas());
    }

    @GetMapping("/recentes")
    public ResponseEntity<List<VendaResponse>> listarRecentes() {
        return ResponseEntity.ok(vendaService.listarVendasRecentes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(vendaService.buscarPorId(id));
    }

    /**
     * Edita uma venda existente (ajusta itens, estoque e transacoes financeiras).
     */
    @PutMapping("/{id}")
    public ResponseEntity<VendaResponse> editar(
            @PathVariable Long id,
            @Valid @RequestBody VendaRequest request) {
        return ResponseEntity.ok(vendaService.editarVenda(id, request));
    }

    /**
     * Estorna uma venda: devolve estoque e remove transacoes financeiras.
     */
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<VendaResponse> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody CancelarVendaRequest request) {
        return ResponseEntity.ok(vendaService.cancelarVenda(id, request));
    }
}
