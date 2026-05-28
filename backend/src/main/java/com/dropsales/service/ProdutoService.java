package com.dropsales.service;

import com.dropsales.dto.ImportResultDTO;
import com.dropsales.dto.ProdutoRequest;
import com.dropsales.dto.ProdutoResponse;
import com.dropsales.exception.ResourceNotFoundException;
import com.dropsales.model.Categoria;
import com.dropsales.model.Produto;
import com.dropsales.model.Usuario;
import com.dropsales.repository.CategoriaRepository;
import com.dropsales.repository.ProdutoRepository;
import com.dropsales.repository.UsuarioRepository;
import com.dropsales.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    private Usuario getUsuarioLogado() {
        String email = SecurityUtils.getCurrentUserEmail();
        if (email == null) throw new com.dropsales.exception.BusinessException("Usuário não autenticado");
        return usuarioRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    public List<ProdutoResponse> listarAtivos() {
        return produtoRepository.findByUsuarioAndAtivoTrue(getUsuarioLogado()).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProdutoResponse> listarEstoqueBaixo() {
        return produtoRepository.findProdutosComEstoqueBaixo(getUsuarioLogado()).stream()
                .map(this::toResponse)
                .toList();
    }

    public ProdutoResponse buscarPorId(Long id) {
        Produto produto = produtoRepository.findByIdAndUsuario(id, getUsuarioLogado())
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
        return toResponse(produto);
    }

    @Transactional
    public ProdutoResponse criar(ProdutoRequest request) {
        Usuario usuario = getUsuarioLogado();
        Categoria categoria = resolverCategoria(request.getCategoriaId(), usuario);
        Produto produto = Produto.builder()
                .usuario(usuario)
                .nome(request.getNome())
                .descricao(request.getDescricao())
                .sku(request.getSku())
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
        Usuario usuario = getUsuarioLogado();
        Produto produto = produtoRepository.findByIdAndUsuario(id, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
        produto.setNome(request.getNome());
        produto.setDescricao(request.getDescricao());
        produto.setSku(request.getSku());
        produto.setPrecoCusto(request.getPrecoCusto());
        produto.setPrecoVenda(request.getPrecoVenda());
        produto.setQuantidadeEstoque(request.getQuantidadeEstoque());
        produto.setEstoqueMinimo(request.getEstoqueMinimo());
        produto.setCategoria(resolverCategoria(request.getCategoriaId(), usuario));
        return toResponse(produtoRepository.save(produto));
    }

    @Transactional
    public void excluir(Long id) {
        Produto produto = produtoRepository.findByIdAndUsuario(id, getUsuarioLogado())
                .orElseThrow(() -> new ResourceNotFoundException("Produto nao encontrado: " + id));
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    /**
     * Importa produtos a partir de um arquivo .xlsx.
     * Colunas esperadas: Nome | SKU | Descricao | Categoria | PrecoCusto | PrecoVenda | QtdEstoque | EstoqueMinimo
     */
    @Transactional
    public ImportResultDTO importarPlanilha(MultipartFile file) throws IOException {
        Usuario usuario = getUsuarioLogado();
        int importados = 0, atualizados = 0, ignorados = 0;
        List<String> erros = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int startRow = 1; // pula cabecalho

            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String nome   = getString(row, 0);
                    String sku    = getString(row, 1);
                    String desc   = getString(row, 2);
                    String catNome= getString(row, 3);
                    BigDecimal custo = getBigDecimal(row, 4);
                    BigDecimal venda = getBigDecimal(row, 5);
                    int qtd          = getInt(row, 6);
                    int minimo       = getInt(row, 7, 5);

                    // Validacoes obrigatorias
                    if (nome == null || nome.isBlank())  { erros.add("Linha " + (i+1) + ": Nome vazio");  ignorados++; continue; }
                    if (sku  == null || sku.isBlank())   { erros.add("Linha " + (i+1) + ": SKU vazio");   ignorados++; continue; }
                    if (custo == null)                   { erros.add("Linha " + (i+1) + ": Preco Custo invalido"); ignorados++; continue; }
                    if (venda == null)                   { erros.add("Linha " + (i+1) + ": Preco Venda invalido"); ignorados++; continue; }

                    // Categoria: busca ou cria
                    Categoria categoria = null;
                    if (catNome != null && !catNome.isBlank()) {
                        categoria = categoriaRepository.findByNomeIgnoreCaseAndUsuario(catNome, usuario)
                                .orElseGet(() -> categoriaRepository.save(
                                        Categoria.builder().nome(catNome).usuario(usuario).build()));
                    }

                    // Produto: atualiza se SKU ja existe, cria caso contrario
                    Optional<Produto> existente = produtoRepository.findBySkuAndUsuario(sku, usuario);
                    if (existente.isPresent()) {
                        Produto p = existente.get();
                        p.setNome(nome); p.setDescricao(desc); p.setPrecoCusto(custo);
                        p.setPrecoVenda(venda); p.setQuantidadeEstoque(qtd);
                        p.setEstoqueMinimo(minimo); p.setCategoria(categoria); p.setAtivo(true);
                        produtoRepository.save(p);
                        atualizados++;
                    } else {
                        produtoRepository.save(Produto.builder()
                                .usuario(usuario)
                                .nome(nome).descricao(desc).sku(sku).precoCusto(custo)
                                .precoVenda(venda).quantidadeEstoque(qtd)
                                .estoqueMinimo(minimo).categoria(categoria).ativo(true).build());
                        importados++;
                    }
                } catch (Exception ex) {
                    erros.add("Linha " + (i+1) + ": " + ex.getMessage());
                    ignorados++;
                }
            }
        }
        return ImportResultDTO.builder()
                .importados(importados).atualizados(atualizados)
                .ignorados(ignorados).erros(erros).build();
    }

    // ---- Helpers ----
    private Categoria resolverCategoria(Long categoriaId, Usuario usuario) {
        if (categoriaId == null) return null;
        Categoria cat = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria nao encontrada: " + categoriaId));
        if (!cat.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Categoria nao encontrada: " + categoriaId);
        }
        return cat;
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

    private int getInt(Row row, int col) { return getInt(row, col, 0); }
    private int getInt(Row row, int col, int def) {
        try {
            Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (c == null) return def;
            return (int) c.getNumericCellValue();
        } catch (Exception e) { return def; }
    }

    private ProdutoResponse toResponse(Produto p) {
        return ProdutoResponse.builder()
                .id(p.getId()).nome(p.getNome()).sku(p.getSku())
                .precoCusto(p.getPrecoCusto()).precoVenda(p.getPrecoVenda())
                .quantidadeEstoque(p.getQuantidadeEstoque()).estoqueMinimo(p.getEstoqueMinimo())
                .categoria(p.getCategoria() != null ? p.getCategoria().getNome() : null)
                .estoqueBaixo(p.isEstoqueBaixo())
                .build();
    }
}