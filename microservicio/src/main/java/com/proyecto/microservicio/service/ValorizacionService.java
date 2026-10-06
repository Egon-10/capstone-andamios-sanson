package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.ValorizacionResponse;
import com.proyecto.microservicio.model.ValorizacionProductoDTO;
import com.proyecto.microservicio.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-17: valorización del inventario por promedio ponderado.
 *
 * El valor de cada producto es su stock por su costo promedio, que la entrada
 * recalcula en cada ingreso. No se valoriza al precio de venta: hacerlo
 * sobreestimaría el inventario en el margen comercial y daría una cifra que no
 * sirve para el balance.
 */
@Service
public class ValorizacionService {

    private static final int ESCALA_VALOR = 2;

    private final ProductoRepository repository;

    public ValorizacionService(ProductoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ValorizacionResponse calcular() {
        List<ValorizacionProductoDTO> filas = repository.obtenerValorizacion();

        BigDecimal valorTotal = BigDecimal.ZERO;
        int unidadesTotales = 0;

        List<ValorizacionResponse.PorProducto> detalle = new ArrayList<>(filas.size());
        Map<String, Acumulador> porCategoria = new LinkedHashMap<>();

        for (ValorizacionProductoDTO f : filas) {
            int stock = f.getStock() == null ? 0 : f.getStock();
            BigDecimal valor = f.getValor() == null ? BigDecimal.ZERO : f.getValor();
            String categoria = f.getCategoria() == null ? "Sin categoria" : f.getCategoria();

            valorTotal = valorTotal.add(valor);
            unidadesTotales += stock;

            Acumulador a = porCategoria.computeIfAbsent(categoria, c -> new Acumulador());
            a.productos++;
            a.unidades += stock;
            a.valor = a.valor.add(valor);

            detalle.add(new ValorizacionResponse.PorProducto(
                    f.getProductoId(),
                    f.getSku(),
                    f.getProducto(),
                    categoria,
                    stock,
                    f.getCostoPromedio(),
                    valor,
                    necesitaReposicion(f)));
        }

        List<ValorizacionResponse.PorCategoria> resumen = new ArrayList<>(porCategoria.size());
        for (Map.Entry<String, Acumulador> e : porCategoria.entrySet()) {
            Acumulador a = e.getValue();
            resumen.add(new ValorizacionResponse.PorCategoria(
                    e.getKey(),
                    a.productos,
                    a.unidades,
                    a.valor,
                    participacion(a.valor, valorTotal)));
        }

        return new ValorizacionResponse(
                valorTotal.setScale(ESCALA_VALOR, RoundingMode.HALF_UP),
                unidadesTotales,
                filas.size(),
                resumen,
                detalle);
    }

    /** HU-20: productos que alcanzaron su umbral de reposición. */
    @Transactional(readOnly = true)
    public List<ValorizacionResponse.PorProducto> porReponer() {
        return repository.obtenerPorReponer().stream()
                .map(f -> new ValorizacionResponse.PorProducto(
                        f.getProductoId(),
                        f.getSku(),
                        f.getProducto(),
                        f.getCategoria() == null ? "Sin categoria" : f.getCategoria(),
                        f.getStock() == null ? 0 : f.getStock(),
                        f.getCostoPromedio(),
                        f.getValor() == null ? BigDecimal.ZERO : f.getValor(),
                        true))
                .toList();
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

    private static boolean necesitaReposicion(ValorizacionProductoDTO f) {
        Integer umbral = f.getPuntoReposicion() != null ? f.getPuntoReposicion() : f.getStockMinimo();
        return umbral != null && f.getStock() != null && f.getStock() <= umbral;
    }

    /** Totales de una categoría mientras se recorre el detalle. */
    private static final class Acumulador {
        private int productos;
        private int unidades;
        private BigDecimal valor = BigDecimal.ZERO;
    }
}
