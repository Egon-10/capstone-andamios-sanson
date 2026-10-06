package com.proyecto.microservicio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * HU-14: motivo tipificado de un movimiento. Se modela como catálogo y no como
 * enumeración del código para que el administrador pueda dar de baja un motivo
 * sin recompilar el sistema. El campo aplicaA restringe cada motivo al tipo de
 * movimiento que le corresponde, de modo que un motivo de salida no puede
 * usarse en una entrada.
 */
@Entity
@Table(name = "motivos_movimiento")
public class MotivoMovimiento {

    @Id
    @Column(length = 30)
    private String codigo;

    @Column(length = 80, nullable = false)
    private String nombre;

    /** ENTRADA o SALIDA: el tipo de movimiento en el que este motivo es válido. */
    @Column(name = "aplica_a", length = 10, nullable = false)
    private String aplicaA;

    /** Cuando es verdadero, el movimiento debe traer una observación que lo explique. */
    @Column(name = "exige_nota", nullable = false)
    private boolean exigeNota;

    @Column(nullable = false)
    private boolean activo = true;

    public MotivoMovimiento() {
        // Requerido por JPA para instanciar la entidad al leerla de la base.
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getAplicaA() {
        return aplicaA;
    }

    public void setAplicaA(String aplicaA) {
        this.aplicaA = aplicaA;
    }

    public boolean isExigeNota() {
        return exigeNota;
    }

    public void setExigeNota(boolean exigeNota) {
        this.exigeNota = exigeNota;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
