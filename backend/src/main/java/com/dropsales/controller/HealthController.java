package com.dropsales.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint minimo usado pelo front-end para iniciar o backend sem bloquear a tela. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Void> health() {
        return ResponseEntity.noContent().build();
    }
}
