package com.proyecto.microservicio.service;

import com.proyecto.microservicio.config.ZonaHoraria;
import com.proyecto.microservicio.dto.IndicadoresResponse;
import com.proyecto.microservicio.dto.StockCriticoResponse;
import com.proyecto.microservicio.dto.TendenciaResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.EstadosAjuste;
import com.proyecto.microservicio.model.IndicadoresProductoDTO;
import com.proyecto.microservicio.model.MovimientosDelDiaDTO;
import com.proyecto.microservicio.model.StockCriticoDTO;
import com.proyecto.microservicio.model.TendenciaDiaDTO;
import com.proyecto.microservicio.repository.AjusteInventarioRepository;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HU-23, HU-24 y HU-25: panel de indicadores de gestion del inventario.
 *
 * Las tres consultas excluyen los movimientos anulados y sus compensatorios.
 * El par se cancela en unidades, pero contarlo como actividad inflaria la
 * cantidad de operaciones del dia con algo que en realidad no ocurrio.
 */
@Service
public class IndicadoresService {

    /** Periodo por omision de la tendencia y tope que se admite. */
    static final int DIAS_POR_OMISION = 30;
    static final int DIAS_MAXIMOS = 92;

    public static final String AGOTADO = "AGOTADO";
    public static final String CRITICO = "CRITICO";
    public static final String BAJO = "BAJO";

    private final ProductoRepository productos;
    private final MovimientoRepository movimientos;
    private final AjusteInventarioRepository ajustes;

    public IndicadoresService(ProductoRepository productos, MovimientoRepository movimientos,
                              AjusteInventarioRepository ajustes) {
        this.productos = productos;
        this.movimientos = movimientos;
        this.ajustes = ajustes;
    }

    /** HU-23: cifras del panel, con la hora de corte en que se calcularon. */
    @Transactional(readOnly = true)
    public IndicadoresResponse indicadores() {
        IndicadoresProductoDTO p = productos.obtenerIndicadores();
        LocalDate hoy = ZonaHoraria.ahora().toLocalDate();
        MovimientosDelDiaDTO m = movimientos.obtenerMovimientosDesde(hoy.atStartOfDay());

        return new IndicadoresResponse(
                valor(p == null ? null : p.getProductosActivos()),
                valor(p == null ? null : p.getUnidades()),
                p == null || p.getValor() == null ? BigDecimal.ZERO : p.getValor(),
                valor(p == null ? null : p.getPorReponer()),
                valor(p == null ? null : p.getSinStock()),
                valor(m == null ? null : m.getTotal()),
                valor(m == null ? null : m.getEntradas()),
                valor(m == null ? null : m.getSalidas()),
                ajustes.countByEstado(EstadosAjuste.PENDIENTE),
                ZonaHoraria.ahora());
    }

    /**
     * HU-24: tendencia diaria de entradas y salidas. Sin fechas, cubre los
     * ultimos treinta dias hasta hoy. El periodo se acota a 92 dias, que es un
     * trimestre: mas alla de eso las barras diarias dejan de leerse.
     */
    @Transactional(readOnly = true)
    public TendenciaResponse tendencia(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : ZonaHoraria.ahora().toLocalDate();
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_POR_OMISION - 1L);

        if (inicio.isAfter(fin)) {
            throw new ReglaNegocioException("desde",
                    "La fecha inicial no puede ser posterior a la final");
        }
        long dias = ChronoUnit.DAYS.between(inicio, fin) + 1;
        if (dias > DIAS_MAXIMOS) {
            throw new ReglaNegocioException("desde",
                    "El periodo admite hasta " + DIAS_MAXIMOS + " dias y se pidieron " + dias);
        }

        Map<LocalDate, TendenciaDiaDTO> porDia = new HashMap<>();
        for (TendenciaDiaDTO f : movimientos.obtenerTendencia(
                inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay())) {
            porDia.put(LocalDate.parse(f.getDia()), f);
        }

        List<TendenciaResponse.Dia> serie = new ArrayList<>((int) dias);
        long entradas = 0;
        long salidas = 0;
        long unidadesEntrada = 0;
        long unidadesSalida = 0;

        // Un elemento por dia, tambien para los dias sin movimientos.
        for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
            TendenciaDiaDTO f = porDia.get(d);
            TendenciaResponse.Dia dia = f == null
                    ? new TendenciaResponse.Dia(d, 0, 0, 0, 0)
                    : new TendenciaResponse.Dia(d, valor(f.getEntradas()), valor(f.getSalidas()),
                            valor(f.getUnidadesEntrada()), valor(f.getUnidadesSalida()));
            serie.add(dia);
            entradas += dia.entradas();
            salidas += dia.salidas();
            unidadesEntrada += dia.unidadesEntrada();
            unidadesSalida += dia.unidadesSalida();
        }

        return new TendenciaResponse(inicio, fin, entradas, salidas,
                unidadesEntrada, unidadesSalida, serie);
    }

    /**
     * HU-25: productos que alcanzaron su umbral, del mas urgente al menos
     * urgente. Primero los agotados, despues los criticos y por ultimo los
     * bajos; dentro de cada nivel, el que tiene menos stock respecto de su
     * umbral va primero.
     */
    @Transactional(readOnly = true)
    public List<StockCriticoResponse> stockCritico() {
        return productos.obtenerStockCritico().stream()
                .map(IndicadoresService::convertir)
                .sorted(Comparator
                        .comparingInt((StockCriticoResponse s) -> orden(s.nivel()))
                        .thenComparingDouble(s -> s.umbral() == 0 ? 0 : (double) s.stock() / s.umbral()))
                .toList();
    }

    static StockCriticoResponse convertir(StockCriticoDTO f) {
        int stock = f.getStock() == null ? 0 : f.getStock();
        int umbral = f.getUmbral() == null ? 0 : f.getUmbral();
        Integer reponer = f.getStockMaximo() == null ? null : Math.max(f.getStockMaximo() - stock, 0);

        return new StockCriticoResponse(
                f.getProductoId(),
                f.getSku(),
                f.getProducto(),
                f.getCategoria() == null ? "Sin categoria" : f.getCategoria(),
                stock,
                umbral,
                Math.max(umbral - stock, 0),
                reponer,
                nivel(stock, umbral));
    }

    static String nivel(int stock, int umbral) {
        if (stock <= 0) {
            return AGOTADO;
        }
        // La mitad del umbral se compara en enteros: stock * 2 <= umbral.
        return stock * 2 <= umbral ? CRITICO : BAJO;
    }

    private static int orden(String nivel) {
        if (AGOTADO.equals(nivel)) {
            return 0;
        }
        return CRITICO.equals(nivel) ? 1 : 2;
    }

    private static long valor(Long numero) {
        return numero == null ? 0L : numero;
    }
}
