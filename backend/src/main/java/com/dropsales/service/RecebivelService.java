package com.dropsales.service;

import com.dropsales.dto.RecebivelResponse;
import com.dropsales.dto.ResumoRecebiveisResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.*;
import com.dropsales.repository.RecebivelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class RecebivelService {

    private final TenantContextService tenantContext;
    private final RecebivelRepository recebivelRepository;

    @Transactional(readOnly = true)
    public ResumoRecebiveisResponse listar() {
        Loja loja = tenantContext.atual().loja();
        return ResumoRecebiveisResponse.builder()
                .pendente(recebivelRepository.somarLiquidoPorStatus(loja, StatusRecebivel.PENDENTE))
                .recebido(recebivelRepository.somarLiquidoPorStatus(loja, StatusRecebivel.RECEBIDO))
                .recebiveis(recebivelRepository.findByLojaOrderByDataPrevistaAscIdAsc(loja).stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    @Transactional
    public RecebivelResponse confirmarRecebimento(Long id) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirGerencia();
        Recebivel recebivel = recebivelRepository.findByIdAndLojaForUpdate(
                        id,
                        contexto.loja())
                .orElseThrow(() -> new ResourceNotFoundException("Recebivel nao encontrado"));
        if (recebivel.getStatus() == StatusRecebivel.CANCELADO) {
            throw new BusinessException("Um recebivel cancelado nao pode ser baixado");
        }
        if (recebivel.getStatus() == StatusRecebivel.RECEBIDO) {
            return toResponse(recebivel);
        }
        recebivel.setStatus(StatusRecebivel.RECEBIDO);
        recebivel.setRecebidoEm(OffsetDateTime.now(ZoneOffset.UTC));
        recebivel.setRecebidoPor(contexto.usuario());
        return toResponse(recebivelRepository.save(recebivel));
    }

    private RecebivelResponse toResponse(Recebivel item) {
        PagamentoVenda pagamento = item.getPagamentoVenda();
        return RecebivelResponse.builder()
                .id(item.getId())
                .vendaId(item.getVenda().getId())
                .formaPagamento(pagamento.getFormaPagamento())
                .adquirente(pagamento.getAdquirente() == null ? null : pagamento.getAdquirente().getNome())
                .numeroParcela(item.getNumeroParcela())
                .totalParcelas(item.getTotalParcelas())
                .valorBruto(item.getValorBruto())
                .taxaValor(item.getTaxaValor())
                .valorLiquido(item.getValorLiquido())
                .dataPrevista(item.getDataPrevista())
                .status(item.getStatus())
                .recebidoEm(item.getRecebidoEm())
                .build();
    }
}
