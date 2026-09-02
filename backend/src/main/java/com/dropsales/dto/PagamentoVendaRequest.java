package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PagamentoVendaRequest {
    @NotNull
    private FormaPagamento formaPagamento;

    @NotNull
    @DecimalMin(value = "0.01", message = "Valor do pagamento deve ser maior que zero")
    @Digits(integer = 10, fraction = 2,
            message = "Valor do pagamento deve ter no maximo dez inteiros e duas casas decimais")
    private BigDecimal valor;

    @Positive(message = "Adquirente deve ser um identificador positivo")
    private Long adquirenteId;

    @Size(max = 40, message = "Bandeira deve ter no maximo 40 caracteres")
    private String bandeira;

    @Min(value = 1, message = "Parcelas deve ser no minimo 1")
    @Max(value = 18, message = "Parcelas deve ser no maximo 18")
    private Integer parcelas = 1;

    @DecimalMin(value = "0.00", message = "Valor recebido nao pode ser negativo")
    @Digits(integer = 10, fraction = 2,
            message = "Valor recebido deve ter no maximo dez inteiros e duas casas decimais")
    private BigDecimal valorRecebido;
}
