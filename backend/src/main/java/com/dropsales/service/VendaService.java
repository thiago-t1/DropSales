package com.dropsales.service;

import com.dropsales.dto.CancelarVendaRequest;
import com.dropsales.dto.ItemVendaRequest;
import com.dropsales.dto.PagamentoVendaRequest;
import com.dropsales.dto.VendaRequest;
import com.dropsales.dto.VendaResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ConflictException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.*;
import com.dropsales.repository.ProdutoRepository;
import com.dropsales.repository.TransacaoRepository;
import com.dropsales.repository.VendaRepository;
import com.dropsales.repository.LojaRepository;
import com.dropsales.util.VendaRequestFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendaService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_MONETARIA = 2;

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final TransacaoRepository transacaoRepository;
    private final LojaRepository lojaRepository;
    private final TenantContextService tenantContext;
    private final PagamentoVendaService pagamentoVendaService;

    /**
     * O bloqueio pessimista do usuario serializa a verificacao da chave e a criacao.
     * Assim, duas requisicoes concorrentes com a mesma chave nunca baixam estoque
     * nem geram transacoes duas vezes.
     */
    @Transactional(rollbackFor = Exception.class)
    public VendaResponse registrarVenda(
            VendaRequest request,
            UUID idempotencyKey) {
        if (idempotencyKey == null) {
            throw new BusinessException("Idempotency-Key e obrigatoria.");
        }

        TenantContextService.ContextoAtual contexto = tenantContext.atual();
        Loja loja = lojaRepository.findByIdForUpdate(contexto.loja().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loja nao encontrada"));
        String requestHash = VendaRequestFingerprint.calcular(request);

        return vendaRepository.findByLojaAndIdempotencyKey(loja, idempotencyKey)
                .map(vendaExistente -> {
                    if (!requestHash.equals(vendaExistente.getIdempotencyRequestHash())) {
                        throw new ConflictException(
                                "A Idempotency-Key informada ja foi usada com outro payload.");
                    }
                    log.info("Repeticao idempotente da venda #{}", vendaExistente.getId());
                    return toResponse(vendaExistente);
                })
                .orElseGet(() -> criarVenda(
                        request,
                        contexto.usuario(),
                        loja,
                        idempotencyKey,
                        requestHash));
    }

    private VendaResponse criarVenda(
            VendaRequest request,
            Usuario usuario,
            Loja loja,
            UUID idempotencyKey,
            String requestHash) {
        validarItens(request);
        Map<Produto, Integer> produtosDaVenda = carregarProdutosDaVenda(
                request,
                loja,
                Set.of());

        Venda venda = Venda.builder()
                .usuario(usuario)
                .loja(loja)
                .idempotencyKey(idempotencyKey)
                .idempotencyRequestHash(requestHash)
                .status(StatusVenda.CONCLUIDA)
                .observacao(request.getObservacao())
                .total(BigDecimal.ZERO)
                .formaPagamento(FormaPagamento.PIX)
                .taxaPagamentoPercentual(BigDecimal.ZERO)
                .taxaPagamentoValor(BigDecimal.ZERO)
                .valorLiquido(BigDecimal.ZERO)
                .build();
        venda.adicionarAuditoria(novaAuditoria(
                TipoAuditoriaVenda.CRIADA,
                usuario,
                "Venda criada"));

        venda = vendaRepository.saveAndFlush(venda);

        BigDecimal custoTotal = BigDecimal.ZERO;
        BigDecimal totalVenda = BigDecimal.ZERO;
        for (Map.Entry<Produto, Integer> entrada : produtosDaVenda.entrySet()) {
            Produto produto = entrada.getKey();
            int quantidade = entrada.getValue();
            BigDecimal subtotal = produto.getPrecoVenda()
                    .multiply(BigDecimal.valueOf(quantidade));

            venda.getItens().add(ItemVenda.builder()
                    .venda(venda)
                    .produto(produto)
                    .quantidade(quantidade)
                    .precoUnitario(produto.getPrecoVenda())
                    .subtotal(subtotal)
                    .build());

            totalVenda = totalVenda.add(subtotal);
            custoTotal = custoTotal.add(
                    produto.getPrecoCusto().multiply(BigDecimal.valueOf(quantidade)));
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - quantidade);
            produtoRepository.save(produto);
        }

        venda.setTotal(moeda(totalVenda));
        Venda vendaSalva = vendaRepository.saveAndFlush(venda);
        List<PagamentoVenda> pagamentos = pagamentoVendaService.processar(
                vendaSalva,
                loja,
                normalizarPagamentos(request, vendaSalva.getTotal()));
        aplicarResumoPagamentos(vendaSalva, pagamentos);
        vendaSalva = vendaRepository.saveAndFlush(vendaSalva);
        criarTransacoesFinanceiras(vendaSalva, usuario, custoTotal, false);

        log.info("Venda #{} concluida na loja #{}",
                vendaSalva.getId(),
                loja.getId());
        return toResponse(vendaSalva);
    }

    @Transactional(rollbackFor = Exception.class)
    public VendaResponse editarVenda(Long vendaId, VendaRequest request) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirGerencia();
        Usuario usuario = contexto.usuario();
        Loja loja = contexto.loja();
        Venda venda = vendaRepository.findByIdAndLojaForUpdate(vendaId, loja)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Venda nao encontrada: " + vendaId));

        if (venda.getStatus() == StatusVenda.CANCELADA) {
            throw new BusinessException("Uma venda cancelada nao pode ser editada.");
        }

        validarItens(request);
        bloquearProdutosDaEdicaoEmOrdemGlobal(venda, request, loja);
        BigDecimal totalAnterior = venda.getTotal();
        FormaPagamento formaAnterior = venda.getFormaPagamento();
        Map<Long, BigDecimal> precosHistoricos = venda.getItens().stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getProduto().getId(),
                        ItemVenda::getPrecoUnitario,
                        (primeiro, ignorado) -> primeiro,
                        TreeMap::new));

        devolverEstoque(venda, loja);
        venda.getItens().clear();
        cancelarTransacoesFinanceiras(venda);
        venda = vendaRepository.saveAndFlush(venda);

        Map<Produto, Integer> produtosDaVenda = carregarProdutosDaVenda(
                request,
                loja,
                precosHistoricos.keySet());
        venda.setObservacao(request.getObservacao());
        BigDecimal custoTotal = BigDecimal.ZERO;
        BigDecimal totalVenda = BigDecimal.ZERO;

        for (Map.Entry<Produto, Integer> entrada : produtosDaVenda.entrySet()) {
            Produto produto = entrada.getKey();
            int quantidade = entrada.getValue();
            BigDecimal precoUnitario = precosHistoricos.getOrDefault(
                    produto.getId(),
                    produto.getPrecoVenda());
            BigDecimal subtotal = precoUnitario
                    .multiply(BigDecimal.valueOf(quantidade));

            venda.getItens().add(ItemVenda.builder()
                    .venda(venda)
                    .produto(produto)
                    .quantidade(quantidade)
                    .precoUnitario(precoUnitario)
                    .subtotal(subtotal)
                    .build());

            totalVenda = totalVenda.add(subtotal);
            custoTotal = custoTotal.add(
                    produto.getPrecoCusto().multiply(BigDecimal.valueOf(quantidade)));
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - quantidade);
            produtoRepository.save(produto);
        }

        venda.setTotal(moeda(totalVenda));
        List<PagamentoVenda> pagamentos = pagamentoVendaService.substituir(
                venda,
                loja,
                normalizarPagamentos(request, venda.getTotal()));
        aplicarResumoPagamentos(venda, pagamentos);
        venda.adicionarAuditoria(novaAuditoria(
                TipoAuditoriaVenda.EDITADA,
                usuario,
                "Venda editada. Total anterior: R$ " + totalAnterior
                        + "; novo total: R$ " + venda.getTotal()
                        + "; forma anterior: " + formaAnterior
                        + "; nova forma: " + venda.getFormaPagamento()));
        Venda vendaSalva = vendaRepository.saveAndFlush(venda);
        criarTransacoesFinanceiras(vendaSalva, usuario, custoTotal, true);
        return toResponse(vendaSalva);
    }

    @Transactional(rollbackFor = Exception.class)
    public VendaResponse cancelarVenda(Long id, CancelarVendaRequest request) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirGerencia();
        Usuario usuario = contexto.usuario();
        Loja loja = contexto.loja();
        Venda venda = vendaRepository.findByIdAndLojaForUpdate(id, loja)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Venda nao encontrada: " + id));

        if (venda.getStatus() == StatusVenda.CANCELADA) {
            return toResponse(venda);
        }

        String motivo = request == null || request.getMotivo() == null
                ? ""
                : request.getMotivo().trim();
        if (motivo.isBlank()) {
            throw new BusinessException("O motivo do cancelamento e obrigatorio.");
        }
        if (motivo.length() > 500) {
            throw new BusinessException(
                    "O motivo do cancelamento deve ter no maximo 500 caracteres.");
        }

        devolverEstoque(venda, loja);
        cancelarTransacoesFinanceiras(venda);
        pagamentoVendaService.cancelar(venda);

        OffsetDateTime agoraUtc = OffsetDateTime.now(ZoneOffset.UTC);
        venda.setStatus(StatusVenda.CANCELADA);
        venda.setMotivoCancelamento(motivo);
        venda.setCanceladaPor(usuario);
        venda.setCanceladaEm(agoraUtc);
        venda.adicionarAuditoria(VendaAuditoria.builder()
                .tipo(TipoAuditoriaVenda.CANCELADA)
                .responsavel(usuario)
                .descricao("Venda cancelada. Motivo: " + motivo)
                .createdAt(agoraUtc)
                .build());

        Venda cancelada = vendaRepository.saveAndFlush(venda);
        log.info("Venda #{} cancelada pelo usuario #{}", id, usuario.getId());
        return toResponse(cancelada);
    }

    /**
     * O historico operacional inclui canceladas para preservar rastreabilidade.
     * Somente metricas e consultas analiticas do dashboard filtram CONCLUIDA.
     */
    @Transactional(readOnly = true)
    public List<VendaResponse> listarVendas() {
        Loja loja = tenantContext.atual().loja();
        return vendaRepository.findByLojaOrderByCreatedAtDesc(loja).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VendaResponse> listarVendasRecentes() {
        Loja loja = tenantContext.atual().loja();
        return vendaRepository
                .findTop5ByLojaAndStatusOrderByCreatedAtDesc(
                        loja,
                        StatusVenda.CONCLUIDA)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VendaResponse buscarPorId(Long id) {
        Loja loja = tenantContext.atual().loja();
        Venda venda = vendaRepository.findByIdAndLoja(id, loja)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Venda nao encontrada: " + id));
        return toResponse(venda);
    }

    private void validarItens(VendaRequest request) {
        if (request.getItens() == null || request.getItens().isEmpty()) {
            throw new BusinessException("A venda deve ter ao menos um item.");
        }
    }

    private void bloquearProdutosDaEdicaoEmOrdemGlobal(
            Venda venda,
            VendaRequest request,
            Loja loja) {
        TreeSet<Long> produtoIds = venda.getItens().stream()
                .map(item -> item.getProduto().getId())
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        request.getItens().stream()
                .map(ItemVendaRequest::getProdutoId)
                .forEach(produtoIds::add);
        produtoIds.forEach(produtoId ->
                buscarProdutoDaLojaParaAtualizacao(produtoId, loja));
    }

    private Map<Produto, Integer> carregarProdutosDaVenda(
            VendaRequest request,
            Loja loja,
            Set<Long> produtosHistoricosPermitidos) {
        Map<Long, Integer> quantidadesPorProduto = new TreeMap<>();
        for (ItemVendaRequest item : request.getItens()) {
            try {
                quantidadesPorProduto.merge(
                        item.getProdutoId(),
                        item.getQuantidade(),
                        Math::addExact);
            } catch (ArithmeticException ex) {
                throw new BusinessException(
                        "Quantidade total por produto excede o limite permitido.");
            }
        }

        Map<Produto, Integer> produtos = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entrada : quantidadesPorProduto.entrySet()) {
            Produto produto = buscarProdutoDaLojaParaAtualizacao(
                    entrada.getKey(),
                    loja);
            if (!Boolean.TRUE.equals(produto.getAtivo())
                    && !produtosHistoricosPermitidos.contains(produto.getId())) {
                throw new BusinessException(
                        "Produto inativo nao pode ser adicionado a venda: "
                                + produto.getNome());
            }
            int quantidade = entrada.getValue();
            if (produto.getQuantidadeEstoque() < quantidade) {
                throw new BusinessException(
                        "Estoque insuficiente para: " + produto.getNome()
                                + " (disponivel: " + produto.getQuantidadeEstoque()
                                + ", solicitado: " + quantidade + ")");
            }
            produtos.put(produto, quantidade);
        }
        return produtos;
    }

    private void devolverEstoque(Venda venda, Loja loja) {
        venda.getItens().stream()
                .sorted(Comparator.comparing(item -> item.getProduto().getId()))
                .forEach(item -> {
                    Produto produto = buscarProdutoDaLojaParaAtualizacao(
                            item.getProduto().getId(),
                            loja);
                    produto.setQuantidadeEstoque(
                            produto.getQuantidadeEstoque() + item.getQuantidade());
                    produtoRepository.save(produto);
                });
    }

    private Produto buscarProdutoDaLojaParaAtualizacao(
            Long produtoId,
            Loja loja) {
        return produtoRepository.findByIdAndLojaForUpdate(produtoId, loja)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Produto nao encontrado: " + produtoId));
    }

    private void cancelarTransacoesFinanceiras(Venda venda) {
        List<Transacao> transacoes = transacaoRepository.findByVenda(venda);
        transacoes.stream()
                .filter(transacao -> transacao.getStatus() != StatusTransacao.CANCELADO)
                .forEach(transacao -> transacao.setStatus(StatusTransacao.CANCELADO));
        transacaoRepository.saveAll(transacoes);
    }

    private void criarTransacoesFinanceiras(
            Venda venda,
            Usuario usuario,
            BigDecimal custoTotal,
            boolean editada) {
        String sufixo = editada ? " (editada)" : "";
        transacaoRepository.save(Transacao.builder()
                .descricao("Venda #" + venda.getId() + sufixo)
                .valor(venda.getTotal())
                .tipo(TipoTransacao.RECEITA)
                .status(StatusTransacao.PAGO)
                .usuario(usuario)
                .loja(venda.getLoja())
                .venda(venda)
                .build());

        if (custoTotal.compareTo(BigDecimal.ZERO) > 0) {
            transacaoRepository.save(Transacao.builder()
                    .descricao("Custo de Mercadorias - Venda #"
                            + venda.getId() + sufixo)
                    .valor(custoTotal)
                    .tipo(TipoTransacao.DESPESA)
                    .status(StatusTransacao.PAGO)
                    .usuario(usuario)
                    .loja(venda.getLoja())
                    .venda(venda)
                    .build());
        }
    }

    private VendaAuditoria novaAuditoria(
            TipoAuditoriaVenda tipo,
            Usuario responsavel,
            String descricao) {
        return VendaAuditoria.builder()
                .tipo(tipo)
                .responsavel(responsavel)
                .descricao(descricao)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private VendaResponse toResponse(Venda venda) {
        List<VendaResponse.ItemResponse> itens = venda.getItens().stream()
                .map(item -> VendaResponse.ItemResponse.builder()
                        .produtoId(item.getProduto().getId())
                        .produto(item.getProduto().getNome())
                        .quantidade(item.getQuantidade())
                        .precoUnitario(item.getPrecoUnitario())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        List<VendaResponse.AuditoriaResponse> auditorias = venda.getAuditorias().stream()
                .map(auditoria -> VendaResponse.AuditoriaResponse.builder()
                        .tipo(auditoria.getTipo())
                        .responsavel(auditoria.getResponsavel().getNome())
                        .descricao(auditoria.getDescricao())
                        .criadoEm(normalizarUtc(auditoria.getCreatedAt()))
                        .build())
                .toList();

        return VendaResponse.builder()
                .id(venda.getId())
                .idempotencyKey(venda.getIdempotencyKey())
                .vendedor(venda.getUsuario().getNome())
                .status(venda.getStatus())
                .total(venda.getTotal())
                .formaPagamento(venda.getFormaPagamento())
                .taxaPagamentoPercentual(venda.getTaxaPagamentoPercentual())
                .taxaPagamentoValor(venda.getTaxaPagamentoValor())
                .valorLiquido(venda.getValorLiquido())
                .observacao(venda.getObservacao())
                .motivoCancelamento(venda.getMotivoCancelamento())
                .canceladaPor(venda.getCanceladaPor() == null
                        ? null
                        : venda.getCanceladaPor().getNome())
                .canceladaEm(normalizarUtc(venda.getCanceladaEm()))
                .criadoEm(normalizarUtc(venda.getCreatedAt()))
                .itens(itens)
                .pagamentos(pagamentoVendaService.listar(venda))
                .auditorias(auditorias)
                .build();
    }

    private OffsetDateTime normalizarUtc(OffsetDateTime timestamp) {
        return timestamp == null
                ? null
                : timestamp.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private List<PagamentoVendaRequest> normalizarPagamentos(
            VendaRequest request,
            BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "O total da venda deve ser maior que zero para registrar pagamentos");
        }
        if (request.getPagamentos() != null && !request.getPagamentos().isEmpty()) {
            return request.getPagamentos();
        }

        FormaPagamento formaLegada = request.getFormaPagamento();
        if (formaLegada == null || !formaLegada.isFormaIndividual()) {
            throw new BusinessException("Informe ao menos uma forma de pagamento");
        }
        PagamentoVendaRequest pagamento = new PagamentoVendaRequest();
        pagamento.setFormaPagamento(formaLegada);
        pagamento.setValor(moeda(total));
        pagamento.setParcelas(1);
        if (formaLegada == FormaPagamento.DINHEIRO) {
            pagamento.setValorRecebido(moeda(total));
        }
        return List.of(pagamento);
    }

    private void aplicarResumoPagamentos(
            Venda venda,
            List<PagamentoVenda> pagamentos) {
        if (pagamentos == null || pagamentos.isEmpty()) {
            throw new BusinessException("A venda deve possuir ao menos um pagamento");
        }
        BigDecimal taxaTotal = pagamentos.stream()
                .map(PagamentoVenda::getTaxaValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal liquidoTotal = pagamentos.stream()
                .map(PagamentoVenda::getValorLiquido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long formasDistintas = pagamentos.stream()
                .map(PagamentoVenda::getFormaPagamento)
                .distinct()
                .count();
        FormaPagamento formaResumo = formasDistintas == 1
                ? pagamentos.get(0).getFormaPagamento()
                : FormaPagamento.MISTO;
        BigDecimal taxaEfetiva = venda.getTotal().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : taxaTotal.multiply(CEM)
                        .divide(venda.getTotal(), 2, RoundingMode.HALF_UP);

        venda.setFormaPagamento(formaResumo);
        venda.setTaxaPagamentoPercentual(taxaEfetiva);
        venda.setTaxaPagamentoValor(moeda(taxaTotal));
        venda.setValorLiquido(moeda(liquidoTotal));
    }

    private BigDecimal moeda(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
