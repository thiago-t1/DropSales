package com.dropsales.util;

import com.dropsales.dto.ItemVendaRequest;
import com.dropsales.dto.PagamentoVendaRequest;
import com.dropsales.dto.VendaRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class VendaRequestFingerprint {

    private VendaRequestFingerprint() {
    }

    public static String calcular(VendaRequest request) {
        StringBuilder canonico = new StringBuilder("dropsales-venda-v1|");
        campo(canonico, request.getObservacao());
        campo(canonico, request.getFormaPagamento());
        campo(canonico, request.getTaxaPagamentoPercentual());

        if (request.getItens() == null) {
            campo(canonico, null);
        } else {
            campo(canonico, request.getItens().size());
            for (ItemVendaRequest item : request.getItens()) {
                if (item == null) {
                    campo(canonico, null);
                    continue;
                }
                campo(canonico, item.getProdutoId());
                campo(canonico, item.getQuantidade());
            }
        }

        if (request.getPagamentos() == null) {
            campo(canonico, null);
        } else {
            campo(canonico, request.getPagamentos().size());
            for (PagamentoVendaRequest pagamento : request.getPagamentos()) {
                if (pagamento == null) {
                    campo(canonico, null);
                    continue;
                }
                campo(canonico, pagamento.getFormaPagamento());
                campo(canonico, pagamento.getValor());
                campo(canonico, pagamento.getAdquirenteId());
                campo(canonico, pagamento.getBandeira());
                campo(canonico, pagamento.getParcelas());
                campo(canonico, pagamento.getValorRecebido());
            }
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonico.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }

    private static void campo(StringBuilder destino, Object valor) {
        if (valor == null) {
            destino.append("-1:");
            return;
        }
        String texto = valor.toString();
        destino.append(texto.length()).append(':').append(texto);
    }
}
