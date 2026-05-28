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
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Autenticacao invalida ou expirada.");
        }
        return ResponseEntity.ok(vendaService.registrarVenda(request, authentication.getName()));
    }

    /**
     * Lista todas as vendas em ordem decrescente de data.
     */
    @GetMapping
    public ResponseEntity<List<VendaResponse>> listar(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("Autenticacao invalida ou expirada.");
        }
        return ResponseEntity.ok(vendaService.listarVendas(authentication.getName()));
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
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        vendaService.cancelarVenda(id);
        return ResponseEntity.noContent().build();
    }
}