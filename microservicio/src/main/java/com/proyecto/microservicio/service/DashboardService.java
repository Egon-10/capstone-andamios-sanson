package com.proyecto.microservicio.service;

import com.proyecto.microservicio.model.DashboardDTO;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.repository.*;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashboardService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimientoRepository movimientoRepository;

    public DashboardService(
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository,
            ProveedorRepository proveedorRepository,
            UsuarioRepository usuarioRepository,
            MovimientoRepository movimientoRepository) {

        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.proveedorRepository = proveedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimientoRepository = movimientoRepository;
    }

    public DashboardDTO obtenerResumen() {

        DashboardDTO dto = new DashboardDTO();

        dto.setTotalProductos(
                productoRepository.count());

        dto.setTotalCategorias(
                categoriaRepository.count());

        dto.setTotalProveedores(
                proveedorRepository.count());

        dto.setTotalUsuarios(
                usuarioRepository.count());

        dto.setStockTotal(
                productoRepository.obtenerStockTotal());

        return dto;
    }

    public List<MovimientoDTO> obtenerUltimosMovimientos() {

        List<MovimientoDTO> movimientos =
                movimientoRepository.obtenerMovimientosConDetalle();

        if (movimientos.size() > 5) {

            return movimientos.subList(0, 5);
        }

        return movimientos;
    }

   public List<Object[]> productosPorCategoria() {

    return productoRepository.productosPorCategoria();
}

public List<Object[]> resumenMovimientos() {

    return movimientoRepository.resumenMovimientos();
}

public List<Object[]> productosMayorStock() {

    return productoRepository.productosConMayorStock();
}


public List<Object[]> obtenerProductosStockCritico() {

    return productoRepository
            .productosEnStockCritico();
}
public List<Object[]> obtenerMovimientosPorDia() {

    return movimientoRepository
            .movimientosPorDia();
}
public List<Object[]> obtenerMovimientosPorSemana(
        Integer offset) {

    return movimientoRepository
            .movimientosPorSemana(offset);
}
}