package com.dropsales.model;

/**
 * Ciclo de vida do snapshot de pagamento.
 * Pagamentos substituidos permanecem persistidos para auditoria.
 */
public enum StatusPagamentoVenda {
    ATIVO,
    SUBSTITUIDO,
    CANCELADO
}
