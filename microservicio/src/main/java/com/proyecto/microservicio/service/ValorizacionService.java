package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.ValorizacionResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.ValorizacionCorteDTO;
import com.proyecto.microservicio.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-17: valorización del inventario por promedio ponderado.
 * HU-28: valorización con corte a una fecha.
 *
 * El valor de cada producto es su stock por su costo promedio, que la entrada
 * recalcula en cada ingreso. No se valoriza al precio de venta: hacerlo
 * sobreestimaría el inventario en el margen comercial y daría una cifra que no
 * sirve para el balance.
 */
@Service
public class ValorizacionService {

    private static final int ESCALA_VALOR = 2;
    private static final String SIN_CATEGORIA = "Sin categoria";

    private final ProductoRepository repository;

    public ValorizacionService(ProductoRepository repository) {
        this.repository = repository;
    }

    /** Valorización con el stock y el costo vigentes. */
    @Transactional(readOnly = true)
    public ValorizacionResponse calcular() {
        return vigente();
    }

    private ValorizacionResponse vigente() {
        List<Fila> filas = repository.obtenerValorizacion().stream()
                .map(f -> new Fila(f.getProductoId(), f.getSku(), f.getProducto(), f.getCategoria(),
                        entero(f.getStock()), f.getCostoPromedio(), false,
                        necesitaReposicion(f.getStock(), f.getPuntoReposicion(), f.getStockMinimo())))
                .toList();
        return armar(filas, null);
    }

    /**
     * HU-28: valorización al cierre de un día pasado.
     *
     * El stock se reconstruye desde el stock actual restando los movimientos
     * posteriores al corte. El costo es el que dejó el último movimiento
     * anterior al corte. Si ese movimiento es anterior al Sprint 3 no guardó su
     * costo: en ese caso se usa el costo vigente, que es exacto cuando no hubo
     * movimientos después del corte, y en otro caso se informa como estimado
     * en lugar de presentar una cifra inventada como si fuera exacta.
     */
    @Transactional(readOnly = true)
    public ValorizacionResponse calcularAlCorte(LocalDate fecha) {
        LocalDate hoy = ZonaHoraria.hoy();
        if (fecha == null || !fecha.isBefore(hoy)) {
            // El día en curso todavía no cerró: su valorización es la actual.
            if (fecha != null && fecha.isAfter(hoy)) {
                throw new ReglaNegocioException("fecha", "La fecha de corte no puede ser posterior a hoy");
            }
            return vigente();
        }

        List<Fila> filas = new ArrayList<>();
        for (ValorizacionCorteDTO f : repository.obtenerValorizacionAlCorte(fecha.plusDays(1).atStartOfDay())) {
            boolean conocido = f.getCostoHistorico() != null;
            boolean sinCambiosDespues = f.getMovimientosPosteriores() == null || f.getMovimientosPosteriores() == 0;
            BigDecimal costo = conocido ? f.getCostoHistorico() : f.getCostoVigente();
            filas.add(new Fila(f.getProductoId(), f.getSku(), f.getProducto(), f.getCategoria(),
                    entero(f.getStock()), costo, !conocido && !sinCambiosDespues,
                    necesitaReposicion(f.getStock(), f.getPuntoReposicion(), f.getStockMinimo())));
        }
        return armar(filas, fecha);
    }

    /** HU-20: productos que alcanzaron su umbral de reposición. */
    @Transactional(readOnly = true)
    public List<ValorizacionResponse.PorProducto> porReponer() {
        return repository.obtenerPorReponer().stream()
                .map(f -> new ValorizacionResponse.PorProducto(
                        f.getProductoId(),
                        f.getSku(),
                        f.getProducto(),
                        categoria(f.getCategoria()),
                        entero(f.getStock()),
                        f.getCostoPromedio(),
                        f.getValor() == null ? BigDecimal.ZERO : f.getValor(),
                        true,
                        false))
                .toList();
    }

    private ValorizacionResponse armar(List<Fila> filas, LocalDate corte) {
        BigDecimal valorTotal = BigDecimal.ZERO;
        int unidadesTotales = 0;
        int estimados = 0;

        List<ValorizacionResponse.PorProducto> detalle = new ArrayList<>(filas.size());
        Map<String, Acumulador> porCategoria = new LinkedHashMap<>();

        for (Fila f : filas) {
            BigDecimal valor = valor(f.stock(), f.costo());
            String categoria = categoria(f.categoria());

            valorTotal = valorTotal.add(valor);
            unidadesTotales += f.stock();
            if (f.costoEstimado()) {
                estimados++;
            }

            Acumulador a = porCategoria.computeIfAbsent(categoria, c -> new Acumulador());
            a.productos++;
            a.unidades += f.stock();
            a.valor = a.valor.add(valor);

            detalle.add(new ValorizacionResponse.PorProducto(
                    f.productoId(), f.sku(), f.producto(), categoria, f.stock(),
                    f.costo(), valor, f.necesitaReposicion(), f.costoEstimado()));
        }

        List<ValorizacionResponse.PorCategoria> resumen = new ArrayList<>(porCategoria.size());
        for (Map.Entry<String, Acumulador> e : porCategoria.entrySet()) {
            Acumulador a = e.getValue();
            resumen.add(new ValorizacionResponse.PorCategoria(
                    e.getKey(), a.productos, a.unidades, a.valor, participacion(a.valor, valorTotal)));
        }

        return new ValorizacionResponse(
                valorTotal.setScale(ESCALA_VALOR, RoundingMode.HALF_UP),
                unidadesTotales,
                filas.size(),
                resumen,
                detalle,
                corte,
                estimados);
    }

    /** Stock por costo, redondeado a céntimos. Un costo desconocido vale cero. */
    static BigDecimal valor(int stock, BigDecimal costo) {
        BigDecimal c = costo == null ? BigDecimal.ZERO : costo;
        return c.multiply(BigDecimal.valueOf(stock)).setScale(ESCALA_VALOR, RoundingMode.HALF_UP);
    }

    /**
     * Porcentaje que representa una categoría sobre el total. Si el inventario
     * vale cero no se divide: se devuelve cero en lugar de fallar.
     */
    static BigDecimal participacion(BigDecimal valor, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(ESCALA_VALOR);
        }
        return valor.multiply(BigDecimal.valueOf(100))
                .divide(total, ESCALA_VALOR, RoundingMode.HALF_UP);
    }

    static boolean necesitaReposicion(Integer stock, Integer puntoReposicion, Integer stockMinimo) {
        Integer umbral = puntoReposicion != null ? puntoReposicion : stockMinimo;
        return umbral != null && stock != null && stock <= umbral;
    }

    private static String categoria(String categoria) {
        return categoria == null ? SIN_CATEGORIA : categoria;
    }

    private static int entero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    /** Producto ya resuelto, venga de la valorización actual o de la del corte. */
    private record Fila(Long productoId, String sku, String producto, String categoria,
                        int stock, BigDecimal costo, boolean costoEstimado, boolean necesitaReposicion) {
    }

    /** Totales de una categoría mientras se recorre el detalle. */
    private static final class Acumulador {
        private int productos;
        private int unidades;
        private BigDecimal valor = BigDecimal.ZERO;
    }
}
