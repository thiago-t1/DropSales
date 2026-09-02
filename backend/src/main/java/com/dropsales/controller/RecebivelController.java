package com.dropsales.controller;

import com.dropsales.dto.RecebivelResponse;
import com.dropsales.dto.ResumoRecebiveisResponse;
import com.dropsales.service.RecebivelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recebiveis")
@RequiredArgsConstructor
public class RecebivelController {

    private final RecebivelService service;

    @GetMapping
    public ResponseEntity<ResumoRecebiveisResponse> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PostMapping("/{id}/receber")
    public ResponseEntity<RecebivelResponse> confirmar(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirmarRecebimento(id));
    }
}
