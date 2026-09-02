package com.dropsales.service;

import com.dropsales.dto.PagamentoVendaRequest;
import com.dropsales.dto.PagamentoVendaResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.model.*;
import com.dropsales.repository.PagamentoVendaRepository;
import com.dropsales.repository.RecebivelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PagamentoVendaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final ConfiguracaoPagamentoService configuracaoService;
    private final PagamentoVendaRepository pagamentoRepository;
    private final RecebivelRepository recebivelRepository;

    @Transactional
    public List<PagamentoVenda> processar(
            Venda venda, Loja loja, List<PagamentoVendaRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("Informe ao menos uma forma de pagamento");
        }
        if (requests.size() > 5) {
            throw new BusinessException("Uma venda pode ter no maximo cinco pagamentos divididos");
        }

        validarLojaDaVenda(venda, loja);
        BigDecimal soma = BigDecimal.ZERO;
        for (PagamentoVendaRequest request : requests) {
            if (request == null) {
                throw new BusinessException("Pagamento invalido");
            }
            soma = soma.add(moeda(request.getValor()));
        }
        if (soma.compareTo(moeda(venda.getTotal())) != 0) {
            throw new BusinessException("A soma dos pagamentos deve ser igual ao total da venda");
        }

        List<PagamentoVenda> pagamentos = new ArrayList<>();
        for (PagamentoVendaRequest request : requests) {
            pagamentos.add(criarPagamento(venda, loja, request));
        }
        return pagamentos;
    }

    @Transactional
    public List<PagamentoVenda> substituir(
            Venda venda,
            Loja loja,
            List<PagamentoVendaRequest> requests) {
        validarLojaDaVenda(venda, loja);
        OffsetDateTime agoraUtc = OffsetDateTime.now(ZoneOffset.UTC);
        List<PagamentoVenda> ativos = pagamentoRepository.findByVendaAndStatusOrderByIdAsc(
                venda,
                StatusPagamentoVenda.ATIVO);
        for (PagamentoVenda pagamento : ativos) {
            pagamento.setStatus(StatusPagamentoVenda.SUBSTITUIDO);
            pagamento.setSubstituidoEm(agoraUtc);
            cancelarRecebiveis(pagamento);
        }
        pagamentoRepository.saveAll(ativos);
        return processar(venda, loja, requests);
    }

    @Transactional
    public void cancelar(Venda venda) {
        OffsetDateTime agoraUtc = OffsetDateTime.now(ZoneOffset.UTC);
        List<PagamentoVenda> ativos = pagamentoRepository.findByVendaAndStatusOrderByIdAsc(
                venda,
                StatusPagamentoVenda.ATIVO);
        for (PagamentoVenda pagamento : ativos) {
            pagamento.setStatus(StatusPagamentoVenda.CANCELADO);
            pagamento.setCanceladoEm(agoraUtc);
            cancelarRecebiveis(pagamento);
        }
        pagamentoRepository.saveAll(ativos);
    }

    @Transactional(readOnly = true)
    public List<PagamentoVendaResponse> listar(Venda venda) {
        return pagamentoRepository.findByVendaAndStatusNotOrderByIdAsc(
                        venda,
                        StatusPagamentoVenda.SUBSTITUIDO).stream()
                .map(this::toResponse)
                .toList();
    }

    public PagamentoVendaResponse toResponse(PagamentoVenda item) {
        return PagamentoVendaResponse.builder()
                .id(item.getId())
                .formaPagamento(item.getFormaPagamento())
                .adquirenteId(item.getAdquirente() == null ? null : item.getAdquirente().getId())
                .adquirenteNome(item.getAdquirente() == null ? null : item.getAdquirente().getNome())
                .bandeira(item.getBandeira())
                .parcelas(item.getParcelas())
                .valorBruto(item.getValorBruto())
                .taxaPercentual(item.getTaxaPercentual())
                .taxaFixa(item.getTaxaFixa())
                .taxaValor(item.getTaxaValor())
                .valorLiquido(item.getValorLiquido())
                .valorRecebido(item.getValorRecebido())
                .troco(item.getTroco())
                .prazoRecebimentoDias(item.getPrazoRecebimentoDias())
                .status(item.getStatus())
                .build();
    }

    private PagamentoVenda criarPagamento(Venda venda, Loja loja, PagamentoVendaRequest request) {
        FormaPagamento forma = request.getFormaPagamento();
        if (forma == null || !forma.isFormaIndividual()) {
            throw new BusinessException("Forma de pagamento invalida");
        }
        int parcelas = request.getParcelas() == null ? 1 : request.getParcelas();
        if (parcelas < 1 || parcelas > 18) {
            throw new BusinessException("O numero de parcelas deve estar entre 1 e 18");
        }
        if (forma != FormaPagamento.CARTAO_CREDITO && parcelas != 1) {
            throw new BusinessException("Somente cartao de credito pode ser parcelado");
        }

        Adquirente adquirente = configuracaoService.resolverAdquirente(request.getAdquirenteId(), loja);
        boolean temBandeira = request.getBandeira() != null
                && !request.getBandeira().isBlank();
        if (!forma.isCartao() && (adquirente != null || temBandeira)) {
            throw new BusinessException(
                    "Adquirente e bandeira so podem ser informadas para cartao");
        }
        ConfiguracaoTaxaPagamento config = configuracaoService.resolverTaxa(
                loja, forma, request.getAdquirenteId(), request.getBandeira(), parcelas);

        BigDecimal bruto = moeda(request.getValor());
        BigDecimal taxaPercentual = config.getTaxaPercentual();
        BigDecimal taxaFixa = moeda(config.getTaxaFixa());
        BigDecimal taxaValor = moeda(bruto.multiply(taxaPercentual).divide(CEM, 6, RoundingMode.HALF_UP)
                .add(taxaFixa));
        if (taxaValor.compareTo(bruto) > 0) {
            throw new BusinessException("A taxa configurada nao pode superar o valor do pagamento");
        }

        BigDecimal valorRecebido = null;
        BigDecimal troco = null;
        if (forma == FormaPagamento.DINHEIRO) {
            valorRecebido = request.getValorRecebido() == null ? bruto : moeda(request.getValorRecebido());
            if (valorRecebido.compareTo(bruto) < 0) {
                throw new BusinessException("O valor recebido em dinheiro e menor que o pagamento");
            }
            troco = moeda(valorRecebido.subtract(bruto));
        } else if (request.getValorRecebido() != null) {
            throw new BusinessException("Valor recebido e troco se aplicam apenas a dinheiro");
        }

        PagamentoVenda pagamento = pagamentoRepository.save(PagamentoVenda.builder()
                .venda(venda)
                .loja(loja)
                .formaPagamento(forma)
                .adquirente(adquirente)
                .bandeira(request.getBandeira() == null || request.getBandeira().isBlank()
                        ? null : request.getBandeira().trim().toUpperCase())
                .parcelas(parcelas)
                .valorBruto(bruto)
                .taxaPercentual(taxaPercentual)
                .taxaFixa(taxaFixa)
                .taxaValor(taxaValor)
                .valorLiquido(moeda(bruto.subtract(taxaValor)))
                .valorRecebido(valorRecebido)
                .troco(troco)
                .prazoRecebimentoDias(config.getPrazoRecebimentoDias())
                .status(StatusPagamentoVenda.ATIVO)
                .build());

        criarRecebiveis(pagamento, loja);
        return pagamento;
    }

    private void criarRecebiveis(PagamentoVenda pagamento, Loja loja) {
        ZoneId zone;
        try {
            zone = ZoneId.of(loja.getTimezone());
        } catch (DateTimeException ex) {
            zone = ZoneId.of("America/Sao_Paulo");
        }
        LocalDate hoje = LocalDate.now(zone);
        boolean imediato = pagamento.getPrazoRecebimentoDias() == 0
                && (pagamento.getFormaPagamento() == FormaPagamento.DINHEIRO
                || pagamento.getFormaPagamento() == FormaPagamento.PIX);
        List<Recebivel> itens = new ArrayList<>();
        for (int numero = 1; numero <= pagamento.getParcelas(); numero++) {
            BigDecimal bruto = ratear(pagamento.getValorBruto(), pagamento.getParcelas(), numero);
            BigDecimal taxa = ratear(pagamento.getTaxaValor(), pagamento.getParcelas(), numero);
            BigDecimal liquido = moeda(bruto.subtract(taxa));
            itens.add(Recebivel.builder()
                    .loja(loja)
                    .venda(pagamento.getVenda())
                    .pagamentoVenda(pagamento)
                    .numeroParcela(numero)
                    .totalParcelas(pagamento.getParcelas())
                    .valorBruto(bruto)
                    .taxaValor(taxa)
                    .valorLiquido(liquido)
                    .dataPrevista(hoje.plusDays(pagamento.getPrazoRecebimentoDias())
                            .plusDays((long) (numero - 1) * 30))
                    .status(imediato ? StatusRecebivel.RECEBIDO : StatusRecebivel.PENDENTE)
                    .recebidoEm(imediato ? OffsetDateTime.now(ZoneOffset.UTC) : null)
                    .build());
        }
        recebivelRepository.saveAll(itens);
    }

    private BigDecimal ratear(BigDecimal total, int parcelas, int numero) {
        BigDecimal base = total.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
        if (numero < parcelas) return base;
        return moeda(total.subtract(base.multiply(BigDecimal.valueOf(parcelas - 1L))));
    }

    private BigDecimal moeda(BigDecimal valor) {
        if (valor == null) {
            throw new BusinessException("O valor do pagamento e obrigatorio");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private void cancelarRecebiveis(PagamentoVenda pagamento) {
        List<Recebivel> recebiveis = recebivelRepository.findByPagamentoVenda(pagamento);
        recebiveis.stream()
                .filter(item -> item.getStatus() != StatusRecebivel.CANCELADO)
                .forEach(item -> item.setStatus(StatusRecebivel.CANCELADO));
        recebivelRepository.saveAll(recebiveis);
    }

    private void validarLojaDaVenda(Venda venda, Loja loja) {
        if (venda == null || loja == null || venda.getLoja() == null
                || (!Objects.equals(loja.getId(), venda.getLoja().getId())
                && loja != venda.getLoja())) {
            throw new BusinessException("Venda e pagamentos devem pertencer a mesma loja");
        }
    }
}
