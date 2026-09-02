package com.dropsales.model;

public enum FormaPagamento {
    DINHEIRO,
    PIX,
    CARTAO_DEBITO,
    CARTAO_CREDITO,

    /**
     * Resumo legado de uma venda que possui mais de uma forma de pagamento.
     * Nao e aceito como forma de um PagamentoVenda individual.
     */
    MISTO;

    public boolean isCartao() {
        return this == CARTAO_DEBITO || this == CARTAO_CREDITO;
    }

    public boolean isFormaIndividual() {
        return this != MISTO;
    }
}
