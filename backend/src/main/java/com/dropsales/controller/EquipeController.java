package com.dropsales.controller;

import com.dropsales.dto.*;
import com.dropsales.service.EquipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/equipe")
@RequiredArgsConstructor
public class EquipeController {

    private final EquipeService equipeService;

    @GetMapping("/membros")
    public ResponseEntity<List<MembroEmpresaResponse>> listarMembros() {
        return ResponseEntity.ok(equipeService.listarMembros());
    }

    @PutMapping("/membros/{id}")
    public ResponseEntity<MembroEmpresaResponse> atualizarMembro(
            @PathVariable Long id,
            @Valid @RequestBody AtualizarMembroRequest request) {
        return ResponseEntity.ok(equipeService.atualizarMembro(id, request));
    }

    @GetMapping("/convites")
    public ResponseEntity<List<ConviteEmpresaResponse>> listarConvites() {
        return ResponseEntity.ok(equipeService.listarConvitesPendentes());
    }

    @PostMapping("/convites")
    public ResponseEntity<ConviteEmpresaResponse> criarConvite(
            @Valid @RequestBody ConviteEmpresaRequest request) {
        return ResponseEntity.ok(equipeService.criarConvite(request));
    }

    @DeleteMapping("/convites/{id}")
    public ResponseEntity<Void> revogarConvite(@PathVariable Long id) {
        equipeService.revogarConvite(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/convites/aceitar")
    public ResponseEntity<ConviteEmpresaResponse> aceitarConvite(
            @Valid @RequestBody AceitarConviteRequest request) {
        return ResponseEntity.ok(equipeService.aceitarConvite(request));
    }
}
