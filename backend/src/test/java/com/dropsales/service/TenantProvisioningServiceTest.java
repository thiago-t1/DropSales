package com.dropsales.service;

import com.dropsales.model.Empresa;
import com.dropsales.model.Loja;
import com.dropsales.model.MembroEmpresa;
import com.dropsales.model.PapelEmpresa;
import com.dropsales.model.Perfil;
import com.dropsales.model.Usuario;
import com.dropsales.repository.EmpresaRepository;
import com.dropsales.repository.LojaRepository;
import com.dropsales.repository.MembroEmpresaRepository;
import com.dropsales.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    @Mock private EmpresaRepository empresaRepository;
    @Mock private LojaRepository lojaRepository;
    @Mock private MembroEmpresaRepository membroRepository;
    @Mock private UsuarioRepository usuarioRepository;

    @Test
    void promoveUsuarioLegadoAoCriarSuaPrimeiraEmpresa() {
        TenantProvisioningService service = new TenantProvisioningService(
                empresaRepository, lojaRepository, membroRepository, usuarioRepository);
        Usuario usuario = Usuario.builder()
                .id(1L)
                .nome("Thiago")
                .perfil(Perfil.OPERADOR)
                .build();

        when(usuarioRepository.save(usuario)).thenReturn(usuario);
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(i -> i.getArgument(0));
        when(lojaRepository.save(any(Loja.class))).thenAnswer(i -> i.getArgument(0));

        service.criarEstruturaInicial(usuario, null);

        assertEquals(Perfil.ADMIN, usuario.getPerfil());
        verify(usuarioRepository).save(usuario);
        ArgumentCaptor<MembroEmpresa> membro = ArgumentCaptor.forClass(MembroEmpresa.class);
        verify(membroRepository).save(membro.capture());
        assertEquals(PapelEmpresa.PROPRIETARIO, membro.getValue().getPapel());
    }
}
