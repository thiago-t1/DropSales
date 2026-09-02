package com.dropsales.service;

import com.dropsales.dto.ImportResultDTO;
import com.dropsales.dto.ProdutoRequest;
import com.dropsales.dto.ProdutoResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.Categoria;
import com.dropsales.model.Empresa;
import com.dropsales.model.Loja;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.Produto;
import com.dropsales.model.Usuario;
import com.dropsales.repository.CategoriaRepository;
import com.dropsales.repository.ProdutoRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private TenantContextService tenantContext;

    private ProdutoService produtoService;
    private Usuario usuario;
    private Loja loja;
    private TenantContextService.ContextoAtual contexto;
    private Categoria categoria;

    @BeforeEach
    void setUp() {
        produtoService = new ProdutoService(
                produtoRepository,
                categoriaRepository,
                tenantContext);
        usuario = Usuario.builder()
                .id(1L)
                .nome("Operador")
                .email("operador@teste.com")
                .build();
        Empresa empresa = Empresa.builder().id(2L).nome("Empresa").ativo(true).build();
        loja = Loja.builder()
                .id(3L)
                .empresa(empresa)
                .nome("Loja A")
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build();
        MembroEmpresa membro = MembroEmpresa.builder()
                .id(4L)
                .empresa(empresa)
                .usuario(usuario)
                .papel(PapelEmpresa.OPERADOR)
                .ativo(true)
                .build();
        contexto = new TenantContextService.ContextoAtual(
                usuario,
                empresa,
                loja,
                membro);
        categoria = Categoria.builder()
                .id(7L)
                .nome("Vestuario")
                .usuario(usuario)
                .loja(loja)
                .build();
    }

    @Test
    void atualizarMantemContratoERestringeCategoriaALojaAtual() {
        Produto produto = produto(42L);
        ProdutoRequest request = request();
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(produtoRepository.findByIdAndLojaForUpdate(produto.getId(), loja))
                .thenReturn(Optional.of(produto));
        when(categoriaRepository.findByIdAndLoja(categoria.getId(), loja))
                .thenReturn(Optional.of(categoria));
        when(produtoRepository.save(any(Produto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProdutoResponse response = produtoService.atualizar(produto.getId(), request);

        assertEquals("Algodao, tamanho M", produto.getDescricao());
        assertSame(categoria, produto.getCategoria());
        assertEquals("Algodao, tamanho M", response.getDescricao());
        assertEquals(categoria.getId(), response.getCategoriaId());
        verify(categoriaRepository).findByIdAndLoja(categoria.getId(), loja);
    }

    @Test
    void produtoDeOutraLojaNaoPodeSerAtualizadoMesmoPeloMesmoUsuario() {
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(produtoRepository.findByIdAndLojaForUpdate(99L, loja))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> produtoService.atualizar(99L, request()));

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void importarValidaTodasAsLinhasAntesDePersistirEMantemTenant()
            throws IOException {
        MockMultipartFile arquivo = planilha(
                new Object[]{null, "SEM-NOME", null, null, 10, 20, 2, 1},
                new Object[]{"Camiseta", "CAM-002", "Algodao", "Vestuario",
                        25, 59.90, 10, 3});
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(categoriaRepository.findByNomeIgnoreCaseAndLoja(
                "Vestuario", loja)).thenReturn(Optional.empty());
        when(categoriaRepository.save(any(Categoria.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(produtoRepository.findBySkuIgnoreCaseAndLojaForUpdate("CAM-002", loja))
                .thenReturn(Optional.empty());
        when(produtoRepository.save(any(Produto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ImportResultDTO resultado = produtoService.importarPlanilha(arquivo);

        assertEquals(1, resultado.getImportados());
        assertEquals(0, resultado.getAtualizados());
        assertEquals(1, resultado.getIgnorados());
        assertEquals("Linha 2: Nome vazio", resultado.getErros().get(0));

        ArgumentCaptor<Categoria> categoriaSalva =
                ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaRepository).save(categoriaSalva.capture());
        assertSame(loja, categoriaSalva.getValue().getLoja());
        assertSame(usuario, categoriaSalva.getValue().getUsuario());

        ArgumentCaptor<Produto> produtoSalvo =
                ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository).save(produtoSalvo.capture());
        assertSame(loja, produtoSalvo.getValue().getLoja());
        assertSame(usuario, produtoSalvo.getValue().getUsuario());
        assertSame(categoriaSalva.getValue(), produtoSalvo.getValue().getCategoria());
    }

    @Test
    void importarRejeitaRestricaoConhecidaSemTocarNosRepositorios()
            throws IOException {
        MockMultipartFile arquivo = planilha(
                new Object[]{"N".repeat(201), "SKU-001", null, null,
                        10, 20, 2, 1});
        when(tenantContext.exigirGerencia()).thenReturn(contexto);

        ImportResultDTO resultado = produtoService.importarPlanilha(arquivo);

        assertEquals(0, resultado.getImportados());
        assertEquals(0, resultado.getAtualizados());
        assertEquals(1, resultado.getIgnorados());
        assertEquals(
                "Linha 2: Nome excede 200 caracteres",
                resultado.getErros().get(0));
        verifyNoInteractions(produtoRepository, categoriaRepository);
    }

    @Test
    void importarAceitaFormatoXlsLegado() throws IOException {
        MockMultipartFile arquivo = planilhaLegada(
                new Object[]{"Camiseta", "CAM-XLS", null, null,
                        10, 20, 2, 1});
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(produtoRepository.findBySkuIgnoreCaseAndLojaForUpdate("CAM-XLS", loja))
                .thenReturn(Optional.empty());
        when(produtoRepository.save(any(Produto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ImportResultDTO resultado = produtoService.importarPlanilha(arquivo);

        assertEquals(1, resultado.getImportados());
        assertEquals(0, resultado.getIgnorados());
        verify(produtoRepository).save(any(Produto.class));
    }

    @Test
    void importarRejeitaArquivoQueNaoEhExcelComoErroDeNegocio() {
        MockMultipartFile arquivo = new MockMultipartFile(
                "file",
                "produtos.xls",
                "application/vnd.ms-excel",
                "conteudo que nao e uma planilha".getBytes());
        when(tenantContext.exigirGerencia()).thenReturn(contexto);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> produtoService.importarPlanilha(arquivo));

        assertEquals(
                "Arquivo Excel invalido. Envie uma planilha .xlsx ou .xls valida.",
                exception.getMessage());
        verifyNoInteractions(produtoRepository, categoriaRepository);
    }

    @Test
    void importarRejeitaPlanilhaAcimaDoLimiteDeLinhas() throws IOException {
        MockMultipartFile arquivo;
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Produtos");
            sheet.createRow(0).createCell(0).setCellValue("Nome");
            sheet.createRow(10_001).createCell(0).setCellValue("Produto excedente");
            workbook.write(output);
            arquivo = new MockMultipartFile(
                    "file",
                    "produtos.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
        when(tenantContext.exigirGerencia()).thenReturn(contexto);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> produtoService.importarPlanilha(arquivo));

        assertEquals(
                "A planilha deve conter no maximo 10000 produtos.",
                exception.getMessage());
        verifyNoInteractions(produtoRepository, categoriaRepository);
    }

    @Test
    void importarValidaLimitesDeTextoPrecoEInteiroAntesDePersistir()
            throws IOException {
        MockMultipartFile arquivo = planilha(
                new Object[]{"Produto A", "SKU-A", "D".repeat(501), null,
                        10, 20, 2, 1},
                new Object[]{"Produto B", "SKU-B", null, null,
                        10_000_000_000D, 20, 2, 1},
                new Object[]{"Produto C", "SKU-C", null, null,
                        10.123, 20, 2, 1},
                new Object[]{"Produto D", "SKU-D", null, null,
                        10, 20, 2_147_483_648D, 1});
        when(tenantContext.exigirGerencia()).thenReturn(contexto);

        ImportResultDTO resultado = produtoService.importarPlanilha(arquivo);

        assertEquals(0, resultado.getImportados());
        assertEquals(0, resultado.getAtualizados());
        assertEquals(4, resultado.getIgnorados());
        assertEquals(
                "Linha 2: Descricao excede 500 caracteres",
                resultado.getErros().get(0));
        assertEquals(
                "Linha 3: Preco Custo excede 10 inteiros ou 2 casas decimais",
                resultado.getErros().get(1));
        assertEquals(
                "Linha 4: Preco Custo excede 10 inteiros ou 2 casas decimais",
                resultado.getErros().get(2));
        assertEquals(
                "Linha 5: Quantidade em estoque invalida",
                resultado.getErros().get(3));
        verifyNoInteractions(produtoRepository, categoriaRepository);
    }

    @Test
    void importarPropagaFalhaDePersistenciaParaRollbackAtomico()
            throws IOException {
        MockMultipartFile arquivo = planilha(
                new Object[]{"Camiseta", "CAM-003", null, null,
                        10, 20, 2, 1});
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(produtoRepository.findBySkuIgnoreCaseAndLojaForUpdate("CAM-003", loja))
                .thenReturn(Optional.empty());
        when(produtoRepository.save(any(Produto.class)))
                .thenThrow(new DataIntegrityViolationException("falha no banco"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> produtoService.importarPlanilha(arquivo));
    }

    @Test
    void importarSkuRepetidoContaUmaInclusaoEUmaAtualizacao()
            throws IOException {
        MockMultipartFile arquivo = planilha(
                new Object[]{"Camiseta azul", "CAM-004", null, null,
                        10, 20, 2, 1},
                new Object[]{"Camiseta verde", "CAM-004", null, null,
                        12, 24, 4, 2});
        when(tenantContext.exigirGerencia()).thenReturn(contexto);
        when(produtoRepository.findBySkuIgnoreCaseAndLojaForUpdate("CAM-004", loja))
                .thenReturn(Optional.empty());
        when(produtoRepository.save(any(Produto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ImportResultDTO resultado = produtoService.importarPlanilha(arquivo);

        assertEquals(1, resultado.getImportados());
        assertEquals(1, resultado.getAtualizados());
        assertEquals(0, resultado.getIgnorados());
        verify(produtoRepository).findBySkuIgnoreCaseAndLojaForUpdate("CAM-004", loja);
        verify(produtoRepository, times(2)).save(any(Produto.class));
    }

    private Produto produto(Long id) {
        return Produto.builder()
                .id(id)
                .usuario(usuario)
                .loja(loja)
                .nome("Camiseta")
                .descricao("Descricao anterior")
                .sku("CAM-001")
                .precoCusto(new BigDecimal("25.00"))
                .precoVenda(new BigDecimal("59.90"))
                .quantidadeEstoque(18)
                .estoqueMinimo(4)
                .categoria(categoria)
                .ativo(true)
                .build();
    }

    private ProdutoRequest request() {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Camiseta atualizada");
        request.setDescricao("Algodao, tamanho M");
        request.setSku("CAM-001");
        request.setPrecoCusto(new BigDecimal("26.00"));
        request.setPrecoVenda(new BigDecimal("62.90"));
        request.setQuantidadeEstoque(20);
        request.setEstoqueMinimo(5);
        request.setCategoriaId(categoria.getId());
        return request;
    }

    private MockMultipartFile planilha(Object[]... linhas)
            throws IOException {
        return criarPlanilha(
                new XSSFWorkbook(),
                "produtos.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                linhas);
    }

    private MockMultipartFile planilhaLegada(Object[]... linhas)
            throws IOException {
        return criarPlanilha(
                new HSSFWorkbook(),
                "produtos.xls",
                "application/vnd.ms-excel",
                linhas);
    }

    private MockMultipartFile criarPlanilha(
            Workbook workbook,
            String nome,
            String contentType,
            Object[]... linhas) throws IOException {
        try (workbook;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (workbook instanceof HSSFWorkbook legado) {
                legado.createInformationProperties();
                legado.getSummaryInformation().getFirstSection().setCodepage(1252);
                legado.getDocumentSummaryInformation().getFirstSection().setCodepage(1252);
            }
            Sheet sheet = workbook.createSheet("Produtos");
            Row cabecalho = sheet.createRow(0);
            String[] colunas = {
                    "Nome", "SKU", "Descricao", "Categoria",
                    "PrecoCusto", "PrecoVenda", "QtdEstoque", "EstoqueMinimo"
            };
            for (int i = 0; i < colunas.length; i++) {
                cabecalho.createCell(i).setCellValue(colunas[i]);
            }

            for (int linhaIndex = 0; linhaIndex < linhas.length; linhaIndex++) {
                Row row = sheet.createRow(linhaIndex + 1);
                Object[] valores = linhas[linhaIndex];
                for (int coluna = 0; coluna < valores.length; coluna++) {
                    Object valor = valores[coluna];
                    if (valor instanceof Number numero) {
                        row.createCell(coluna).setCellValue(numero.doubleValue());
                    } else if (valor != null) {
                        row.createCell(coluna).setCellValue(valor.toString());
                    }
                }
            }

            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    nome,
                    contentType,
                    output.toByteArray());
        }
    }
}
