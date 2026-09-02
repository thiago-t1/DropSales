package com.dropsales.service;

import com.dropsales.dto.DashboardResponse;
import com.dropsales.dto.ProdutoResponse;
import com.dropsales.model.Loja;
import com.dropsales.model.StatusRecebivel;
import com.dropsales.model.StatusVenda;
import com.dropsales.model.Transacao;
import com.dropsales.model.Venda;
import com.dropsales.repository.PagamentoVendaRepository;
import com.dropsales.repository.RecebivelRepository;
import com.dropsales.repository.TransacaoRepository;
import com.dropsales.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TransacaoRepository transacaoRepository;
    private final ProdutoService produtoService;
    private final VendaRepository vendaRepository;
    private final PagamentoVendaRepository pagamentoVendaRepository;
    private final RecebivelRepository recebivelRepository;
    private final TenantContextService tenantContext;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        Loja loja = tenantContext.atual().loja();

        // Competencia: venda concluida no periodo, independentemente da liquidacao.
        BigDecimal receitas = valorOuZero(transacaoRepository.somarReceitasPagas(loja));
        BigDecimal despesasTransacoes = valorOuZero(
                transacaoRepository.somarDespesasPagas(loja));
        BigDecimal cmv = valorOuZero(transacaoRepository.somarCMV(loja));
        BigDecimal taxasPagamento = valorOuZero(
                pagamentoVendaRepository.somarTaxasAtivasPorLoja(loja));
        BigDecimal despesas = despesasTransacoes.add(taxasPagamento);
        BigDecimal saldoOperacional = receitas.subtract(despesas);
        BigDecimal lucroBruto = receitas.subtract(cmv);

        // Caixa: usa exclusivamente os snapshots de recebiveis.
        BigDecimal recebidoLiquido = valorOuZero(recebivelRepository
                .somarLiquidoPorStatus(loja, StatusRecebivel.RECEBIDO));
        BigDecimal aReceber = valorOuZero(recebivelRepository
                .somarLiquidoPorStatus(loja, StatusRecebivel.PENDENTE));

        List<ProdutoResponse> estoqueBaixo = produtoService.listarEstoqueBaixo();
        List<DashboardResponse.VendaDiariaDTO> vendas30d =
                calcularVendasDiarias(30, loja);
        List<DashboardResponse.VendaDiariaDTO> custos30d =
                calcularCustosDiarios(30, loja);
        List<DashboardResponse.TopProdutoDTO> top5 =
                calcularTop5Produtos(loja);
        List<DashboardResponse.VendaRecenteDTO> recentes =
                buscarVendasRecentes(loja);

        return DashboardResponse.builder()
                .receitas(receitas)
                .receitaBruta(receitas)
                .despesas(despesas)
                .cmv(cmv)
                .taxasPagamento(taxasPagamento)
                .saldo(saldoOperacional)
                .saldoOperacional(saldoOperacional)
                .lucroLiquido(saldoOperacional)
                .lucroBruto(lucroBruto)
                .recebidoLiquido(recebidoLiquido)
                .aReceber(aReceber)
                .estoqueBaixo(estoqueBaixo)
                .vendasDiarias(vendas30d)
                .custosDiarios(custos30d)
                .topProdutos(top5)
                .vendasRecentes(recentes)
                .build();
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private List<DashboardResponse.VendaDiariaDTO> calcularVendasDiarias(
            int dias,
            Loja loja) {
        ZoneId zone = zoneDaLoja(loja);
        LocalDate hoje = LocalDate.now(zone);
        OffsetDateTime desde = hoje.minusDays(dias - 1)
                .atStartOfDay(zone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime();
        List<Venda> vendas = vendaRepository.findVendasDesde(desde, loja);

        Map<LocalDate, BigDecimal> porDia = vendas.stream()
                .collect(Collectors.groupingBy(
                        venda -> venda.getCreatedAt()
                                .atZoneSameInstant(zone)
                                .toLocalDate(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Venda::getTotal,
                                BigDecimal::add)));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        List<DashboardResponse.VendaDiariaDTO> resultado = new ArrayList<>();
        for (int i = dias - 1; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            resultado.add(DashboardResponse.VendaDiariaDTO.builder()
                    .data(dia.format(formatter))
                    .total(porDia.getOrDefault(dia, BigDecimal.ZERO))
                    .build());
        }
        return resultado;
    }

    private List<DashboardResponse.VendaDiariaDTO> calcularCustosDiarios(
            int dias,
            Loja loja) {
        ZoneId zone = zoneDaLoja(loja);
        LocalDate hoje = LocalDate.now(zone);
        OffsetDateTime desde = hoje.minusDays(dias - 1)
                .atStartOfDay(zone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime();
        List<Transacao> custos = transacaoRepository.findCustosDesde(desde, loja);

        Map<LocalDate, BigDecimal> porDia = custos.stream()
                .collect(Collectors.groupingBy(
                        transacao -> transacao.getCreatedAt()
                                .atZoneSameInstant(zone)
                                .toLocalDate(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Transacao::getValor,
                                BigDecimal::add)));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");
        List<DashboardResponse.VendaDiariaDTO> resultado = new ArrayList<>();
        for (int i = dias - 1; i >= 0; i--) {
            LocalDate dia = hoje.minusDays(i);
            resultado.add(DashboardResponse.VendaDiariaDTO.builder()
                    .data(dia.format(formatter))
                    .total(porDia.getOrDefault(dia, BigDecimal.ZERO))
                    .build());
        }
        return resultado;
    }

    private List<DashboardResponse.TopProdutoDTO> calcularTop5Produtos(Loja loja) {
        List<DashboardResponse.TopProdutoDTO> resultado = new ArrayList<>();
        for (Object[] row : vendaRepository.findTop5ProdutosPorQuantidade(loja)) {
            resultado.add(DashboardResponse.TopProdutoDTO.builder()
                    .nome((String) row[0])
                    .totalUnidades(((Number) row[1]).longValue())
                    .build());
        }
        return resultado;
    }

    private List<DashboardResponse.VendaRecenteDTO> buscarVendasRecentes(Loja loja) {
        ZoneId zone = zoneDaLoja(loja);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return vendaRepository.findTop5ByLojaAndStatusOrderByCreatedAtDesc(
                        loja,
                        StatusVenda.CONCLUIDA).stream()
                .map(venda -> DashboardResponse.VendaRecenteDTO.builder()
                        .id(venda.getId())
                        .vendedor(venda.getUsuario().getNome())
                        .data(venda.getCreatedAt()
                                .atZoneSameInstant(zone)
                                .format(formatter))
                        .valor(venda.getTotal())
                        .totalItens(venda.getItens().stream()
                                .mapToInt(item -> item.getQuantidade())
                                .sum())
                        .build())
                .toList();
    }

    private ZoneId zoneDaLoja(Loja loja) {
        try {
            return ZoneId.of(loja.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("America/Sao_Paulo");
        }
    }
}
