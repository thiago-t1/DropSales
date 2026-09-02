package com.dropsales.service;

import com.dropsales.dto.*;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.*;
import com.dropsales.repository.AdquirenteRepository;
import com.dropsales.repository.ConfiguracaoTaxaPagamentoRepository;
import com.dropsales.repository.LojaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfiguracaoPagamentoService {

    private final TenantContextService tenantContext;
    private final AdquirenteRepository adquirenteRepository;
    private final ConfiguracaoTaxaPagamentoRepository configuracaoRepository;
    private final LojaRepository lojaRepository;

    @Transactional
    public List<ConfiguracaoTaxaResponse> listarTaxas() {
        Loja loja = tenantContext.atual().loja();
        garantirPadroes(loja);
        return configuracaoRepository.findByLojaOrderByFormaPagamentoAscParcelasAsc(loja).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdquirenteResponse> listarAdquirentes() {
        Loja loja = tenantContext.atual().loja();
        return adquirenteRepository.findByLojaAndAtivoTrueOrderByNomeAsc(loja).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdquirenteResponse criarAdquirente(AdquirenteRequest request) {
        Loja loja = tenantContext.exigirAdministracao().loja();
        String nome = request.getNome().trim();
        if (adquirenteRepository.existsByLojaAndNomeIgnoreCase(loja, nome)) {
            throw new BusinessException("Ja existe uma adquirente com este nome");
        }
        Adquirente adquirente = adquirenteRepository.save(Adquirente.builder()
                .loja(loja).nome(nome).ativo(true).build());
        return toResponse(adquirente);
    }

    @Transactional
    public ConfiguracaoTaxaResponse salvarTaxa(Long id, ConfiguracaoTaxaRequest request) {
        Loja loja = tenantContext.exigirAdministracao().loja();
        validarForma(request);
        Adquirente adquirente = resolverAdquirente(request.getAdquirenteId(), loja);
        String bandeira = normalizarBandeira(request.getBandeira());
        validarDuplicidade(id, loja, request, adquirente, bandeira);

        ConfiguracaoTaxaPagamento config = id == null
                ? ConfiguracaoTaxaPagamento.builder().loja(loja).build()
                : configuracaoRepository.findByIdAndLoja(id, loja)
                    .orElseThrow(() -> new ResourceNotFoundException("Configuracao de taxa nao encontrada"));

        config.setFormaPagamento(request.getFormaPagamento());
        config.setAdquirente(adquirente);
        config.setBandeira(bandeira);
        config.setParcelas(request.getParcelas());
        config.setTaxaPercentual(request.getTaxaPercentual());
        config.setTaxaFixa(request.getTaxaFixa());
        config.setPrazoRecebimentoDias(request.getPrazoRecebimentoDias());
        config.setAtivo(request.getAtivo());
        return toResponse(configuracaoRepository.save(config));
    }

    @Transactional
    public void garantirPadroes(Loja loja) {
        if (configuracaoRepository.existsByLoja(loja)) return;
        Loja lojaBloqueada = lojaRepository.findByIdForUpdate(loja.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loja nao encontrada"));
        if (configuracaoRepository.existsByLoja(lojaBloqueada)) return;
        List<ConfiguracaoTaxaPagamento> defaults = new ArrayList<>();
        defaults.add(padrao(lojaBloqueada, FormaPagamento.DINHEIRO, 1, "0", 0));
        defaults.add(padrao(lojaBloqueada, FormaPagamento.PIX, 1, "0", 0));
        defaults.add(padrao(lojaBloqueada, FormaPagamento.CARTAO_DEBITO, 1, "1.50", 1));
        defaults.add(padrao(lojaBloqueada, FormaPagamento.CARTAO_CREDITO, 1, "3.50", 30));
        defaults.add(padrao(lojaBloqueada, FormaPagamento.CARTAO_CREDITO, 2, "4.00", 30));
        defaults.add(padrao(lojaBloqueada, FormaPagamento.CARTAO_CREDITO, 3, "4.50", 30));
        for (int parcelas = 4; parcelas <= 12; parcelas++) {
            defaults.add(padrao(lojaBloqueada, FormaPagamento.CARTAO_CREDITO, parcelas, "5.00", 30));
        }
        configuracaoRepository.saveAll(defaults);
    }

    @Transactional
    public ConfiguracaoTaxaPagamento resolverTaxa(
            Loja loja, FormaPagamento forma, Long adquirenteId, String bandeira, int parcelas) {
        if (forma == null || !forma.isFormaIndividual()) {
            throw new BusinessException("Forma de pagamento invalida");
        }
        garantirPadroes(loja);
        String bandeiraNormalizada = normalizarBandeira(bandeira);
        return configuracaoRepository.findByLojaAndAtivoTrue(loja).stream()
                .filter(item -> item.getFormaPagamento() == forma)
                .filter(item -> item.getParcelas() == parcelas)
                .filter(item -> item.getAdquirente() == null
                        || (adquirenteId != null && item.getAdquirente().getId().equals(adquirenteId)))
                .filter(item -> item.getBandeira() == null
                        || item.getBandeira().equalsIgnoreCase(bandeiraNormalizada == null ? "" : bandeiraNormalizada))
                .max(Comparator.comparingInt(item -> especificidade(item, adquirenteId, bandeiraNormalizada)))
                .orElseThrow(() -> new BusinessException(
                        "Nao ha taxa configurada para " + forma + " em " + parcelas + "x"));
    }

    public Adquirente resolverAdquirente(Long adquirenteId, Loja loja) {
        if (adquirenteId == null) return null;
        return adquirenteRepository.findByIdAndLoja(adquirenteId, loja)
                .filter(item -> Boolean.TRUE.equals(item.getAtivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Adquirente nao encontrada"));
    }

    private int especificidade(ConfiguracaoTaxaPagamento item, Long adquirenteId, String bandeira) {
        int score = 0;
        if (item.getAdquirente() != null && adquirenteId != null) score += 2;
        if (item.getBandeira() != null && bandeira != null) score += 1;
        return score;
    }

    private ConfiguracaoTaxaPagamento padrao(
            Loja loja, FormaPagamento forma, int parcelas, String percentual, int prazo) {
        return ConfiguracaoTaxaPagamento.builder()
                .loja(loja)
                .formaPagamento(forma)
                .parcelas(parcelas)
                .taxaPercentual(new BigDecimal(percentual))
                .taxaFixa(BigDecimal.ZERO)
                .prazoRecebimentoDias(prazo)
                .ativo(true)
                .build();
    }

    private void validarForma(ConfiguracaoTaxaRequest request) {
        if (!request.getFormaPagamento().isCartao()) {
            if (request.getParcelas() != 1) {
                throw new BusinessException("Dinheiro e PIX devem usar uma parcela");
            }
            request.setAdquirenteId(null);
            request.setBandeira(null);
        }
    }

    private void validarDuplicidade(Long id, Loja loja, ConfiguracaoTaxaRequest request,
                                    Adquirente adquirente, String bandeira) {
        boolean duplicado = configuracaoRepository.findByLojaOrderByFormaPagamentoAscParcelasAsc(loja).stream()
                .filter(item -> id == null || !item.getId().equals(id))
                .anyMatch(item -> item.getFormaPagamento() == request.getFormaPagamento()
                        && item.getParcelas().equals(request.getParcelas())
                        && idsIguais(item.getAdquirente(), adquirente)
                        && textosIguais(item.getBandeira(), bandeira));
        if (duplicado) {
            throw new BusinessException("Ja existe uma regra para esta combinacao de pagamento");
        }
    }

    private boolean idsIguais(Adquirente a, Adquirente b) {
        return a == null ? b == null : b != null && a.getId().equals(b.getId());
    }

    private boolean textosIguais(String a, String b) {
        return a == null ? b == null : b != null && a.equalsIgnoreCase(b);
    }

    private String normalizarBandeira(String bandeira) {
        return bandeira == null || bandeira.isBlank() ? null : bandeira.trim().toUpperCase();
    }

    private ConfiguracaoTaxaResponse toResponse(ConfiguracaoTaxaPagamento item) {
        return ConfiguracaoTaxaResponse.builder()
                .id(item.getId())
                .formaPagamento(item.getFormaPagamento())
                .adquirenteId(item.getAdquirente() == null ? null : item.getAdquirente().getId())
                .adquirenteNome(item.getAdquirente() == null ? null : item.getAdquirente().getNome())
                .bandeira(item.getBandeira())
                .parcelas(item.getParcelas())
                .taxaPercentual(item.getTaxaPercentual())
                .taxaFixa(item.getTaxaFixa())
                .prazoRecebimentoDias(item.getPrazoRecebimentoDias())
                .ativo(Boolean.TRUE.equals(item.getAtivo()))
                .build();
    }

    private AdquirenteResponse toResponse(Adquirente item) {
        return AdquirenteResponse.builder()
                .id(item.getId()).nome(item.getNome()).ativo(Boolean.TRUE.equals(item.getAtivo())).build();
    }
}
