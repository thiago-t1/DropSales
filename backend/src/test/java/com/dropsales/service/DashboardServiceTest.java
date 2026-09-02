package com.dropsales.service;

import com.dropsales.dto.DashboardResponse;
import com.dropsales.model.Empresa;
import com.dropsales.model.ItemVenda;
import com.dropsales.model.Loja;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.StatusRecebivel;
import com.dropsales.model.StatusVenda;
import com.dropsales.model.Transacao;
import com.dropsales.model.Usuario;
import com.dropsales.model.Venda;
import com.dropsales.repository.PagamentoVendaRepository;
import com.dropsales.repository.RecebivelRepository;
import com.dropsales.repository.TransacaoRepository;
import com.dropsales.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private TransacaoRepository transacaoRepository;
    @Mock private ProdutoService produtoService;
    @Mock private VendaRepository vendaRepository;
    @Mock private PagamentoVendaRepository pagamentoVendaRepository;
    @Mock private RecebivelRepository recebivelRepository;
    @Mock private TenantContextService tenantContext;

    private DashboardService dashboardService;
    private Loja loja;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                transacaoRepository,
                produtoService,
                vendaRepository,
                pagamentoVendaRepository,
                recebivelRepository,
                tenantContext);
        Usuario usuario = Usuario.builder()
                .id(1L)
                .nome("Operador")
                .email("operador@teste.com")
                .build();
        Empresa empresa = Empresa.builder().id(2L).nome("Empresa").ativo(true).build();
        loja = Loja.builder()
                .id(3L)
                .empresa(empresa)
                .nome("Loja A")
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build();
        MembroEmpresa membro = MembroEmpresa.builder()
                .id(4L)
                .empresa(empresa)
                .usuario(usuario)
                .papel(PapelEmpresa.GERENTE)
                .ativo(true)
                .build();
        when(tenantContext.atual()).thenReturn(
                new TenantContextService.ContextoAtual(
                        usuario,
                        empresa,
                        loja,
                        membro));
    }

    @Test
    void separaCompetenciaCaixaETudoPermaneceIsoladoPelaLoja() {
        when(transacaoRepository.somarReceitasPagas(loja))
                .thenReturn(new BigDecimal("1000.00"));
        when(transacaoRepository.somarDespesasPagas(loja))
                .thenReturn(new BigDecimal("300.00"));
        when(transacaoRepository.somarCMV(loja))
                .thenReturn(new BigDecimal("250.00"));
        when(pagamentoVendaRepository.somarTaxasAtivasPorLoja(loja))
                .thenReturn(new BigDecimal("20.00"));
        when(recebivelRepository.somarLiquidoPorStatus(
                loja,
                StatusRecebivel.RECEBIDO)).thenReturn(new BigDecimal("480.00"));
        when(recebivelRepository.somarLiquidoPorStatus(
                loja,
                StatusRecebivel.PENDENTE)).thenReturn(new BigDecimal("500.00"));
        when(produtoService.listarEstoqueBaixo()).thenReturn(List.of());
        when(vendaRepository.findVendasDesde(any(), org.mockito.ArgumentMatchers.eq(loja)))
                .thenReturn(List.of());
        when(transacaoRepository.findCustosDesde(
                any(),
                org.mockito.ArgumentMatchers.eq(loja))).thenReturn(List.of());
        when(vendaRepository.findTop5ProdutosPorQuantidade(loja)).thenReturn(List.of());
        when(vendaRepository.findTop5ByLojaAndStatusOrderByCreatedAtDesc(
                loja,
                StatusVenda.CONCLUIDA)).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard();

        assertEquals(new BigDecimal("1000.00"), response.getReceitas());
        assertEquals(response.getReceitas(), response.getReceitaBruta());
        assertEquals(new BigDecimal("320.00"), response.getDespesas());
        assertEquals(new BigDecimal("20.00"), response.getTaxasPagamento());
        assertEquals(new BigDecimal("250.00"), response.getCmv());
        assertEquals(new BigDecimal("750.00"), response.getLucroBruto());
        assertEquals(new BigDecimal("680.00"), response.getSaldoOperacional());
        assertEquals(response.getSaldoOperacional(), response.getSaldo());
        assertEquals(response.getSaldoOperacional(), response.getLucroLiquido());
        assertEquals(new BigDecimal("480.00"), response.getRecebidoLiquido());
        assertEquals(new BigDecimal("500.00"), response.getAReceber());
        verify(transacaoRepository).somarReceitasPagas(loja);
        verify(recebivelRepository).somarLiquidoPorStatus(
                loja,
                StatusRecebivel.PENDENTE);
    }

    @Test
    void apresentaVendaRecenteNoHorarioDaLojaComVendedorEQuantidade() {
        Usuario vendedor = Usuario.builder()
                .id(10L)
                .nome("Maria")
                .email("maria@teste.com")
                .build();
        Venda venda = Venda.builder()
                .id(20L)
                .usuario(vendedor)
                .loja(loja)
                .status(StatusVenda.CONCLUIDA)
                .total(new BigDecimal("149.90"))
                .createdAt(OffsetDateTime.parse("2026-07-28T15:30:00Z"))
                .itens(List.of(
                        ItemVenda.builder().quantidade(2).build(),
                        ItemVenda.builder().quantidade(1).build()))
                .build();

        when(produtoService.listarEstoqueBaixo()).thenReturn(List.of());
        when(vendaRepository.findVendasDesde(any(), org.mockito.ArgumentMatchers.eq(loja)))
                .thenReturn(List.of(venda));
        when(transacaoRepository.findCustosDesde(
                any(),
                org.mockito.ArgumentMatchers.eq(loja))).thenReturn(List.of());
        when(vendaRepository.findTop5ProdutosPorQuantidade(loja)).thenReturn(List.of());
        when(vendaRepository.findTop5ByLojaAndStatusOrderByCreatedAtDesc(
                loja,
                StatusVenda.CONCLUIDA)).thenReturn(List.of(venda));

        DashboardResponse response = dashboardService.getDashboard();

        assertEquals(1, response.getVendasRecentes().size());
        DashboardResponse.VendaRecenteDTO recente = response.getVendasRecentes().get(0);
        assertEquals("Maria", recente.getVendedor());
        assertEquals("28/07/2026 12:30", recente.getData());
        assertEquals(3, recente.getTotalItens());
        assertEquals(new BigDecimal("149.90"), recente.getValor());
    }

    @Test
    void agrupaCmvPeloDiaCivilDaLoja() {
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate hoje = LocalDate.now(zone);
        OffsetDateTime instanteUtc = hoje.atTime(0, 30)
                .atZone(zone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime();
        Transacao custo = Transacao.builder()
                .valor(new BigDecimal("42.50"))
                .createdAt(instanteUtc)
                .build();

        when(produtoService.listarEstoqueBaixo()).thenReturn(List.of());
        when(vendaRepository.findVendasDesde(any(), org.mockito.ArgumentMatchers.eq(loja)))
                .thenReturn(List.of());
        when(transacaoRepository.findCustosDesde(
                any(),
                org.mockito.ArgumentMatchers.eq(loja))).thenReturn(List.of(custo));
        when(vendaRepository.findTop5ProdutosPorQuantidade(loja)).thenReturn(List.of());
        when(vendaRepository.findTop5ByLojaAndStatusOrderByCreatedAtDesc(
                loja,
                StatusVenda.CONCLUIDA)).thenReturn(List.of());

        DashboardResponse response = dashboardService.getDashboard();

        DashboardResponse.VendaDiariaDTO ultimo =
                response.getCustosDiarios().get(response.getCustosDiarios().size() - 1);
        assertEquals(new BigDecimal("42.50"), ultimo.getTotal());
    }

}
