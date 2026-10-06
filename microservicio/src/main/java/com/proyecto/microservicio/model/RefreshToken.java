package com.proyecto.microservicio.model;

import com.proyecto.microservicio.config.ZonaHoraria;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Token de renovación de sesión. Solo se guarda su hash SHA-256;
 * cada uso lo revoca y emite uno nuevo (rotación).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime expiracion;

    @Column(nullable = false)
    private boolean revocado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    public RefreshToken() {
    }

    public RefreshToken(String tokenHash, Usuario usuario, LocalDateTime expiracion) {
        this.tokenHash = tokenHash;
        this.usuario = usuario;
        this.expiracion = expiracion;
        this.revocado = false;
        this.fechaCreacion = ZonaHoraria.ahora();
    }

    public boolean estaVigente(LocalDateTime ahora) {
        return !revocado && expiracion.isAfter(ahora);
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getExpiracion() { return expiracion; }
    public boolean isRevocado() { return revocado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }

    public void revocar() { this.revocado = true; }
}
