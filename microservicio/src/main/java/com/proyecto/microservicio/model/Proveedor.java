package com.proyecto.microservicio.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "proveedores")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100, nullable = false)
    private String nombre;

    /** RUC de 11 digitos. Unico: dos proveedores no pueden tener el mismo. */
    @Column(length = 11, unique = true)
    private String ruc;

    private String direccion;

    private String telefono;

    private String correo;

    /**
     * HU-11: baja logica. Un proveedor retirado deja de ofrecerse al registrar
     * productos, pero los productos que ya lo referencian conservan el dato.
     */
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    public Proveedor() {
    }

    public Proveedor(Long id,
                     String nombre,
                     String ruc,
                     String direccion,
                     String telefono,
                     String correo) {

        this.id = id;
        this.nombre = nombre;
        this.ruc = ruc;
        this.direccion = direccion;
        this.telefono = telefono;
        this.correo = correo;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRuc() {
        return ruc;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
