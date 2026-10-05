package com.proyecto.microservicio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * HU-16: ajuste de inventario por conteo físico. El encargado registra el
 * conteo y el ajuste queda PENDIENTE; el stock no cambia todavía. Solo el
 * administrador o el gerente lo aprueban, y es la aprobación la que genera el
 * movimiento que corrige el inventario. De este modo una diferencia de conteo
 * nunca modifica el stock sin que quede constancia de quién la autorizó.
 */
@Entity
@Table(name = "ajustes_inventario")
public class AjusteInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** Stock que el sistema tenía registrado en el momento del conteo. */
    @Column(name = "stock_sistema", nullable = false)
    private Integer stockSistema;

    /** Unidades realmente contadas en el almacén. */
    @Column(name = "stock_fisico", nullable = false)
    private Integer stockFisico;

    /** stockFisico - stockSistema. Positiva si sobra, negativa si falta. */
    @Column(nullable = false)
    private Integer diferencia;

    @ManyToOne
    @JoinColumn(name = "motivo")
    private MotivoMovimiento motivo;

    @Column(length = 200)
    private String observacion;

    @Column(length = 12, nullable = false)
    private String estado = EstadosAjuste.PENDIENTE;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_solicita_id")
    private Usuario usuarioSolicita;

    @ManyToOne
    @JoinColumn(name = "usuario_aprueba_id")
    private Usuario usuarioAprueba;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;

    /** Movimiento que la aprobación generó para corregir el stock. */
    @OneToOne
    @JoinColumn(name = "movimiento_id")
    private Movimiento movimiento;

    public AjusteInventario() {
    }

    /** Un ajuste solo se puede aprobar o rechazar mientras siga pendiente. */
    public boolean estaPendiente() {
        return EstadosAjuste.PENDIENTE.equals(estado);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public Integer getStockSistema() {
        return stockSistema;
    }

    public void setStockSistema(Integer stockSistema) {
        this.stockSistema = stockSistema;
    }

    public Integer getStockFisico() {
        return stockFisico;
    }

    public void setStockFisico(Integer stockFisico) {
        this.stockFisico = stockFisico;
    }

    public Integer getDiferencia() {
        return diferencia;
    }

    public void setDiferencia(Integer diferencia) {
        this.diferencia = diferencia;
    }

    public MotivoMovimiento getMotivo() {
        return motivo;
    }

    public void setMotivo(MotivoMovimiento motivo) {
        this.motivo = motivo;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Usuario getUsuarioSolicita() {
        return usuarioSolicita;
    }

    public void setUsuarioSolicita(Usuario usuarioSolicita) {
        this.usuarioSolicita = usuarioSolicita;
    }

    public Usuario getUsuarioAprueba() {
        return usuarioAprueba;
    }

    public void setUsuarioAprueba(Usuario usuarioAprueba) {
        this.usuarioAprueba = usuarioAprueba;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public LocalDateTime getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(LocalDateTime fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Movimiento getMovimiento() {
        return movimiento;
    }

    public void setMovimiento(Movimiento movimiento) {
        this.movimiento = movimiento;
    }
}
