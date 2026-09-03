package com.dropsales.service;

import com.dropsales.model.*;
import com.dropsales.repository.EmpresaRepository;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.MembroEmpresaRepository;
import com.dropsales.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final EmpresaRepository empresaRepository;
    private final LojaRepository lojaRepository;
    private final MembroEmpresaRepository membroRepository;
    private final UsuarioRepository usuarioRepository;

    /** Cria a empresa, a primeira loja e o vinculo de proprietario numa unica transacao. */
    @Transactional
    public Loja criarEstruturaInicial(Usuario usuario, String nomeEmpresa) {
        // Cadastros legados sem vinculo empresarial eram OPERADOR. Ao criar a
        // primeira empresa, esse usuario passa a ser o administrador proprietario.
        if (usuario.getPerfil() != Perfil.ADMIN) {
            usuario.setPerfil(Perfil.ADMIN);
            usuario = usuarioRepository.save(usuario);
        }

        String nome = normalizarNome(nomeEmpresa, usuario.getNome());

        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nome(nome)
                .ativo(true)
                .build());

        Loja loja = lojaRepository.save(Loja.builder()
                .empresa(empresa)
                .nome("Loja principal")
                .timezone("America/Sao_Paulo")
                .ativo(true)
                .build());

        membroRepository.save(MembroEmpresa.builder()
                .empresa(empresa)
                .usuario(usuario)
                .papel(PapelEmpresa.PROPRIETARIO)
                .ativo(true)
                .build());

        return loja;
    }

    private String normalizarNome(String nomeEmpresa, String nomeUsuario) {
        if (nomeEmpresa != null && !nomeEmpresa.isBlank()) {
            return nomeEmpresa.trim();
        }
        String primeiroNome = nomeUsuario == null || nomeUsuario.isBlank()
                ? "Minha" : nomeUsuario.trim().split("\\s+")[0];
        return "Loja de " + primeiroNome;
    }
}
