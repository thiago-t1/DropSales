package com.dropsales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Foto mantida fora de usuarios para que autenticacao e contexto nunca carreguem
 * o binario a cada requisicao.
 */
@Entity
@Table(name = "usuario_fotos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsuarioFoto {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "conteudo", nullable = false)
    private byte[] conteudo;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
