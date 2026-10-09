package com.proyecto.microservicio.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * HU-32: intento de inicio de sesión, exitoso o no.
 *
 * Se guarda el identificador tal como se escribió, aunque no corresponda a
 * ninguna cuenta: los intentos contra cuentas inexistentes son los que
 * delatan un ataque de adivinación. La contraseña nunca se guarda.
 */
@Entity
@Table(name = "accesos")
public class Acceso {

    public static final String EXITOSO = "EXITOSO";
    public static final String FALLIDO = "FALLIDO";
    public static final String BLOQUEADO = "BLOQUEADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, length = 100)
    private String identificador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 12)
    private String resultado;

    @Column(length = 60)
    private String motivo;

    @Column(length = 45)
    private String ip;

    @Column(length = 255)
    private String agente;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getAgente() { return agente; }
    public void setAgente(String agente) { this.agente = agente; }
}
