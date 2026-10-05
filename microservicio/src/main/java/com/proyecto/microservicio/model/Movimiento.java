package com.proyecto.microservicio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Asiento del kardex: una entrada o una salida de un producto.
 *
 * Un movimiento es inmutable una vez registrado. No se edita ni se borra: si
 * hay que corregirlo se anula (HU-15), lo que marca el asiento original como
 * ANULADO y crea un asiento compensatorio de tipo contrario. Así el kardex
 * conserva la traza completa de lo ocurrido y el saldo sigue siendo correcto.
 */
@Entity
@Table(name = "movimientos")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ENTRADA o SALIDA. Ver {@link TiposMovimiento}. */
    @Column(length = 10)
    private String tipo;

    /** HU-14: motivo tipificado, tomado del catálogo de motivos. */
    @ManyToOne
    @JoinColumn(name = "motivo")
    private MotivoMovimiento motivo;

    @Column(length = 200)
    private String observacion;

    @Column(nullable = false)
    private Integer cantidad;

    private LocalDateTime fecha;

    /** HU-15: REGISTRADO, ANULADO o COMPENSACION. Ver {@link EstadosMovimiento}. */
    @Column(length = 12, nullable = false)
    private String estado = EstadosMovimiento.REGISTRADO;

    /**
     * HU-17: costo unitario del asiento. En las entradas lo informa quien
     * registra; en las salidas se copia el costo promedio vigente del producto,
     * para que la valorización del inventario no dependa del precio de venta.
     */
    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 4)
    private BigDecimal costoUnitario = BigDecimal.ZERO;

    /**
     * HU-18: stock del producto después de aplicar este asiento. Se guarda al
     * registrar para que el kardex no tenga que reconstruir el saldo en cada
     * consulta. Los movimientos heredados de la base anterior lo tienen vacío,
     * y en ese caso el kardex lo calcula como suma acumulada.
     */
    @Column(name = "saldo_resultante")
    private Integer saldoResultante;

    /** Asiento que este movimiento compensa, cuando es una anulación (HU-15). */
    @ManyToOne
    @JoinColumn(name = "movimiento_origen_id")
    private Movimiento movimientoOrigen;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @ManyToOne
    @JoinColumn(name = "usuario_anulacion_id")
    private Usuario usuarioAnulacion;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /**
     * Quien registró el movimiento. Se toma siempre del token de la sesión y
     * nunca de la solicitud del cliente (CR-04), de modo que un usuario no
     * puede atribuir un movimiento a otro.
     */
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    public Movimiento() {
    }

    /**
     * Signo con el que este asiento afecta al stock. Todo asiento cuenta,
     * incluido el anulado: lo que revierte a un asiento anulado es su asiento
     * compensatorio, y la suma de los dos es cero (ver {@link EstadosMovimiento}).
     */
    public int efectoEnStock() {
        return TiposMovimiento.signo(tipo) * cantidad;
    }

    /** Valor del asiento: cantidad por costo unitario. */
    public BigDecimal valorizado() {
        if (costoUnitario == null || cantidad == null) {
            return BigDecimal.ZERO;
        }
        return costoUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public boolean estaAnulado() {
        return EstadosMovimiento.ANULADO.equals(estado);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
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

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public void setCostoUnitario(BigDecimal costoUnitario) {
        this.costoUnitario = costoUnitario;
    }

    public Integer getSaldoResultante() {
        return saldoResultante;
    }

    public void setSaldoResultante(Integer saldoResultante) {
        this.saldoResultante = saldoResultante;
    }

    public Movimiento getMovimientoOrigen() {
        return movimientoOrigen;
    }

    public void setMovimientoOrigen(Movimiento movimientoOrigen) {
        this.movimientoOrigen = movimientoOrigen;
    }

    public LocalDateTime getFechaAnulacion() {
        return fechaAnulacion;
    }

    public void setFechaAnulacion(LocalDateTime fechaAnulacion) {
        this.fechaAnulacion = fechaAnulacion;
    }

    public Usuario getUsuarioAnulacion() {
        return usuarioAnulacion;
    }

    public void setUsuarioAnulacion(Usuario usuarioAnulacion) {
        this.usuarioAnulacion = usuarioAnulacion;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}
