package com.dropsales.service;

import com.dropsales.dto.CancelarVendaRequest;
import com.dropsales.dto.ItemVendaRequest;
import com.dropsales.dto.PagamentoVendaRequest;
import com.dropsales.dto.VendaRequest;
import com.dropsales.dto.VendaResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ConflictException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.Empresa;
import com.dropsales.model.FormaPagamento;
import com.dropsales.model.ItemVenda;
import com.dropsales.model.Loja;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.PagamentoVenda;
import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.Produto;
import com.dropsales.model.StatusPagamentoVenda;
import com.dropsales.model.StatusTransacao;
import com.dropsales.model.StatusVenda;
import com.dropsales.model.TipoAuditoriaVenda;
import com.dropsales.model.TipoTransacao;
import com.dropsales.model.Transacao;
import com.dropsales.model.Usuario;
import com.dropsales.model.Venda;
import com.dropsales.model.VendaAuditoria;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.ProdutoRepository;
import com.dropsales.repository.TransacaoRepository;
import com.dropsales.repository.VendaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendaServiceTest {

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private TenantContextService tenantContext;
    @Mock private PagamentoVendaService pagamentoVendaService;

    private VendaService vendaService;
    private Empresa empresa;
    private Loja lojaA;
    private Loja lojaB;
    private Usuario operadorA;
    private Usuario operadorB;

    @BeforeEach
    void setUp() {
        vendaService = new VendaService(
                vendaRepository,
                produtoRepository,
                transacaoRepository,
                lojaRepository,
                tenantContext,
                pagamentoVendaService);
        empresa = Empresa.builder().id(1L).nome("Empresa").ativo(true).build();
        lojaA = loja(10L, "Loja A");
        lojaB = loja(20L, "Loja B");
        operadorA = usuario(100L, "Operador A");
        operadorB = usuario(200L, "Operador B");
        lenient().when(pagamentoVendaService.listar(any(Venda.class)))
                .thenReturn(List.of());
    }

    @Test
    void vendaNaoAceitaProdutoDeOutraLoja() {
        UUID chave = UUID.randomUUID();
        when(tenantContext.atual()).thenReturn(contexto(operadorA, lojaA));
        when(lojaRepository.findByIdForUpdate(lojaA.getId()))
                .thenReturn(Optional.of(lojaA));
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaA, chave))
                .thenReturn(Optional.empty());
        when(produtoRepository.findByIdAndLojaForUpdate(99L, lojaA))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> vendaService.registrarVenda(vendaLegada(item(99L, 1)), chave));

        verify(vendaRepository, never()).saveAndFlush(any());
        verify(pagamentoVendaService, never()).processar(any(), any(), anyList());
    }

    @Test
    void vendaRejeitaOverflowAoSomarItensDuplicados() {
        UUID chave = UUID.randomUUID();
        when(tenantContext.atual()).thenReturn(contexto(operadorA, lojaA));
        when(lojaRepository.findByIdForUpdate(lojaA.getId()))
                .thenReturn(Optional.of(lojaA));
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaA, chave))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vendaService.registrarVenda(
                        vendaLegada(
                                item(99L, Integer.MAX_VALUE),
                                item(99L, 3)),
                        chave));

        assertEquals(
                "Quantidade total por produto excede o limite permitido.",
                exception.getMessage());
        verify(produtoRepository, never())
                .findByIdAndLojaForUpdate(any(), any());
        verify(vendaRepository, never()).saveAndFlush(any());
    }

    @Test
    void novaVendaNaoAceitaProdutoInativo() {
        UUID chave = UUID.randomUUID();
        Produto produto = produto(31L, lojaA, "5.00", "10.00", 10);
        produto.setAtivo(false);
        when(tenantContext.atual()).thenReturn(contexto(operadorA, lojaA));
        when(lojaRepository.findByIdForUpdate(lojaA.getId()))
                .thenReturn(Optional.of(lojaA));
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaA, chave))
                .thenReturn(Optional.empty());
        when(produtoRepository.findByIdAndLojaForUpdate(31L, lojaA))
                .thenReturn(Optional.of(produto));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> vendaService.registrarVenda(
                        vendaLegada(item(31L, 1)),
                        chave));

        assertTrue(exception.getMessage().contains("Produto inativo"));
        verify(vendaRepository, never()).saveAndFlush(any());
    }

    @Test
    void mesmaChaveNaMesmaLojaEntreOperadoresProduzUmaUnicaVenda() {
        UUID chave = UUID.randomUUID();
        Produto produto = produto(30L, lojaA, "5.00", "10.00", 10);
        AtomicReference<Venda> persistida = new AtomicReference<>();
        when(tenantContext.atual()).thenReturn(
                contexto(operadorA, lojaA),
                contexto(operadorB, lojaA));
        when(lojaRepository.findByIdForUpdate(lojaA.getId()))
                .thenReturn(Optional.of(lojaA));
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaA, chave))
                .thenAnswer(invocation -> Optional.ofNullable(persistida.get()));
        when(produtoRepository.findByIdAndLojaForUpdate(30L, lojaA))
                .thenReturn(Optional.of(produto));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(invocation -> {
            Venda venda = invocation.getArgument(0);
            venda.setId(300L);
            venda.setCreatedAt(OffsetDateTime.parse("2026-07-28T06:00:00Z"));
            persistida.set(venda);
            return venda;
        });
        when(pagamentoVendaService.processar(
                any(Venda.class),
                eq(lojaA),
                anyList())).thenAnswer(invocation -> pagamentosSemTaxa(
                        invocation.getArgument(0),
                        lojaA,
                        invocation.getArgument(2)));

        VendaRequest request = vendaLegada(item(30L, 2));
        VendaResponse primeira = vendaService.registrarVenda(request, chave);
        VendaResponse repetida = vendaService.registrarVenda(request, chave);

        assertEquals(primeira, repetida);
        assertEquals("Operador A", primeira.getVendedor());
        assertEquals(8, produto.getQuantidadeEstoque());
        verify(produtoRepository, times(1))
                .findByIdAndLojaForUpdate(30L, lojaA);
        verify(pagamentoVendaService, times(1))
                .processar(any(), eq(lojaA), anyList());
        verify(transacaoRepository, times(2)).save(any());
    }

    @Test
    void mesmaChaveComPayloadDiferenteRetornaConflitoSemNovaBaixa() {
        UUID chave = UUID.randomUUID();
        Produto produto = produto(32L, lojaA, "5.00", "10.00", 10);
        AtomicReference<Venda> persistida = new AtomicReference<>();
        when(tenantContext.atual()).thenReturn(contexto(operadorA, lojaA));
        when(lojaRepository.findByIdForUpdate(lojaA.getId()))
                .thenReturn(Optional.of(lojaA));
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaA, chave))
                .thenAnswer(invocation -> Optional.ofNullable(persistida.get()));
        when(produtoRepository.findByIdAndLojaForUpdate(32L, lojaA))
                .thenReturn(Optional.of(produto));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(invocation -> {
            Venda venda = invocation.getArgument(0);
            venda.setId(320L);
            venda.setCreatedAt(OffsetDateTime.parse("2026-07-28T06:00:00Z"));
            persistida.set(venda);
            return venda;
        });
        when(pagamentoVendaService.processar(any(), eq(lojaA), anyList()))
                .thenAnswer(invocation -> pagamentosSemTaxa(
                        invocation.getArgument(0),
                        lojaA,
                        invocation.getArgument(2)));

        vendaService.registrarVenda(vendaLegada(item(32L, 1)), chave);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> vendaService.registrarVenda(
                        vendaLegada(item(32L, 2)),
                        chave));

        assertTrue(exception.getMessage().contains("outro payload"));
        assertEquals(9, produto.getQuantidadeEstoque());
        verify(produtoRepository, times(1))
                .findByIdAndLojaForUpdate(32L, lojaA);
        verify(pagamentoVendaService, times(1))
                .processar(any(), eq(lojaA), anyList());
    }

    @Test
    void mesmaChaveEmLojasDiferentesCriaVendasIndependentes() {
        UUID chave = UUID.randomUUID();
        Produto produtoA = produto(40L, lojaA, "1.00", "5.00", 5);
        Produto produtoB = produto(40L, lojaB, "1.00", "5.00", 7);
        AtomicLong ids = new AtomicLong(400L);
        when(tenantContext.atual()).thenReturn(
                contexto(operadorA, lojaA),
                contexto(operadorA, lojaB));
        when(lojaRepository.findByIdForUpdate(lojaA.getId()))
                .thenReturn(Optional.of(lojaA));
        when(lojaRepository.findByIdForUpdate(lojaB.getId()))
                .thenReturn(Optional.of(lojaB));
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaA, chave))
                .thenReturn(Optional.empty());
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaB, chave))
                .thenReturn(Optional.empty());
        when(produtoRepository.findByIdAndLojaForUpdate(40L, lojaA))
                .thenReturn(Optional.of(produtoA));
        when(produtoRepository.findByIdAndLojaForUpdate(40L, lojaB))
                .thenReturn(Optional.of(produtoB));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(invocation -> {
            Venda venda = invocation.getArgument(0);
            if (venda.getId() == null) venda.setId(ids.incrementAndGet());
            venda.setCreatedAt(OffsetDateTime.parse("2026-07-28T06:00:00Z"));
            return venda;
        });
        when(pagamentoVendaService.processar(any(), any(), anyList()))
                .thenAnswer(invocation -> pagamentosSemTaxa(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)));

        VendaResponse vendaA = vendaService.registrarVenda(
                vendaLegada(item(40L, 1)),
                chave);
        VendaResponse vendaB = vendaService.registrarVenda(
                vendaLegada(item(40L, 1)),
                chave);

        assertNotEquals(vendaA.getId(), vendaB.getId());
        assertEquals(4, produtoA.getQuantidadeEstoque());
        assertEquals(6, produtoB.getQuantidadeEstoque());
        verify(vendaRepository).findByLojaAndIdempotencyKey(lojaA, chave);
        verify(vendaRepository).findByLojaAndIdempotencyKey(lojaB, chave);
    }

    @Test
    void notificacoesConsultamSomenteAsCincoVendasConcluidasMaisRecentes() {
        Produto produto = produto(45L, lojaA, "1.00", "5.00", 5);
        Venda venda = vendaConcluida(450L, lojaA, operadorA, produto, 1);
        when(tenantContext.atual()).thenReturn(contexto(operadorA, lojaA));
        when(vendaRepository.findTop5ByLojaAndStatusOrderByCreatedAtDesc(
                lojaA,
                StatusVenda.CONCLUIDA))
                .thenReturn(List.of(venda));

        List<VendaResponse> recentes = vendaService.listarVendasRecentes();

        assertEquals(1, recentes.size());
        assertEquals(venda.getId(), recentes.get(0).getId());
        verify(vendaRepository).findTop5ByLojaAndStatusOrderByCreatedAtDesc(
                lojaA,
                StatusVenda.CONCLUIDA);
        verify(vendaRepository, never()).findByLojaOrderByCreatedAtDesc(lojaA);
    }

    @Test
    void splitGeraResumoMistoComTaxaELiquidoAgregados() {
        UUID chave = UUID.randomUUID();
        Produto produto = produto(50L, lojaA, "10.00", "100.00", 3);
        prepararNovaVenda(chave, produto);
        PagamentoVendaRequest pix = pagamento(FormaPagamento.PIX, "40.00");
        PagamentoVendaRequest credito = pagamento(
                FormaPagamento.CARTAO_CREDITO,
                "60.00");
        when(pagamentoVendaService.processar(
                any(),
                eq(lojaA),
                eq(List.of(pix, credito)))).thenAnswer(invocation -> List.of(
                        pagamentoPersistido(
                                invocation.getArgument(0),
                                lojaA,
                                FormaPagamento.PIX,
                                "40.00",
                                "0.00"),
                        pagamentoPersistido(
                                invocation.getArgument(0),
                                lojaA,
                                FormaPagamento.CARTAO_CREDITO,
                                "60.00",
                                "2.40")));
        VendaRequest request = vendaComPagamentos(
                List.of(pix, credito),
                item(50L, 1));

        VendaResponse response = vendaService.registrarVenda(request, chave);

        assertEquals(FormaPagamento.MISTO, response.getFormaPagamento());
        assertEquals(new BigDecimal("2.40"), response.getTaxaPagamentoValor());
        assertEquals(new BigDecimal("97.60"), response.getValorLiquido());
        assertEquals(new BigDecimal("2.40"), response.getTaxaPagamentoPercentual());
        verify(transacaoRepository, times(2)).save(any());
    }

    @Test
    void payloadLegadoEhConvertidoParaPagamentoDaLoja() {
        UUID chave = UUID.randomUUID();
        Produto produto = produto(60L, lojaA, "0.00", "25.00", 3);
        prepararNovaVenda(chave, produto);
        ArgumentCaptor<List<PagamentoVendaRequest>> captor =
                ArgumentCaptor.captor();
        when(pagamentoVendaService.processar(
                any(),
                eq(lojaA),
                captor.capture())).thenAnswer(invocation -> List.of(
                        pagamentoPersistido(
                                invocation.getArgument(0),
                                lojaA,
                                FormaPagamento.CARTAO_DEBITO,
                                "25.00",
                                "0.38")));
        VendaRequest request = vendaLegada(item(60L, 1));
        request.setFormaPagamento(FormaPagamento.CARTAO_DEBITO);
        request.setTaxaPagamentoPercentual(new BigDecimal("1.50"));

        VendaResponse response = vendaService.registrarVenda(request, chave);

        assertEquals(1, captor.getValue().size());
        assertEquals(
                FormaPagamento.CARTAO_DEBITO,
                captor.getValue().get(0).getFormaPagamento());
        assertEquals(new BigDecimal("25.00"), captor.getValue().get(0).getValor());
        assertEquals(new BigDecimal("0.38"), response.getTaxaPagamentoValor());
    }

    @Test
    void editarSubstituiPagamentosReverteEstoqueTransacoesEAudita() {
        Produto produto = produto(70L, lojaA, "4.00", "10.00", 8);
        Venda venda = vendaConcluida(700L, lojaA, operadorA, produto, 2);
        produto.setAtivo(false);
        produto.setPrecoVenda(new BigDecimal("15.00"));
        Transacao receita = transacao(venda, TipoTransacao.RECEITA, "20.00");
        Transacao cmv = transacao(venda, TipoTransacao.DESPESA, "8.00");
        VendaRequest request = vendaLegada(item(70L, 1));
        request.setFormaPagamento(FormaPagamento.CARTAO_DEBITO);

        when(tenantContext.exigirGerencia()).thenReturn(contexto(operadorB, lojaA));
        when(vendaRepository.findByIdAndLojaForUpdate(700L, lojaA))
                .thenReturn(Optional.of(venda));
        when(produtoRepository.findByIdAndLojaForUpdate(70L, lojaA))
                .thenReturn(Optional.of(produto));
        when(transacaoRepository.findByVenda(venda))
                .thenReturn(List.of(receita, cmv));
        when(vendaRepository.saveAndFlush(venda)).thenReturn(venda);
        when(pagamentoVendaService.substituir(
                eq(venda),
                eq(lojaA),
                anyList())).thenReturn(List.of(pagamentoPersistido(
                        venda,
                        lojaA,
                        FormaPagamento.CARTAO_DEBITO,
                        "10.00",
                        "0.50")));

        VendaResponse response = vendaService.editarVenda(700L, request);

        assertEquals(new BigDecimal("10.00"), response.getTotal());
        assertEquals(new BigDecimal("10.00"), response.getItens().get(0).getPrecoUnitario());
        assertEquals(new BigDecimal("0.50"), response.getTaxaPagamentoValor());
        assertEquals(9, produto.getQuantidadeEstoque());
        assertEquals(StatusTransacao.CANCELADO, receita.getStatus());
        assertEquals(StatusTransacao.CANCELADO, cmv.getStatus());
        assertTrue(response.getAuditorias().stream()
                .anyMatch(auditoria ->
                        auditoria.getTipo() == TipoAuditoriaVenda.EDITADA
                        && auditoria.getResponsavel().equals("Operador B")));
        verify(pagamentoVendaService).substituir(
                eq(venda),
                eq(lojaA),
                anyList());
    }

    @Test
    void cancelamentoEhIdempotenteERestauraTudoUmaVez() {
        Produto produto = produto(80L, lojaA, "3.00", "10.00", 8);
        Venda venda = vendaConcluida(800L, lojaA, operadorA, produto, 2);
        Transacao receita = transacao(venda, TipoTransacao.RECEITA, "20.00");
        Transacao cmv = transacao(venda, TipoTransacao.DESPESA, "6.00");
        when(tenantContext.exigirGerencia()).thenReturn(contexto(operadorB, lojaA));
        when(vendaRepository.findByIdAndLojaForUpdate(800L, lojaA))
                .thenReturn(Optional.of(venda));
        when(produtoRepository.findByIdAndLojaForUpdate(80L, lojaA))
                .thenReturn(Optional.of(produto));
        when(transacaoRepository.findByVenda(venda))
                .thenReturn(List.of(receita, cmv));
        when(vendaRepository.saveAndFlush(venda)).thenReturn(venda);

        VendaResponse primeira = vendaService.cancelarVenda(
                800L,
                cancelamento("Cliente desistiu"));
        VendaResponse repetida = vendaService.cancelarVenda(
                800L,
                cancelamento("Cliente desistiu"));

        assertEquals(primeira, repetida);
        assertEquals(StatusVenda.CANCELADA, primeira.getStatus());
        assertEquals(10, produto.getQuantidadeEstoque());
        assertEquals(ZoneOffset.UTC, primeira.getCanceladaEm().getOffset());
        assertEquals(1, primeira.getAuditorias().stream()
                .filter(auditoria ->
                        auditoria.getTipo() == TipoAuditoriaVenda.CANCELADA)
                .count());
        verify(pagamentoVendaService, times(1)).cancelar(venda);
        verify(transacaoRepository, times(1)).saveAll(any());
        verify(produtoRepository, times(1)).save(produto);
    }

    @Test
    void vendaCanceladaNaoPodeSerEditadaEOutraLojaNaoPodeAcessar() {
        Produto produto = produto(90L, lojaA, "3.00", "10.00", 8);
        Venda venda = vendaConcluida(900L, lojaA, operadorA, produto, 1);
        venda.setStatus(StatusVenda.CANCELADA);
        when(tenantContext.exigirGerencia()).thenReturn(contexto(operadorA, lojaA));
        when(tenantContext.atual()).thenReturn(contexto(operadorA, lojaB));
        when(vendaRepository.findByIdAndLojaForUpdate(900L, lojaA))
                .thenReturn(Optional.of(venda));
        when(vendaRepository.findByIdAndLoja(900L, lojaB))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessException.class,
                () -> vendaService.editarVenda(
                        900L,
                        vendaLegada(item(90L, 1))));
        assertThrows(
                ResourceNotFoundException.class,
                () -> vendaService.buscarPorId(900L));
        verify(produtoRepository, never()).save(any());
    }

    private void prepararNovaVenda(UUID chave, Produto produto) {
        when(tenantContext.atual()).thenReturn(contexto(operadorA, lojaA));
        when(lojaRepository.findByIdForUpdate(lojaA.getId()))
                .thenReturn(Optional.of(lojaA));
        when(vendaRepository.findByLojaAndIdempotencyKey(lojaA, chave))
                .thenReturn(Optional.empty());
        when(produtoRepository.findByIdAndLojaForUpdate(produto.getId(), lojaA))
                .thenReturn(Optional.of(produto));
        when(vendaRepository.saveAndFlush(any(Venda.class))).thenAnswer(invocation -> {
            Venda venda = invocation.getArgument(0);
            venda.setId(999L);
            venda.setCreatedAt(OffsetDateTime.parse("2026-07-28T06:00:00Z"));
            return venda;
        });
    }

    private List<PagamentoVenda> pagamentosSemTaxa(
            Venda venda,
            Loja loja,
            List<PagamentoVendaRequest> requests) {
        List<PagamentoVenda> resultado = new ArrayList<>();
        for (PagamentoVendaRequest request : requests) {
            resultado.add(pagamentoPersistido(
                    venda,
                    loja,
                    request.getFormaPagamento(),
                    request.getValor().toPlainString(),
                    "0.00"));
        }
        return resultado;
    }

    private PagamentoVenda pagamentoPersistido(
            Venda venda,
            Loja loja,
            FormaPagamento forma,
            String bruto,
            String taxa) {
        BigDecimal valorBruto = new BigDecimal(bruto);
        BigDecimal taxaValor = new BigDecimal(taxa);
        return PagamentoVenda.builder()
                .venda(venda)
                .loja(loja)
                .formaPagamento(forma)
                .parcelas(1)
                .valorBruto(valorBruto)
                .taxaPercentual(BigDecimal.ZERO)
                .taxaFixa(BigDecimal.ZERO)
                .taxaValor(taxaValor)
                .valorLiquido(valorBruto.subtract(taxaValor))
                .prazoRecebimentoDias(0)
                .status(StatusPagamentoVenda.ATIVO)
                .build();
    }

    private Venda vendaConcluida(
            Long id,
            Loja loja,
            Usuario criador,
            Produto produto,
            int quantidade) {
        BigDecimal total = produto.getPrecoVenda()
                .multiply(BigDecimal.valueOf(quantidade));
        Venda venda = Venda.builder()
                .id(id)
                .loja(loja)
                .usuario(criador)
                .idempotencyKey(UUID.randomUUID())
                .status(StatusVenda.CONCLUIDA)
                .total(total)
                .formaPagamento(FormaPagamento.PIX)
                .taxaPagamentoPercentual(BigDecimal.ZERO.setScale(2))
                .taxaPagamentoValor(BigDecimal.ZERO.setScale(2))
                .valorLiquido(total)
                .createdAt(OffsetDateTime.parse("2026-07-28T06:00:00Z"))
                .build();
        venda.getItens().add(ItemVenda.builder()
                .venda(venda)
                .produto(produto)
                .quantidade(quantidade)
                .precoUnitario(produto.getPrecoVenda())
                .subtotal(total)
                .build());
        venda.adicionarAuditoria(VendaAuditoria.builder()
                .tipo(TipoAuditoriaVenda.CRIADA)
                .responsavel(criador)
                .descricao("Venda criada")
                .createdAt(venda.getCreatedAt())
                .build());
        return venda;
    }

    private Transacao transacao(Venda venda, TipoTransacao tipo, String valor) {
        return Transacao.builder()
                .venda(venda)
                .loja(venda.getLoja())
                .usuario(venda.getUsuario())
                .descricao(tipo.name())
                .tipo(tipo)
                .status(StatusTransacao.PAGO)
                .valor(new BigDecimal(valor))
                .build();
    }

    private VendaRequest vendaLegada(ItemVendaRequest... itens) {
        VendaRequest request = new VendaRequest();
        request.setItens(List.of(itens));
        request.setFormaPagamento(FormaPagamento.PIX);
        request.setTaxaPagamentoPercentual(BigDecimal.ZERO);
        return request;
    }

    private VendaRequest vendaComPagamentos(
            List<PagamentoVendaRequest> pagamentos,
            ItemVendaRequest... itens) {
        VendaRequest request = new VendaRequest();
        request.setItens(List.of(itens));
        request.setPagamentos(pagamentos);
        return request;
    }

    private PagamentoVendaRequest pagamento(FormaPagamento forma, String valor) {
        PagamentoVendaRequest request = new PagamentoVendaRequest();
        request.setFormaPagamento(forma);
        request.setValor(new BigDecimal(valor));
        request.setParcelas(1);
        return request;
    }

    private CancelarVendaRequest cancelamento(String motivo) {
        CancelarVendaRequest request = new CancelarVendaRequest();
        request.setMotivo(motivo);
        return request;
    }

    private ItemVendaRequest item(Long produtoId, int quantidade) {
        ItemVendaRequest item = new ItemVendaRequest();
        item.setProdutoId(produtoId);
        item.setQuantidade(quantidade);
        return item;
    }

    private Produto produto(
            Long id,
            Loja loja,
            String custo,
            String venda,
            int estoque) {
        return Produto.builder()
                .id(id)
                .loja(loja)
                .usuario(operadorA)
                .nome("Produto " + id)
                .precoCusto(new BigDecimal(custo))
                .precoVenda(new BigDecimal(venda))
                .quantidadeEstoque(estoque)
                .estoqueMinimo(2)
                .ativo(true)
                .build();
    }

    private Loja loja(Long id, String nome) {
        return Loja.builder()
                .id(id)
                .empresa(empresa)
                .nome(nome)
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build();
    }

    private Usuario usuario(Long id, String nome) {
        return Usuario.builder()
                .id(id)
                .nome(nome)
                .email(nome.toLowerCase().replace(" ", ".") + "@teste.com")
                .build();
    }

    private TenantContextService.ContextoAtual contexto(
            Usuario usuario,
            Loja loja) {
        MembroEmpresa membro = MembroEmpresa.builder()
                .id(usuario.getId())
                .empresa(empresa)
                .usuario(usuario)
                .papel(PapelEmpresa.OPERADOR)
                .ativo(true)
                .build();
        return new TenantContextService.ContextoAtual(
                usuario,
                empresa,
                loja,
                membro);
    }
}
