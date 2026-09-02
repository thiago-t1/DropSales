package com.dropsales.service;

import com.dropsales.dto.PagamentoVendaRequest;
import com.dropsales.model.ConfiguracaoTaxaPagamento;
import com.dropsales.model.FormaPagamento;
import com.dropsales.model.Loja;
import com.dropsales.model.PagamentoVenda;
import com.dropsales.model.Recebivel;
import com.dropsales.model.StatusPagamentoVenda;
import com.dropsales.model.StatusRecebivel;
import com.dropsales.model.Venda;
import com.dropsales.repository.PagamentoVendaRepository;
import com.dropsales.repository.RecebivelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagamentoVendaServiceTest {

    @Mock private ConfiguracaoPagamentoService configuracaoService;
    @Mock private PagamentoVendaRepository pagamentoRepository;
    @Mock private RecebivelRepository recebivelRepository;

    @Captor private ArgumentCaptor<Iterable<Recebivel>> recebiveisCaptor;
    @Captor private ArgumentCaptor<Iterable<PagamentoVenda>> pagamentosCaptor;

    private PagamentoVendaService service;
    private Loja loja;
    private Venda venda;

    @BeforeEach
    void setUp() {
        service = new PagamentoVendaService(
                configuracaoService,
                pagamentoRepository,
                recebivelRepository);
        loja = Loja.builder()
                .id(10L)
                .nome("Loja teste")
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build();
        venda = Venda.builder()
                .id(20L)
                .loja(loja)
                .total(new BigDecimal("100.00"))
                .build();
    }

    @Test
    void processarSplitPreservaSomaExataEPermiteAgregarTaxasELiquidos() {
        PagamentoVendaRequest pix = pagamento(FormaPagamento.PIX, "40.00", 1, null);
        PagamentoVendaRequest credito = pagamento(
                FormaPagamento.CARTAO_CREDITO,
                "60.00",
                1,
                null);
        when(configuracaoService.resolverTaxa(
                loja,
                FormaPagamento.PIX,
                null,
                null,
                1)).thenReturn(configuracao("0.0000", "0.00", 0));
        when(configuracaoService.resolverTaxa(
                loja,
                FormaPagamento.CARTAO_CREDITO,
                null,
                null,
                1)).thenReturn(configuracao("2.0000", "1.00", 30));
        persistirPagamentoRecebidoPeloRepositorio();

        List<PagamentoVenda> pagamentos = service.processar(
                venda,
                loja,
                List.of(pix, credito));

        assertEquals(2, pagamentos.size());
        assertMoney("100.00", somar(pagamentos, PagamentoVenda::getValorBruto));
        assertMoney("2.20", somar(pagamentos, PagamentoVenda::getTaxaValor));
        assertMoney("97.80", somar(pagamentos, PagamentoVenda::getValorLiquido));
        assertTrue(pagamentos.stream()
                .allMatch(item -> item.getStatus() == StatusPagamentoVenda.ATIVO));
        verify(pagamentoRepository, times(2)).save(any(PagamentoVenda.class));
    }

    @Test
    void pagamentoEmDinheiroCalculaTrocoERecebimentoImediato() {
        venda.setTotal(new BigDecimal("45.50"));
        PagamentoVendaRequest dinheiro = pagamento(
                FormaPagamento.DINHEIRO,
                "45.50",
                1,
                "50.00");
        when(configuracaoService.resolverTaxa(
                loja,
                FormaPagamento.DINHEIRO,
                null,
                null,
                1)).thenReturn(configuracao("0.0000", "0.00", 0));
        persistirPagamentoRecebidoPeloRepositorio();

        PagamentoVenda resultado = service.processar(
                venda,
                loja,
                List.of(dinheiro)).get(0);

        assertMoney("50.00", resultado.getValorRecebido());
        assertMoney("4.50", resultado.getTroco());
        verify(recebivelRepository).saveAll(recebiveisCaptor.capture());
        Recebivel recebivel = lista(recebiveisCaptor.getValue()).get(0);
        assertEquals(StatusRecebivel.RECEBIDO, recebivel.getStatus());
        assertNotNull(recebivel.getRecebidoEm());
        assertEquals(ZoneOffset.UTC, recebivel.getRecebidoEm().getOffset());
    }

    @Test
    void parcelamentoFechaTotaisECadaParcelaDerivaLiquidoDoBrutoMenosTaxa() {
        venda.setTotal(new BigDecimal("10.00"));
        PagamentoVendaRequest credito = pagamento(
                FormaPagamento.CARTAO_CREDITO,
                "10.00",
                3,
                null);
        when(configuracaoService.resolverTaxa(
                loja,
                FormaPagamento.CARTAO_CREDITO,
                null,
                null,
                3)).thenReturn(configuracao("0.0000", "0.01", 30));
        persistirPagamentoRecebidoPeloRepositorio();

        PagamentoVenda pagamento = service.processar(
                venda,
                loja,
                List.of(credito)).get(0);

        verify(recebivelRepository).saveAll(recebiveisCaptor.capture());
        List<Recebivel> parcelas = lista(recebiveisCaptor.getValue());
        assertEquals(3, parcelas.size());
        assertMoney(pagamento.getValorBruto(), somarRecebiveis(
                parcelas,
                Recebivel::getValorBruto));
        assertMoney(pagamento.getTaxaValor(), somarRecebiveis(
                parcelas,
                Recebivel::getTaxaValor));
        assertMoney(pagamento.getValorLiquido(), somarRecebiveis(
                parcelas,
                Recebivel::getValorLiquido));
        parcelas.forEach(parcela -> assertMoney(
                parcela.getValorBruto().subtract(parcela.getTaxaValor()),
                parcela.getValorLiquido()));
    }

    @Test
    void cartaoComPrazoZeroContinuaPendenteAteBaixaExplicita() {
        venda.setTotal(new BigDecimal("20.00"));
        PagamentoVendaRequest debito = pagamento(
                FormaPagamento.CARTAO_DEBITO,
                "20.00",
                1,
                null);
        when(configuracaoService.resolverTaxa(
                loja,
                FormaPagamento.CARTAO_DEBITO,
                null,
                null,
                1)).thenReturn(configuracao("1.0000", "0.00", 0));
        persistirPagamentoRecebidoPeloRepositorio();

        service.processar(venda, loja, List.of(debito));

        verify(recebivelRepository).saveAll(recebiveisCaptor.capture());
        Recebivel recebivel = lista(recebiveisCaptor.getValue()).get(0);
        assertEquals(StatusRecebivel.PENDENTE, recebivel.getStatus());
        assertNull(recebivel.getRecebidoEm());
    }

    @Test
    void substituirPreservaPagamentoAnteriorECancelaSeusRecebiveis() {
        PagamentoVenda anterior = pagamentoPersistido(
                30L,
                FormaPagamento.CARTAO_CREDITO,
                StatusPagamentoVenda.ATIVO);
        Recebivel pendente = recebivel(anterior, StatusRecebivel.PENDENTE);
        Recebivel recebido = recebivel(anterior, StatusRecebivel.RECEBIDO);
        PagamentoVendaRequest novoPix = pagamento(
                FormaPagamento.PIX,
                "100.00",
                1,
                null);
        when(pagamentoRepository.findByVendaAndStatusOrderByIdAsc(
                venda,
                StatusPagamentoVenda.ATIVO)).thenReturn(List.of(anterior));
        when(recebivelRepository.findByPagamentoVenda(anterior))
                .thenReturn(List.of(pendente, recebido));
        when(configuracaoService.resolverTaxa(
                loja,
                FormaPagamento.PIX,
                null,
                null,
                1)).thenReturn(configuracao("0.0000", "0.00", 0));
        persistirPagamentoRecebidoPeloRepositorio();

        List<PagamentoVenda> novos = service.substituir(
                venda,
                loja,
                List.of(novoPix));

        assertEquals(StatusPagamentoVenda.SUBSTITUIDO, anterior.getStatus());
        assertNotNull(anterior.getSubstituidoEm());
        assertEquals(ZoneOffset.UTC, anterior.getSubstituidoEm().getOffset());
        assertNull(anterior.getCanceladoEm());
        assertEquals(StatusRecebivel.CANCELADO, pendente.getStatus());
        assertEquals(StatusRecebivel.CANCELADO, recebido.getStatus());
        assertEquals(1, novos.size());
        assertEquals(StatusPagamentoVenda.ATIVO, novos.get(0).getStatus());

        verify(pagamentoRepository).saveAll(pagamentosCaptor.capture());
        List<PagamentoVenda> preservados = lista(pagamentosCaptor.getValue());
        assertEquals(1, preservados.size());
        assertSame(anterior, preservados.get(0));
        verify(recebivelRepository, times(2)).saveAll(recebiveisCaptor.capture());
        List<Recebivel> recebiveisAntigos = lista(
                recebiveisCaptor.getAllValues().get(0));
        assertEquals(List.of(pendente, recebido), recebiveisAntigos);
    }

    @Test
    void cancelarMarcaAtivosERecebiveisEEhSeguroQuandoNaoHaMaisAtivos() {
        PagamentoVenda ativo = pagamentoPersistido(
                40L,
                FormaPagamento.CARTAO_DEBITO,
                StatusPagamentoVenda.ATIVO);
        Recebivel recebivel = recebivel(ativo, StatusRecebivel.PENDENTE);
        when(pagamentoRepository.findByVendaAndStatusOrderByIdAsc(
                venda,
                StatusPagamentoVenda.ATIVO))
                .thenReturn(List.of(ativo))
                .thenReturn(Collections.emptyList());
        when(recebivelRepository.findByPagamentoVenda(ativo))
                .thenReturn(List.of(recebivel));

        assertDoesNotThrow(() -> {
            service.cancelar(venda);
            service.cancelar(venda);
        });

        assertEquals(StatusPagamentoVenda.CANCELADO, ativo.getStatus());
        assertNotNull(ativo.getCanceladoEm());
        assertEquals(ZoneOffset.UTC, ativo.getCanceladoEm().getOffset());
        assertNull(ativo.getSubstituidoEm());
        assertEquals(StatusRecebivel.CANCELADO, recebivel.getStatus());
        verify(recebivelRepository, times(1)).findByPagamentoVenda(ativo);
        verify(pagamentoRepository, times(2)).saveAll(pagamentosCaptor.capture());
        assertFalse(lista(pagamentosCaptor.getAllValues().get(0)).isEmpty());
        assertTrue(lista(pagamentosCaptor.getAllValues().get(1)).isEmpty());
    }

    private PagamentoVendaRequest pagamento(
            FormaPagamento forma,
            String valor,
            int parcelas,
            String valorRecebido) {
        PagamentoVendaRequest request = new PagamentoVendaRequest();
        request.setFormaPagamento(forma);
        request.setValor(new BigDecimal(valor));
        request.setParcelas(parcelas);
        if (valorRecebido != null) {
            request.setValorRecebido(new BigDecimal(valorRecebido));
        }
        return request;
    }

    private ConfiguracaoTaxaPagamento configuracao(
            String percentual,
            String fixa,
            int prazo) {
        return ConfiguracaoTaxaPagamento.builder()
                .loja(loja)
                .taxaPercentual(new BigDecimal(percentual))
                .taxaFixa(new BigDecimal(fixa))
                .prazoRecebimentoDias(prazo)
                .ativo(true)
                .build();
    }

    private PagamentoVenda pagamentoPersistido(
            Long id,
            FormaPagamento forma,
            StatusPagamentoVenda status) {
        return PagamentoVenda.builder()
                .id(id)
                .venda(venda)
                .loja(loja)
                .formaPagamento(forma)
                .parcelas(1)
                .valorBruto(venda.getTotal())
                .taxaPercentual(BigDecimal.ZERO)
                .taxaFixa(BigDecimal.ZERO)
                .taxaValor(BigDecimal.ZERO)
                .valorLiquido(venda.getTotal())
                .prazoRecebimentoDias(0)
                .status(status)
                .build();
    }

    private Recebivel recebivel(
            PagamentoVenda pagamento,
            StatusRecebivel status) {
        return Recebivel.builder()
                .loja(loja)
                .venda(venda)
                .pagamentoVenda(pagamento)
                .numeroParcela(1)
                .totalParcelas(1)
                .valorBruto(pagamento.getValorBruto())
                .taxaValor(pagamento.getTaxaValor())
                .valorLiquido(pagamento.getValorLiquido())
                .status(status)
                .build();
    }

    private void persistirPagamentoRecebidoPeloRepositorio() {
        when(pagamentoRepository.save(any(PagamentoVenda.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BigDecimal somar(
            List<PagamentoVenda> pagamentos,
            java.util.function.Function<PagamentoVenda, BigDecimal> campo) {
        return pagamentos.stream()
                .map(campo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal somarRecebiveis(
            List<Recebivel> recebiveis,
            java.util.function.Function<Recebivel, BigDecimal> campo) {
        return recebiveis.stream()
                .map(campo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private <T> List<T> lista(Iterable<T> itens) {
        List<T> resultado = new ArrayList<>();
        itens.forEach(resultado::add);
        return resultado;
    }

    private void assertMoney(String esperado, BigDecimal atual) {
        assertMoney(new BigDecimal(esperado), atual);
    }

    private void assertMoney(BigDecimal esperado, BigDecimal atual) {
        assertEquals(0, esperado.compareTo(atual),
                () -> "Esperado " + esperado + ", mas foi " + atual);
    }
}
