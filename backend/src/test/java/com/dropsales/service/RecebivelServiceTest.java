package com.dropsales.service;

import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.Empresa;
import com.dropsales.model.FormaPagamento;
import com.dropsales.model.Loja;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.PagamentoVenda;
import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.Recebivel;
import com.dropsales.model.StatusRecebivel;
import com.dropsales.model.Usuario;
import com.dropsales.model.Venda;
import com.dropsales.repository.RecebivelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecebivelServiceTest {

    @Mock private TenantContextService tenantContext;
    @Mock private RecebivelRepository recebivelRepository;

    private RecebivelService service;
    private Usuario gerente;
    private Loja loja;
    private TenantContextService.ContextoAtual contexto;

    @BeforeEach
    void setUp() {
        service = new RecebivelService(tenantContext, recebivelRepository);
        gerente = Usuario.builder()
                .id(1L)
                .nome("Gerente")
                .email("gerente@teste.com")
                .build();
        Empresa empresa = Empresa.builder().id(2L).nome("Empresa").ativo(true).build();
        loja = Loja.builder()
                .id(3L)
                .empresa(empresa)
                .nome("Loja")
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build();
        MembroEmpresa membro = MembroEmpresa.builder()
                .id(4L)
                .empresa(empresa)
                .usuario(gerente)
                .papel(PapelEmpresa.GERENTE)
                .ativo(true)
                .build();
        contexto = new TenantContextService.ContextoAtual(
                gerente,
                empresa,
                loja,
                membro);
    }

    @Test
    void recebivelDeOutraLojaNaoPodeSerBaixado() {
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(recebivelRepository.findByIdAndLojaForUpdate(99L, loja))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.confirmarRecebimento(99L));
    }

    @Test
    void baixaEhIdempotenteRegistraResponsavelEUsaLockDaLoja() {
        Recebivel recebivel = recebivelPendente();
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(recebivelRepository.findByIdAndLojaForUpdate(recebivel.getId(), loja))
                .thenReturn(Optional.of(recebivel));
        when(recebivelRepository.save(recebivel)).thenReturn(recebivel);

        service.confirmarRecebimento(recebivel.getId());
        service.confirmarRecebimento(recebivel.getId());

        assertEquals(StatusRecebivel.RECEBIDO, recebivel.getStatus());
        assertEquals(gerente, recebivel.getRecebidoPor());
        assertEquals(ZoneOffset.UTC, recebivel.getRecebidoEm().getOffset());
        verify(recebivelRepository, times(2))
                .findByIdAndLojaForUpdate(recebivel.getId(), loja);
        verify(recebivelRepository, times(1)).save(recebivel);
    }

    private Recebivel recebivelPendente() {
        Venda venda = Venda.builder()
                .id(10L)
                .loja(loja)
                .usuario(gerente)
                .total(new BigDecimal("100.00"))
                .build();
        PagamentoVenda pagamento = PagamentoVenda.builder()
                .id(11L)
                .loja(loja)
                .venda(venda)
                .formaPagamento(FormaPagamento.CARTAO_CREDITO)
                .parcelas(1)
                .valorBruto(new BigDecimal("100.00"))
                .taxaValor(new BigDecimal("3.00"))
                .valorLiquido(new BigDecimal("97.00"))
                .build();
        return Recebivel.builder()
                .id(12L)
                .loja(loja)
                .venda(venda)
                .pagamentoVenda(pagamento)
                .numeroParcela(1)
                .totalParcelas(1)
                .valorBruto(new BigDecimal("100.00"))
                .taxaValor(new BigDecimal("3.00"))
                .valorLiquido(new BigDecimal("97.00"))
                .dataPrevista(LocalDate.now())
                .status(StatusRecebivel.PENDENTE)
                .build();
    }
}
