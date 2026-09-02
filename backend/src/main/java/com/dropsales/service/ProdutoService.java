package com.dropsales.service;

import com.dropsales.dto.ImportResultDTO;
import com.dropsales.dto.ProdutoRequest;
import com.dropsales.dto.ProdutoResponse;
import com.dropsales.exception.BusinessException;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.Categoria;
import com.dropsales.model.Loja;
import com.dropsales.model.Produto;
import com.dropsales.model.Usuario;
import com.dropsales.repository.CategoriaRepository;
import com.dropsales.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private static final int MAX_IMPORT_ROWS = 10_000;

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final TenantContextService tenantContext;

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarAtivos() {
        Loja loja = tenantContext.atual().loja();
        return produtoRepository.findByLojaAndAtivoTrue(loja).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponse> listarEstoqueBaixo() {
        Loja loja = tenantContext.atual().loja();
        return produtoRepository.findProdutosComEstoqueBaixo(loja).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProdutoResponse buscarPorId(Long id) {
        Loja loja = tenantContext.atual().loja();
        Produto produto = produtoRepository.findByIdAndLoja(id, loja)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
        return toResponse(produto);
    }

    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirGerencia();
        Usuario usuario = contexto.usuario();
        Loja loja = contexto.loja();
        Categoria categoria = resolverCategoria(request.getCategoriaId(), loja);
        Produto produto = Produto.builder()
                .usuario(usuario)
                .loja(loja)
                .nome(request.getNome().trim())
                .descricao(request.getDescricao())
                .sku(normalizarSku(request.getSku()))
                .precoCusto(request.getPrecoCusto())
                .precoVenda(request.getPrecoVenda())
                .quantidadeEstoque(request.getQuantidadeEstoque())
                .estoqueMinimo(request.getEstoqueMinimo())
                .categoria(categoria)
                .ativo(true)
                .build();
        return toResponse(produtoRepository.save(produto));
    }

    @Transactional
    public ProdutoResponse atualizar(Long id, ProdutoRequest request) {
        Loja loja = tenantContext.exigirGerencia().loja();
        Produto produto = produtoRepository.findByIdAndLojaForUpdate(id, loja)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
        produto.setNome(request.getNome().trim());
        produto.setDescricao(request.getDescricao());
        produto.setSku(normalizarSku(request.getSku()));
        produto.setPrecoCusto(request.getPrecoCusto());
        produto.setPrecoVenda(request.getPrecoVenda());
        produto.setQuantidadeEstoque(request.getQuantidadeEstoque());
        produto.setEstoqueMinimo(request.getEstoqueMinimo());
        produto.setCategoria(resolverCategoria(request.getCategoriaId(), loja));
        return toResponse(produtoRepository.save(produto));
    }

    @Transactional
    public void excluir(Long id) {
        Loja loja = tenantContext.exigirGerencia().loja();
        Produto produto = produtoRepository.findByIdAndLojaForUpdate(id, loja)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    /**
     * Importa produtos a partir de um arquivo Excel .xlsx ou .xls.
     * Colunas esperadas: Nome | SKU | Descricao | Categoria | PrecoCusto | PrecoVenda | QtdEstoque | EstoqueMinimo
     */
    @Transactional
    public ImportResultDTO importarPlanilha(MultipartFile file) {
        TenantContextService.ContextoAtual contexto = tenantContext.exigirGerencia();
        Usuario usuario = contexto.usuario();
        Loja loja = contexto.loja();
        int importados = 0, atualizados = 0, ignorados = 0;
        List<String> erros = new ArrayList<>();
        List<LinhaImportacao> linhasValidas = new ArrayList<>();

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            if (wb.getNumberOfSheets() == 0) {
                throw new BusinessException("A planilha nao possui abas para importar");
            }
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_IMPORT_ROWS) {
                throw new BusinessException(
                        "A planilha deve conter no maximo " + MAX_IMPORT_ROWS
                                + " produtos.");
            }
            int startRow = 1; // pula cabecalho

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    LinhaImportacao linha = lerLinha(row, i + 1);
                    String erro = validarLinha(linha);
                    if (erro != null) {
                        erros.add(erro);
                        ignorados++;
                        continue;
                    }
                    linhasValidas.add(linha);
                } catch (Exception ex) {
                    erros.add("Linha " + (i+1) + ": " + ex.getMessage());
                    ignorados++;
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException | EncryptedDocumentException | IllegalArgumentException ex) {
            throw new BusinessException(
                    "Arquivo Excel invalido. Envie uma planilha .xlsx ou .xls valida.");
        }

        /*
         * Persistencia ocorre apenas depois que todas as linhas foram lidas e
         * validadas. Excecoes do JPA nao sao convertidas em erro de linha:
         * elas sobem e provocam rollback atomico, evitando devolver contagens
         * de registros que nao chegaram a ser confirmados no banco.
         */
        Map<String, Categoria> categoriasPorNome = new HashMap<>();
        Map<String, Produto> produtosPorSku = new HashMap<>();

        new TreeSet<>(linhasValidas.stream()
                .map(linha -> normalizarSku(linha.sku()))
                .toList())
                .forEach(sku -> produtoRepository
                        .findBySkuIgnoreCaseAndLojaForUpdate(sku, loja)
                        .ifPresent(produto -> produtosPorSku.put(sku, produto)));

        for (LinhaImportacao linha : linhasValidas) {
            Categoria categoria = resolverCategoriaImportada(
                    linha.categoria(), usuario, loja, categoriasPorNome);

            String sku = normalizarSku(linha.sku());
            Produto produto = produtosPorSku.get(sku);
            boolean atualizacao = produto != null;
            if (produto == null) {
                produto = Produto.builder()
                        .usuario(usuario)
                        .loja(loja)
                        .sku(sku)
                        .ativo(true)
                        .build();
                produtosPorSku.put(sku, produto);
            }

            aplicarLinha(produto, linha, categoria, sku);
            produtoRepository.save(produto);
            if (atualizacao) {
                atualizados++;
            } else {
                importados++;
            }
        }

        return ImportResultDTO.builder()
                .importados(importados).atualizados(atualizados)
                .ignorados(ignorados).erros(erros).build();
    }

    private LinhaImportacao lerLinha(Row row, int numero) {
        return new LinhaImportacao(
                numero,
                getString(row, 0),
                getString(row, 1),
                getString(row, 2),
                getString(row, 3),
                getBigDecimal(row, 4),
                getBigDecimal(row, 5),
                getInt(row, 6),
                getInt(row, 7, 5));
    }

    private String validarLinha(LinhaImportacao linha) {
        String prefixo = "Linha " + linha.numero() + ": ";
        if (linha.nome() == null || linha.nome().isBlank()) {
            return prefixo + "Nome vazio";
        }
        if (linha.nome().length() > 200) {
            return prefixo + "Nome excede 200 caracteres";
        }
        if (linha.sku() == null || linha.sku().isBlank()) {
            return prefixo + "SKU vazio";
        }
        if (linha.sku().length() > 50) {
            return prefixo + "SKU excede 50 caracteres";
        }
        if (linha.descricao() != null && linha.descricao().length() > 500) {
            return prefixo + "Descricao excede 500 caracteres";
        }
        if (linha.categoria() != null && linha.categoria().length() > 100) {
            return prefixo + "Categoria excede 100 caracteres";
        }
        if (linha.custo() == null || linha.custo().signum() < 0) {
            return prefixo + "Preco Custo invalido";
        }
        if (excedeLimiteMonetario(linha.custo())) {
            return prefixo
                    + "Preco Custo excede 10 inteiros ou 2 casas decimais";
        }
        if (linha.venda() == null || linha.venda().signum() <= 0) {
            return prefixo + "Preco Venda invalido";
        }
        if (excedeLimiteMonetario(linha.venda())) {
            return prefixo
                    + "Preco Venda excede 10 inteiros ou 2 casas decimais";
        }
        if (linha.quantidade() == null || linha.quantidade() < 0) {
            return prefixo + "Quantidade em estoque invalida";
        }
        if (linha.estoqueMinimo() == null || linha.estoqueMinimo() < 0) {
            return prefixo + "Estoque minimo invalido";
        }
        return null;
    }

    private boolean excedeLimiteMonetario(BigDecimal valor) {
        BigDecimal normalizado = valor.stripTrailingZeros();
        int casasDecimais = Math.max(normalizado.scale(), 0);
        int digitosInteiros = Math.max(
                normalizado.precision() - normalizado.scale(), 0);
        return digitosInteiros > 10 || casasDecimais > 2;
    }

    private Categoria resolverCategoriaImportada(
            String nome,
            Usuario usuario,
            Loja loja,
            Map<String, Categoria> categoriasPorNome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }

        String chave = nome.toLowerCase(Locale.ROOT);
        Categoria categoria = categoriasPorNome.get(chave);
        if (categoria != null) {
            return categoria;
        }

        categoria = categoriaRepository.findByNomeIgnoreCaseAndLoja(nome, loja)
                .orElseGet(() -> categoriaRepository.save(
                        Categoria.builder()
                                .nome(nome)
                                .usuario(usuario)
                                .loja(loja)
                                .build()));
        categoriasPorNome.put(chave, categoria);
        return categoria;
    }

    private void aplicarLinha(
            Produto produto,
            LinhaImportacao linha,
            Categoria categoria,
            String sku) {
        produto.setNome(linha.nome());
        produto.setDescricao(linha.descricao());
        produto.setSku(sku);
        produto.setPrecoCusto(linha.custo());
        produto.setPrecoVenda(linha.venda());
        produto.setQuantidadeEstoque(linha.quantidade());
        produto.setEstoqueMinimo(linha.estoqueMinimo());
        produto.setCategoria(categoria);
        produto.setAtivo(true);
    }

    private String normalizarSku(String sku) {
        if (sku == null || sku.isBlank()) return null;
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private Categoria resolverCategoria(Long categoriaId, Loja loja) {
        if (categoriaId == null) return null;
        return categoriaRepository.findByIdAndLoja(categoriaId, loja)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Categoria nao encontrada: " + categoriaId));
    }

    private String getString(Row row, int col) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        switch (c.getCellType()) {
            case STRING:  return c.getStringCellValue().trim();
            case NUMERIC: return NumberToTextConverter.toText(c.getNumericCellValue()).trim();
            case BOOLEAN: return String.valueOf(c.getBooleanCellValue()).trim();
            case FORMULA: {
                try { return c.getStringCellValue().trim(); }
                catch (Exception e) { return NumberToTextConverter.toText(c.getNumericCellValue()).trim(); }
            }
            default: return null;
        }
    }

    private BigDecimal getBigDecimal(Row row, int col) {
        try {
            Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) return null;
            return BigDecimal.valueOf(c.getNumericCellValue());
        } catch (Exception e) { return null; }
    }

    private Integer getInt(Row row, int col) {
        return getInt(row, col, 0);
    }

    private Integer getInt(Row row, int col, int def) {
        try {
            Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) return def;
            BigDecimal valor;
            if (c.getCellType() == CellType.NUMERIC
                    || c.getCellType() == CellType.FORMULA) {
                valor = BigDecimal.valueOf(c.getNumericCellValue());
            } else if (c.getCellType() == CellType.STRING) {
                String texto = c.getStringCellValue().trim();
                if (texto.isBlank()) return def;
                valor = new BigDecimal(texto);
            } else {
                return null;
            }
            return valor.stripTrailingZeros().intValueExact();
        } catch (Exception e) {
            return null;
        }
    }

    private ProdutoResponse toResponse(Produto p) {
        return ProdutoResponse.builder()
                .id(p.getId()).nome(p.getNome()).descricao(p.getDescricao()).sku(p.getSku())
                .precoCusto(p.getPrecoCusto()).precoVenda(p.getPrecoVenda())
                .quantidadeEstoque(p.getQuantidadeEstoque()).estoqueMinimo(p.getEstoqueMinimo())
                .categoriaId(p.getCategoria() != null ? p.getCategoria().getId() : null)
                .categoria(p.getCategoria() != null ? p.getCategoria().getNome() : null)
                .estoqueBaixo(p.isEstoqueBaixo())
                .build();
    }

    private record LinhaImportacao(
            int numero,
            String nome,
            String sku,
            String descricao,
            String categoria,
            BigDecimal custo,
            BigDecimal venda,
            Integer quantidade,
            Integer estoqueMinimo) {
    }
}
