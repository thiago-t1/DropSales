package com.dropsales.controller;

import com.dropsales.dto.DashboardResponse;
import com.dropsales.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(dashboardService.getDashboard());
    }

    @GetMapping("/atividades-recentes")
    public ResponseEntity<List<DashboardResponse.VendaRecenteDTO>> getAtividadesRecentes() {
        return ResponseEntity.ok(dashboardService.getVendasRecentes());
    }
}
