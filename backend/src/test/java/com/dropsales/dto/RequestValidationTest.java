package com.dropsales.dto;

import com.dropsales.model.FormaPagamento;
import com.dropsales.model.PapelEmpresa;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void fecharValidator() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void produtoAceitaLimitesDasColunas() {
        ProdutoRequest request = produtoValido();
        request.setNome("N".repeat(200));
        request.setDescricao("D".repeat(500));
        request.setSku("S".repeat(50));
        request.setPrecoCusto(new BigDecimal("9999999999.99"));
        request.setPrecoVenda(new BigDecimal("9999999999.99"));
        request.setCategoriaId(1L);

        assertTrue(validar(request).isEmpty());
    }

    @Test
    void produtoRejeitaTextosValoresEIdsForaDosLimites() {
        ProdutoRequest request = produtoValido();
        request.setNome("N".repeat(201));
        request.setDescricao("D".repeat(501));
        request.setSku("S".repeat(51));
        request.setPrecoCusto(new BigDecimal("10000000000.00"));
        request.setPrecoVenda(new BigDecimal("1.001"));
        request.setCategoriaId(0L);

        Set<ConstraintViolation<ProdutoRequest>> violations = validar(request);

        assertCamposInvalidos(violations,
                "nome", "descricao", "sku", "precoCusto", "precoVenda", "categoriaId");
    }

    @Test
    void produtoRejeitaEstoqueMinimoNuloAntesDePersistir() {
        ProdutoRequest request = produtoValido();
        request.setEstoqueMinimo(null);

        assertCamposInvalidos(validar(request), "estoqueMinimo");
    }

    @Test
    void configuracaoDeTaxaAceitaPrecisaoEExtremosDoBanco() {
        ConfiguracaoTaxaRequest request = configuracaoValida();
        request.setAdquirenteId(1L);
        request.setBandeira("B".repeat(40));
        request.setParcelas(18);
        request.setTaxaPercentual(new BigDecimal("100.0000"));
        request.setTaxaFixa(new BigDecimal("9999999999.99"));
        request.setPrazoRecebimentoDias(365);

        assertTrue(validar(request).isEmpty());
    }

    @Test
    void configuracaoDeTaxaRejeitaPrecisaoParcelasPrazoEIdInvalidos() {
        ConfiguracaoTaxaRequest request = configuracaoValida();
        request.setAdquirenteId(0L);
        request.setBandeira("B".repeat(41));
        request.setParcelas(19);
        request.setTaxaPercentual(new BigDecimal("10.00001"));
        request.setTaxaFixa(new BigDecimal("0.001"));
        request.setPrazoRecebimentoDias(366);

        Set<ConstraintViolation<ConfiguracaoTaxaRequest>> violations = validar(request);

        assertCamposInvalidos(violations,
                "adquirenteId", "bandeira", "parcelas", "taxaPercentual",
                "taxaFixa", "prazoRecebimentoDias");
    }

    @Test
    void pagamentoAceitaValoresMonetariosCompativeisComDecimalDozeDois() {
        PagamentoVendaRequest request = pagamentoValido();
        request.setAdquirenteId(1L);
        request.setBandeira("B".repeat(40));
        request.setParcelas(18);
        request.setValor(new BigDecimal("9999999999.99"));
        request.setValorRecebido(new BigDecimal("9999999999.99"));

        assertTrue(validar(request).isEmpty());
    }

    @Test
    void pagamentoRejeitaPrecisaoParcelasEIdInvalidos() {
        PagamentoVendaRequest request = pagamentoValido();
        request.setAdquirenteId(-1L);
        request.setBandeira("B".repeat(41));
        request.setParcelas(0);
        request.setValor(new BigDecimal("1.001"));
        request.setValorRecebido(new BigDecimal("-0.01"));

        Set<ConstraintViolation<PagamentoVendaRequest>> violations = validar(request);

        assertCamposInvalidos(violations,
                "adquirenteId", "bandeira", "parcelas", "valor", "valorRecebido");
    }

    @Test
    void vendaValidaLimitesDeObservacaoColecaoEObjetosAninhados() {
        VendaRequest request = vendaValida();
        request.setObservacao("O".repeat(501));
        request.setItens(Collections.nCopies(501, itemValido()));
        PagamentoVendaRequest pagamentoInvalido = pagamentoValido();
        pagamentoInvalido.setValor(new BigDecimal("2.999"));
        request.setPagamentos(List.of(pagamentoInvalido));

        Set<ConstraintViolation<VendaRequest>> violations = validar(request);

        assertCamposInvalidos(violations,
                "observacao", "itens", "pagamentos[0].valor");
    }

    @Test
    void vendaAceitaContratoLegadoDentroDosLimites() {
        VendaRequest request = vendaValida();
        request.setObservacao("O".repeat(500));
        request.setPagamentos(null);
        request.setFormaPagamento(FormaPagamento.PIX);
        request.setTaxaPagamentoPercentual(new BigDecimal("100.00"));

        assertTrue(validar(request).isEmpty());
    }

    @Test
    void dadosCadastraisRespeitamOsLimitesDasColunas() {
        RegisterRequest cadastro = new RegisterRequest();
        cadastro.setNome("N".repeat(151));
        cadastro.setNomeEmpresa("E".repeat(161));
        cadastro.setEmail("a".repeat(192) + "@email.com");
        cadastro.setSenha("S".repeat(73));

        EmpresaRequest empresa = new EmpresaRequest();
        empresa.setNome("E".repeat(161));
        empresa.setDocumento("D".repeat(21));
        empresa.setNomeLoja("L".repeat(121));

        LojaRequest loja = new LojaRequest();
        loja.setNome("L".repeat(121));
        loja.setTimezone("T".repeat(61));

        assertCamposInvalidos(validar(cadastro), "nome", "nomeEmpresa", "email", "senha");
        assertCamposInvalidos(validar(empresa), "nome", "documento", "nomeLoja");
        assertCamposInvalidos(validar(loja), "nome", "timezone");
    }

    @Test
    void cadastroETrocaDeSenhaExigemDozeCaracteres() {
        RegisterRequest cadastro = new RegisterRequest();
        cadastro.setNome("Pessoa Teste");
        cadastro.setNomeEmpresa("Empresa Teste");
        cadastro.setEmail("pessoa@teste.com");
        cadastro.setSenha("S".repeat(11));

        AlterarSenhaRequest alteracao = new AlterarSenhaRequest();
        alteracao.setSenhaAtual("senha-atual");
        alteracao.setNovaSenha("N".repeat(11));
        alteracao.setConfirmarSenha("N".repeat(11));

        assertCamposInvalidos(validar(cadastro), "senha");
        assertCamposInvalidos(
                validar(alteracao),
                "novaSenha",
                "confirmarSenha");

        cadastro.setSenha("S".repeat(12));
        alteracao.setNovaSenha("N".repeat(12));
        alteracao.setConfirmarSenha("N".repeat(12));

        assertTrue(validar(cadastro).isEmpty());
        assertTrue(validar(alteracao).isEmpty());
    }

    @Test
    void conviteLoginEPerfilRejeitamCamposAcimaDasColunas() {
        ConviteEmpresaRequest convite = new ConviteEmpresaRequest();
        convite.setEmail("a".repeat(192) + "@email.com");
        convite.setPapel(PapelEmpresa.OPERADOR);

        LoginRequest login = new LoginRequest();
        login.setEmail("a".repeat(192) + "@email.com");
        login.setSenha("S".repeat(73));

        UsuarioUpdateRequest usuario = new UsuarioUpdateRequest();
        usuario.setNome("N".repeat(151));
        usuario.setEmail("a".repeat(192) + "@email.com");

        assertCamposInvalidos(validar(convite), "email");
        assertCamposInvalidos(validar(login), "email", "senha");
        assertCamposInvalidos(validar(usuario), "nome", "email");
    }

    @Test
    void conviteEItemDeVendaExigemIdentificadoresValidos() {
        AceitarConviteRequest convite = new AceitarConviteRequest();
        convite.setToken("curto");

        ItemVendaRequest item = itemValido();
        item.setProdutoId(0L);

        assertCamposInvalidos(validar(convite), "token");
        assertCamposInvalidos(validar(item), "produtoId");
    }

    private ProdutoRequest produtoValido() {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Produto");
        request.setPrecoCusto(new BigDecimal("10.00"));
        request.setPrecoVenda(new BigDecimal("20.00"));
        request.setQuantidadeEstoque(10);
        request.setEstoqueMinimo(2);
        return request;
    }

    private ConfiguracaoTaxaRequest configuracaoValida() {
        ConfiguracaoTaxaRequest request = new ConfiguracaoTaxaRequest();
        request.setFormaPagamento(FormaPagamento.CARTAO_CREDITO);
        request.setParcelas(1);
        request.setTaxaPercentual(new BigDecimal("1.5000"));
        request.setTaxaFixa(new BigDecimal("0.50"));
        request.setPrazoRecebimentoDias(30);
        request.setAtivo(true);
        return request;
    }

    private PagamentoVendaRequest pagamentoValido() {
        PagamentoVendaRequest request = new PagamentoVendaRequest();
        request.setFormaPagamento(FormaPagamento.CARTAO_CREDITO);
        request.setValor(new BigDecimal("20.00"));
        return request;
    }

    private VendaRequest vendaValida() {
        VendaRequest request = new VendaRequest();
        request.setItens(List.of(itemValido()));
        request.setPagamentos(List.of(pagamentoValido()));
        return request;
    }

    private ItemVendaRequest itemValido() {
        ItemVendaRequest item = new ItemVendaRequest();
        item.setProdutoId(1L);
        item.setQuantidade(1);
        return item;
    }

    private <T> Set<ConstraintViolation<T>> validar(T value) {
        return VALIDATOR.validate(value);
    }

    private void assertCamposInvalidos(
            Set<? extends ConstraintViolation<?>> violations,
            String... campos) {
        Set<String> paths = violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
        for (String campo : campos) {
            assertTrue(paths.contains(campo),
                    () -> "Esperava violacao em " + campo + ", mas recebeu " + paths);
        }
        assertEquals(campos.length, paths.size(),
                () -> "Campos inesperados na validacao: " + paths);
    }
}
