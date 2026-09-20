package com.proyecto.microservicio.service;

import com.proyecto.microservicio.model.*;
import com.proyecto.microservicio.repository.*;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MovimientoService {

    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;

    public MovimientoService(
            MovimientoRepository movimientoRepository,
            ProductoRepository productoRepository,
            UsuarioRepository usuarioRepository) {

        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<MovimientoDTO> listarMovimientos() {
        return movimientoRepository.obtenerMovimientosConDetalle();
    }

    public Movimiento registrarEntrada(
            Long productoId,
            Integer cantidad,
            Long usuarioId) {

        Producto producto =
                productoRepository.findById(productoId)
                        .orElseThrow(() ->
                                new RuntimeException("Producto no encontrado"));

        Usuario usuario =
                usuarioRepository.findById(usuarioId)
                        .orElseThrow(() ->
                                new RuntimeException("Usuario no encontrado"));

        producto.setStock(
                producto.getStock() + cantidad);

        productoRepository.save(producto);

        Movimiento movimiento =
                new Movimiento();

        movimiento.setTipo("ENTRADA");
        movimiento.setCantidad(cantidad);
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setProducto(producto);
        movimiento.setUsuario(usuario);

        return movimientoRepository.save(movimiento);
    }

    public Movimiento registrarSalida(
            Long productoId,
            Integer cantidad,
            Long usuarioId) {

        Producto producto =
                productoRepository.findById(productoId)
                        .orElseThrow(() ->
                                new RuntimeException("Producto no encontrado"));

        Usuario usuario =
                usuarioRepository.findById(usuarioId)
                        .orElseThrow(() ->
                                new RuntimeException("Usuario no encontrado"));

        if (producto.getStock() < cantidad) {

            throw new RuntimeException(
                    "Stock insuficiente");
        }

        producto.setStock(
                producto.getStock() - cantidad);

        productoRepository.save(producto);

        Movimiento movimiento =
                new Movimiento();

        movimiento.setTipo("SALIDA");
        movimiento.setCantidad(cantidad);
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setProducto(producto);
        movimiento.setUsuario(usuario);

        return movimientoRepository.save(movimiento);
    }


    public void eliminarMovimiento(Long id){

    Movimiento movimiento =
            movimientoRepository.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Movimiento no encontrado"));

    Producto producto = movimiento.getProducto();

    if(movimiento.getTipo().equals("ENTRADA")){

        producto.setStock(
                producto.getStock() -
                movimiento.getCantidad()
        );
    }

    if(movimiento.getTipo().equals("SALIDA")){

        producto.setStock(
                producto.getStock() +
                movimiento.getCantidad()
        );
    }

    productoRepository.save(producto);

    movimientoRepository.delete(movimiento);
}

public Movimiento actualizarMovimiento(
        Long id,
        Integer nuevaCantidad) {

    Movimiento movimiento =
            movimientoRepository.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Movimiento no encontrado"));

    Producto producto =
            movimiento.getProducto();

    Integer cantidadAnterior =
            movimiento.getCantidad();

    /*
     * Revertimos el movimiento anterior
     */

    if (movimiento.getTipo().equals("ENTRADA")) {

        producto.setStock(
                producto.getStock() - cantidadAnterior
        );

    } else {

        producto.setStock(
                producto.getStock() + cantidadAnterior
        );
    }

    /*
     * Aplicamos el nuevo movimiento
     */

    if (movimiento.getTipo().equals("ENTRADA")) {

        producto.setStock(
                producto.getStock() + nuevaCantidad
        );

    } else {

        if (producto.getStock() < nuevaCantidad) {

            throw new RuntimeException(
                    "Stock insuficiente");
        }

        producto.setStock(
                producto.getStock() - nuevaCantidad
        );
    }

    productoRepository.save(producto);

    movimiento.setCantidad(nuevaCantidad);

    return movimientoRepository.save(movimiento);
}
public List<MovimientoDTO> filtrarMovimientos(
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        String tipo) {

    return movimientoRepository.buscarConFiltros(
            fechaInicio,
            fechaFin,
            tipo
    );
}


}