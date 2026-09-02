package com.dropsales.controller;

import com.dropsales.dto.ConviteEmpresaResponse;
import com.dropsales.service.EquipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/convites")
@RequiredArgsConstructor
public class ConvitePublicoController {

    private final EquipeService equipeService;

    @GetMapping("/{token}")
    public ResponseEntity<ConviteEmpresaResponse> visualizar(@PathVariable String token) {
        return ResponseEntity.ok(equipeService.visualizarConvite(token));
    }
}
