package com.proyecto.microservicio.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Identificador (jti) de un token de acceso invalidado al cerrar sesión (HU-08). */
@Entity
@Table(name = "tokens_revocados")
public class TokenRevocado {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(nullable = false)
    private Instant expiracion;

    public TokenRevocado() {
    }

    public TokenRevocado(String jti, Instant expiracion) {
        this.jti = jti;
        this.expiracion = expiracion;
    }

    public String getJti() { return jti; }
    public Instant getExpiracion() { return expiracion; }
}
