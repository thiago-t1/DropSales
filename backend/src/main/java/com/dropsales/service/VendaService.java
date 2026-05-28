package com.dropsales.service;

import com.dropsales.dto.*;
import com.dropsales.exception.*;
import com.dropsales.model.*;
import com.dropsales.repository.*;
import com.dropsales.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TransacaoRepository transacaoRepository;
    private final EntityManager entityManager;

    private Usuario getUsuarioLogado() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) throw new BusinessException("Usuário não autenticado");
        return usuarioRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    /**
     * Registra uma nova venda:
     * 1. Valida estoque de cada item antes de qualquer escrita
     * 2. Salva a Venda (shell) para obter ID persistido
     * 3. Cria e associa cada ItemVenda com referencia a Venda salva
     * 4. Atualiza estoque de cada Produto
     * 5. Gera Transacao RECEITA (valor total) e Transacao DESPESA (CMV)
     */
    @Transactional(rollbackFor = Exception.class)
    public VendaResponse registrarVenda(VendaRequest request, String emailUsuario) {
        log.info("Registrando venda para o usuario: {}", emailUsuario);

        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado: " + emailUsuario));

        if (request.getItens() == null || request.getItens().isEmpty()) {
            throw new BusinessException("A venda deve ter ao menos um item.");
        }

        // Passo 1: Valida estoque de todos os itens antes de persistir qualquer coisa
        for (ItemVendaRequest itemReq : request.getItens()) {
            Produto produto = produtoRepository.findById(itemReq.getProdutoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Produto nao encontrado: " + itemReq.getProdutoId()));
            if (produto.getQuantidadeEstoque() < itemReq.getQuantidade()) {
                throw new BusinessException("Estoque insuficiente para: " + produto.getNome()
                        + " (disponivel: " + produto.getQuantidadeEstoque()
                        + ", solicitado: " + itemReq.getQuantidade() + ")");
            }
        }

        // Passo 2: Salva Venda shell para obter ID (total = 0, sem itens ainda)
        Venda venda = Venda.builder()
                .usuario(usuario)
                .observacao(request.getObservacao())
                .total(BigDecimal.ZERO)
                .build();
        venda = vendaRepository.saveAndFlush(venda);
        log.debug("Venda #{} salva (shell)", venda.getId());

        // Passo 3: Constroi itens com referencia a Venda ja persistida
        BigDecimal custoTotal = BigDecimal.ZERO;
        BigDecimal totalVenda = BigDecimal.ZERO;

        for (ItemVendaRequest itemReq : request.getItens()) {
            Produto produto = produtoRepository.findById(itemReq.getProdutoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Produto nao encontrado: " + itemReq.getProdutoId()));

            BigDecimal subtotal = produto.getPrecoVenda()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

            ItemVenda item = ItemVenda.builder()
                    .venda(venda)
                    .produto(produto)
                    .quantidade(itemReq.getQuantidade())
                    .precoUnitario(produto.getPrecoVenda())
                    .subtotal(subtotal)
                    .build();
            venda.getItens().add(item);

            totalVenda = totalVenda.add(subtotal);
            custoTotal = custoTotal.add(
                    produto.getPrecoCusto()
                           .multiply(BigDecimal.valueOf(itemReq.getQuantidade())));

            // Atualiza estoque do produto
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - itemReq.getQuantidade());
            produtoRepository.save(produto);
        }

        // Passo 4: Atualiza total e salva venda com os itens em cascade
        venda.setTotal(totalVenda);
        // saveAndFlush garante que @CreationTimestamp e os IDs dos itens sejam populados
        Venda vendaSalva = vendaRepository.saveAndFlush(venda);
        // refresh sincroniza o estado gerenciado com o banco (popula createdAt)
        entityManager.refresh(vendaSalva);
        log.info("Venda #{} finalizada. Total: R$ {} | CMV: R$ {}",
                vendaSalva.getId(), totalVenda, custoTotal);
        final Venda vendaFinal = vendaSalva;

        // Passo 5a: Transacao RECEITA
        transacaoRepository.save(Transacao.builder()
                .descricao("Venda #" + vendaFinal.getId())
                .valor(vendaFinal.getTotal())
                .tipo(TipoTransacao.RECEITA)
                .status(StatusTransacao.PAGO)
                .usuario(usuario)
                .venda(vendaFinal)
                .build());

        // Passo 5b: Transacao DESPESA (CMV) — apenas se houver custo real
        if (custoTotal.compareTo(BigDecimal.ZERO) > 0) {
            transacaoRepository.save(Transacao.builder()
                    .descricao("Custo de Mercadorias - Venda #" + vendaFinal.getId())
                    .valor(custoTotal)
                    .tipo(TipoTransacao.DESPESA)
                    .status(StatusTransacao.PAGO)
                    .usuario(usuario)
                    .venda(vendaFinal)
                    .build());
        }

        return toResponse(vendaFinal);
    }

    /**
     * Edita uma venda existente:
     * 1. Devolve estoque dos itens antigos
     * 2. Remove itens e transacoes antigas
     * 3. Valida estoque para novos itens
     * 4. Aplica novos itens, atualiza estoque, recria transacoes
     */
    @Transactional(rollbackFor = Exception.class)
    public VendaResponse editarVenda(Long vendaId, VendaRequest request) {
        log.info("Editando venda #{}", vendaId);
        Usuario usuarioLogado = getUsuarioLogado();

        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new ResourceNotFoundException("Venda nao encontrada: " + vendaId));

        if (!venda.getUsuario().getId().equals(usuarioLogado.getId())) {
            throw new ResourceNotFoundException("Venda nao encontrada: " + vendaId);
        }

        if (request.getItens() == null || request.getItens().isEmpty()) {
            throw new BusinessException("A venda deve ter ao menos um item.");
        }

        // 1) Devolve estoque dos itens antigos
        for (ItemVenda oldItem : venda.getItens()) {
            Produto p = oldItem.getProduto();
            p.setQuantidadeEstoque(p.getQuantidadeEstoque() + oldItem.getQuantidade());
            produtoRepository.save(p);
        }

        // 2) Remove itens e transacoes antigas
        venda.getItens().clear();
        List<Transacao> transacoes = transacaoRepository.findByVenda(venda);
        transacaoRepository.deleteAll(transacoes);
        venda = vendaRepository.saveAndFlush(venda);

        // 3) Valida estoque para novos itens
        for (ItemVendaRequest itemReq : request.getItens()) {
            Produto produto = produtoRepository.findById(itemReq.getProdutoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Produto nao encontrado: " + itemReq.getProdutoId()));
            if (produto.getQuantidadeEstoque() < itemReq.getQuantidade()) {
                throw new BusinessException("Estoque insuficiente para: " + produto.getNome()
                        + " (disponivel: " + produto.getQuantidadeEstoque() + ")");
            }
        }

        // 4) Aplica novos itens
        venda.setObservacao(request.getObservacao());
        BigDecimal custoTotal = BigDecimal.ZERO;
        BigDecimal totalVenda = BigDecimal.ZERO;

        for (ItemVendaRequest itemReq : request.getItens()) {
            Produto produto = produtoRepository.findById(itemReq.getProdutoId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Produto: " + itemReq.getProdutoId()));

            BigDecimal subtotal = produto.getPrecoVenda()
                    .multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

            ItemVenda item = ItemVenda.builder()
                    .venda(venda)
                    .produto(produto)
                    .quantidade(itemReq.getQuantidade())
                    .precoUnitario(produto.getPrecoVenda())
                    .subtotal(subtotal)
                    .build();
            venda.getItens().add(item);

            totalVenda = totalVenda.add(subtotal);
            custoTotal = custoTotal.add(
                    produto.getPrecoCusto()
                           .multiply(BigDecimal.valueOf(itemReq.getQuantidade())));

            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - itemReq.getQuantidade());
            produtoRepository.save(produto);
        }

        venda.setTotal(totalVenda);
        final Venda vendaSalva = vendaRepository.save(venda);

        // 5) Recria transacoes financeiras
        transacaoRepository.save(Transacao.builder()
                .descricao("Venda #" + vendaSalva.getId() + " (editada)")
                .valor(vendaSalva.getTotal())
                .tipo(TipoTransacao.RECEITA)
                .status(StatusTransacao.PAGO)
                .usuario(venda.getUsuario())
                .venda(vendaSalva)
                .build());

        if (custoTotal.compareTo(BigDecimal.ZERO) > 0) {
            transacaoRepository.save(Transacao.builder()
                    .descricao("Custo de Mercadorias - Venda #" + vendaSalva.getId() + " (editada)")
                    .valor(custoTotal)
                    .tipo(TipoTransacao.DESPESA)
                    .status(StatusTransacao.PAGO)
                    .usuario(venda.getUsuario())
                    .venda(vendaSalva)
                    .build());
        }

        return toResponse(vendaSalva);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelarVenda(Long id) {
        log.info("Cancelando venda #{}", id);
        Usuario usuarioLogado = getUsuarioLogado();

        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda nao encontrada: " + id));

        if (!venda.getUsuario().getId().equals(usuarioLogado.getId())) {
            throw new ResourceNotFoundException("Venda nao encontrada: " + id);
        }

        for (ItemVenda item : venda.getItens()) {
            Produto produto = item.getProduto();
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() + item.getQuantidade());
            produtoRepository.save(produto);
        }

        transacaoRepository.deleteAll(transacaoRepository.findByVenda(venda));
        vendaRepository.delete(venda);
    }

    public List<VendaResponse> listarVendas(String emailUsuario) {
        Usuario usuario = usuarioRepository.findByEmail(emailUsuario)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado: " + emailUsuario));

        return vendaRepository.findByUsuarioOrderByCreatedAtDesc(usuario).stream()
                .map(this::toResponse)
                .toList();
    }

    public VendaResponse buscarPorId(Long id) {
        Usuario usuarioLogado = getUsuarioLogado();
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda nao encontrada: " + id));

        if (!venda.getUsuario().getId().equals(usuarioLogado.getId())) {
            throw new ResourceNotFoundException("Venda nao encontrada: " + id);
        }
        return toResponse(venda);
    }

    private VendaResponse toResponse(Venda venda) {
        List<VendaResponse.ItemResponse> itens = venda.getItens().stream()
                .map(i -> VendaResponse.ItemResponse.builder()
                        .produtoId(i.getProduto().getId())
                        .produto(i.getProduto().getNome())
                        .quantidade(i.getQuantidade())
                        .precoUnitario(i.getPrecoUnitario())
                        .subtotal(i.getSubtotal())
                        .build())
                .toList();

        // Garante que criadoEm nunca seja null na resposta
        LocalDateTime criadoEm = venda.getCreatedAt() != null
                ? venda.getCreatedAt()
                : LocalDateTime.now();

        return VendaResponse.builder()
                .id(venda.getId())
                .vendedor(venda.getUsuario().getNome())
                .total(venda.getTotal())
                .observacao(venda.getObservacao())
                .criadoEm(criadoEm)
                .itens(itens)
                .build();
    }
}