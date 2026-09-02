package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VendaRequest {
    @Size(max = 500, message = "Observacao deve ter no maximo 500 caracteres")
    private String observacao;

    /** Contrato legado. Usado apenas quando pagamentos nao for informado. */
    private FormaPagamento formaPagamento;

    /** Contrato legado. As taxas configuradas da loja sao autoritativas. */
    @DecimalMin(value = "0.00", message = "A taxa de pagamento nao pode ser negativa")
    @DecimalMax(value = "100.00", message = "A taxa de pagamento nao pode exceder 100%")
    @Digits(integer = 3, fraction = 2, message = "A taxa deve ter no maximo duas casas decimais")
    private BigDecimal taxaPagamentoPercentual;

    @Valid
    @Size(max = 5, message = "Uma venda pode ter no maximo cinco pagamentos divididos")
    private List<PagamentoVendaRequest> pagamentos;

    @NotEmpty(message = "A venda deve ter pelo menos um item")
    @Size(max = 500, message = "A venda deve ter no maximo 500 itens")
    @Valid
    private List<ItemVendaRequest> itens;
}
