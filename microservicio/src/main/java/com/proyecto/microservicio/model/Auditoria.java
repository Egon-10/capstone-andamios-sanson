package com.proyecto.microservicio.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String accion;

    /**
     * Explicación legible de la acción. Se separó de `accion`, que ahora es el
     * código, porque una anulación o un ajuste necesitan dejar constancia de su
     * justificación y 255 caracteres no alcanzaban para ambas cosas.
     */
    @Column(length = 500)
    private String detalle;

    private LocalDateTime fecha;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public Auditoria() {
    }

    public Auditoria(Long id,
                     String accion,
                     LocalDateTime fecha,
                     Usuario usuario) {

        this.id = id;
        this.accion = accion;
        this.fecha = fecha;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public String getAccion() {
        return accion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }
}
