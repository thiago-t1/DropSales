package com.dropsales.service;

import com.dropsales.dto.DashboardResponse;
import com.dropsales.dto.ProdutoResponse;
import com.dropsales.model.Venda;
import com.dropsales.repository.TransacaoRepository;
import com.dropsales.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.dropsales.model.Usuario;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.SecurityUtils;
import com.dropsales.exception.ResourceNotFoundException;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransacaoRepository transacaoRepository;
    private final ProdutoService produtoService;
    private final VendaRepository vendaRepository;
    private final UsuarioRepository usuarioRepository;

    private Usuario getUsuarioLogado() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) throw new com.dropsales.exception.BusinessException("Usuário não autenticado");
        return usuarioRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    public DashboardResponse getDashboard() {
        Usuario usuario = getUsuarioLogado();

        BigDecimal receitas   = transacaoRepository.somarReceitasPagas(usuario);
        BigDecimal despesas   = transacaoRepository.somarDespesasPagas(usuario);
        BigDecimal cmv        = transacaoRepository.somarCMV(usuario);
        BigDecimal saldo      = receitas.subtract(despesas);
        BigDecimal lucro      = receitas.subtract(cmv);

        List<ProdutoResponse>                estoqueBaixo  = produtoService.listarEstoqueBaixo();
        List<DashboardResponse.VendaDiariaDTO> vendas30d   = calcularVendasDiarias(30, usuario);
        List<DashboardResponse.VendaDiariaDTO> custos30d   = calcularCustosDiarios(30, usuario);
        List<DashboardResponse.TopProdutoDTO>  top5         = calcularTop5Produtos(usuario);
        List<DashboardResponse.VendaRecenteDTO> recentes   = buscarVendasRecentes(usuario);

        return DashboardResponse.builder()
                .receitas(receitas)
                .despesas(despesas)
                .cmv(cmv)
                .saldo(saldo)
                .lucroLiquido(lucro)
                .estoqueBaixo(estoqueBaixo)
                .vendasDiarias(vendas30d)
                .custosDiarios(custos30d)
                .topProdutos(top5)
                .vendasRecentes(recentes)
                .build();
    }

    private List<DashboardResponse.VendaDiariaDTO> calcularVendasDiarias(int dias, Usuario usuario) {
        LocalDateTime desde = LocalDate.now().minusDays(dias - 1).atStartOfDay();
        List<Venda> vendas  = vendaRepository.findVendasDesde(desde, usuario);

        Map<LocalDate, BigDecimal> porDia = vendas.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getCreatedAt().toLocalDate(),
                        Collectors.reducing(BigDecimal.ZERO, Venda::getTotal, BigDecimal::add)
                ));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<DashboardResponse.VendaDiariaDTO> resultado = new ArrayList<>();
        for (int i = dias - 1; i >= 0; i--) {
            LocalDate dia = LocalDate.now().minusDays(i);
            resultado.add(DashboardResponse.VendaDiariaDTO.builder()
                    .data(dia.format(fmt))
                    .total(porDia.getOrDefault(dia, BigDecimal.ZERO))
                    .build());
        }
        return resultado;
    }

    private List<DashboardResponse.VendaDiariaDTO> calcularCustosDiarios(int dias, Usuario usuario) {
        LocalDateTime desde = LocalDate.now().minusDays(dias - 1).atStartOfDay();
        List<Object[]> rows = transacaoRepository.somarCustosDiariosDesde(desde, usuario);

        Map<LocalDate, BigDecimal> porDia = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate dia   = ((java.sql.Date) row[0]).toLocalDate();
            BigDecimal valor = (BigDecimal) row[1];
            porDia.put(dia, valor);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<DashboardResponse.VendaDiariaDTO> resultado = new ArrayList<>();
        for (int i = dias - 1; i >= 0; i--) {
            LocalDate dia = LocalDate.now().minusDays(i);
            resultado.add(DashboardResponse.VendaDiariaDTO.builder()
                    .data(dia.format(fmt))
                    .total(porDia.getOrDefault(dia, BigDecimal.ZERO))
                    .build());
        }
        return resultado;
    }

    private List<DashboardResponse.TopProdutoDTO> calcularTop5Produtos(Usuario usuario) {
        List<Object[]> rows = vendaRepository.findTop5ProdutosPorQuantidade(usuario);
        List<DashboardResponse.TopProdutoDTO> resultado = new ArrayList<>();
        for (Object[] row : rows) {
            resultado.add(DashboardResponse.TopProdutoDTO.builder()
                    .nome((String) row[0])
                    .totalUnidades(((Number) row[1]).longValue())
                    .build());
        }
        return resultado;
    }

    private List<DashboardResponse.VendaRecenteDTO> buscarVendasRecentes(Usuario usuario) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return vendaRepository.findTop5ByUsuarioOrderByCreatedAtDesc(usuario).stream()
                .map(v -> DashboardResponse.VendaRecenteDTO.builder()
                        .id(v.getId())
                        .vendedor(v.getUsuario().getNome())
                        .data(v.getCreatedAt().format(fmt))
                        .valor(v.getTotal())
                        .totalItens(v.getItens().stream().mapToInt(i -> i.getQuantidade()).sum())
                        .build())
                .toList();
    }
}