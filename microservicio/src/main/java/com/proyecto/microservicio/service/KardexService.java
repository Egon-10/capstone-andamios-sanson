package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.KardexResponse;
import com.proyecto.microservicio.exception.RecursoNoEncontradoException;
import com.proyecto.microservicio.model.KardexLineaDTO;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.TiposMovimiento;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * HU-18: kardex por producto con saldo acumulado.
 *
 * El saldo de cada línea se obtiene sumando al saldo inicial del producto el
 * acumulado que devuelve la consulta. El saldo inicial se calcula como el
 * stock actual menos el neto de todos los asientos, lo que garantiza que la
 * última línea del kardex coincida con el inventario real.
 *
 * Se hace así, y no leyendo la columna saldo_resultante, porque los
 * movimientos heredados de la base anterior no la tienen: el sistema empezó a
 * registrarla en el Sprint 2. Reconstruir el saldo hacia atrás desde el stock
 * actual es la única forma de que la columna cuadre para el histórico completo.
 */
@Service
public class KardexService {

    private final MovimientoRepository movimientoRepository;
    private final ProductoRepository productoRepository;

    public KardexService(MovimientoRepository movimientoRepository,
                         ProductoRepository productoRepository) {
        this.movimientoRepository = movimientoRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional(readOnly = true)
    public KardexResponse obtener(Long productoId, LocalDateTime desde, LocalDateTime hasta) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado"));

        int stockActual = producto.getStock() == null ? 0 : producto.getStock();

        // Saldo anterior al primer asiento del producto: lo que habia en el
        // almacen antes de que el sistema empezara a registrar movimientos.
        int saldoInicial = stockActual - entero(movimientoRepository.obtenerNeto(productoId));

        // El acumulado de la consulta parte del primer asiento que devuelve, no
        // del primero del producto. Si se filtro por fecha, hay que sumarle lo
        // ocurrido antes del rango para que la primera linea arranque bien.
        int saldoBase = saldoInicial
                + (desde == null ? 0 : entero(movimientoRepository.obtenerNetoAntesDe(productoId, desde)));

        List<KardexLineaDTO> filas = movimientoRepository.obtenerKardex(productoId, desde, hasta);
        List<KardexResponse.Linea> lineas = new ArrayList<>(filas.size());

        for (KardexLineaDTO f : filas) {
            boolean esEntrada = TiposMovimiento.ENTRADA.equals(f.getTipo());
            BigDecimal costo = f.getCostoUnitario() == null ? BigDecimal.ZERO : f.getCostoUnitario();
            int cantidad = f.getCantidad() == null ? 0 : f.getCantidad();

            lineas.add(new KardexResponse.Linea(
                    f.getId(),
                    f.getFecha(),
                    f.getTipo(),
                    f.getMotivo(),
                    f.getMotivoNombre(),
                    f.getObservacion(),
                    f.getEstado(),
                    esEntrada ? cantidad : null,
                    esEntrada ? null : cantidad,
                    costo,
                    costo.multiply(BigDecimal.valueOf(cantidad)),
                    saldoBase + (f.getAcumulado() == null ? 0 : f.getAcumulado()),
                    f.getUsuario()));
        }

        return new KardexResponse(
                producto.getId(),
                producto.getSku(),
                producto.getNombre(),
                stockActual,
                producto.getCostoPromedio(),
                producto.valorizado(),
                saldoBase,
                lineas);
    }

    private static int entero(Integer valor) {
        return valor == null ? 0 : valor;
    }
}
