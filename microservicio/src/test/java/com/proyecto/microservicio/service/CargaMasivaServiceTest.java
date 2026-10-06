package com.proyecto.microservicio.service;

import com.proyecto.microservicio.dto.CargaMasivaResponse;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import com.proyecto.microservicio.model.Categoria;
import com.proyecto.microservicio.model.Producto;
import com.proyecto.microservicio.model.Proveedor;
import com.proyecto.microservicio.repository.CategoriaRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** CP-20: carga masiva de productos con validacion por lotes. */
class CargaMasivaServiceTest {

    private static final String CABECERA =
            "sku,nombre,descripcion,precio,stock,stockMinimo,puntoReposicion,stockMaximo,categoria,proveedor";

    private ProductoRepository productos;
    private CargaMasivaService servicio;

    @BeforeEach
    void preparar() {
        productos = mock(ProductoRepository.class);
        CategoriaRepository categorias = mock(CategoriaRepository.class);
        ProveedorRepository proveedores = mock(ProveedorRepository.class);

        when(categorias.findAll()).thenReturn(List.of(
                new Categoria(1L, "Andamios"),
                new Categoria(2L, "Ruedas")));

        Proveedor p = new Proveedor();
        p.setId(1L);
        p.setNombre("Aceros del Norte");
        when(proveedores.findAll()).thenReturn(List.of(p));

        servicio = new CargaMasivaService(productos, categorias, proveedores,
                mock(AuditoriaService.class));
    }

    private static String archivo(String... filas) {
        return CABECERA + "\n" + String.join("\n", filas);
    }

    @Test
    @DisplayName("CP-20: carga las filas validas y devuelve los SKU cargados")
    void cargaValida() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco de 1.50 m,Galvanizado,300,50,10,15,200,Andamios,Aceros del Norte",
                "RUE-200,Rueda de nylon,,92,40,10,,,Ruedas,"), null, false, 7L);

        assertEquals(2, r.filasLeidas());
        assertEquals(2, r.aceptadas());
        assertEquals(0, r.rechazadas());
        assertTrue(r.seGuardo());
        assertEquals(List.of("AND-100", "RUE-200"), r.skusCargados());
        verify(productos).saveAll(anyList());
    }

    @Test
    @DisplayName("CP-20: el modo de por omision no guarda nada si una fila falla")
    void todoONada() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco de 1.50 m,,300,50,10,,,Andamios,",
                "AND-101,Sin categoria valida,,300,50,,,,Inexistente,"), null, false, 7L);

        assertEquals(CargaMasivaService.MODO_TODO_O_NADA, r.modo());
        assertEquals(1, r.aceptadas());
        assertEquals(1, r.rechazadas());
        assertFalse(r.seGuardo());
        assertTrue(r.skusCargados().isEmpty());
        verify(productos, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("CP-20: el modo parcial guarda las filas validas e informa las demas")
    void modoParcial() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco de 1.50 m,,300,50,10,,,Andamios,",
                "AND-101,Sin categoria valida,,300,50,,,,Inexistente,"), "PARCIAL", false, 7L);

        assertEquals(CargaMasivaService.MODO_PARCIAL, r.modo());
        assertEquals(1, r.aceptadas());
        assertTrue(r.seGuardo());
        verify(productos).saveAll(anyList());
    }

    @Test
    @DisplayName("CP-20: la simulacion valida el archivo sin guardar nada")
    void simulacion() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco de 1.50 m,,300,50,10,,,Andamios,"), null, true, 7L);

        assertTrue(r.simulacion());
        assertEquals(1, r.aceptadas());
        assertFalse(r.seGuardo());
        verify(productos, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("CP-20: el informe nombra la fila tal como se ve en Excel")
    void numeroDeFila() {
        // La cabecera es la fila 1, de modo que la primera de datos es la 2.
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,300,50,,,,Andamios,",
                ",Sin SKU,,300,50,,,,Andamios,"), "PARCIAL", false, 7L);

        assertEquals(1, r.errores().size());
        assertEquals(3, r.errores().get(0).fila());
        assertTrue(r.errores().get(0).motivo().contains("SKU"));
    }

    @Test
    @DisplayName("CP-20: rechaza un SKU repetido dentro del mismo archivo")
    void skuRepetidoEnElArchivo() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,300,50,,,,Andamios,",
                "and-100,Marco otra vez,,300,50,,,,Andamios,"), "PARCIAL", false, 7L);

        assertEquals(1, r.rechazadas());
        assertTrue(r.errores().get(0).motivo().contains("repetido"));
    }

    @Test
    @DisplayName("CP-20: rechaza un SKU que ya existe en el catalogo")
    void skuYaExistente() {
        when(productos.existsBySkuIgnoreCase("AND-100")).thenReturn(true);

        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,300,50,,,,Andamios,"), "PARCIAL", false, 7L);

        assertEquals(1, r.rechazadas());
        assertTrue(r.errores().get(0).motivo().contains("ya existe"));
    }

    @Test
    @DisplayName("CP-20: no crea la categoria que no existe, la rechaza")
    void categoriaInexistente() {
        // Crear una categoria a partir de un nombre mal escrito ensuciaria el
        // catalogo maestro sin que nadie lo note.
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,300,50,,,,Andamiios,"), "PARCIAL", false, 7L);

        assertEquals(1, r.rechazadas());
        assertTrue(r.errores().get(0).motivo().contains("no existe"));
    }

    @Test
    @DisplayName("CP-20: compara la categoria sin distinguir tildes ni mayusculas")
    void categoriaConTildesYMayusculas() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,300,50,,,,ANDAMIOS,"), "PARCIAL", false, 7L);

        assertEquals(0, r.rechazadas());
        assertEquals("andamios", CargaMasivaService.clave(" Andámios "));
    }

    @Test
    @DisplayName("CP-20: acepta el decimal escrito con coma, como lo exporta Excel en espanol")
    void decimalConComa() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,\"310,50\",50,,,,Andamios,"), "PARCIAL", false, 7L);

        assertEquals(1, r.aceptadas());
        assertEquals(0, r.rechazadas());
    }

    @Test
    @DisplayName("CP-20: rechaza un precio que no es numero y un stock negativo")
    void camposNoNumericos() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,abc,50,,,,Andamios,",
                "AND-101,Cruceta,,300,-5,,,,Andamios,"), "PARCIAL", false, 7L);

        assertEquals(2, r.rechazadas());
        assertTrue(r.errores().get(0).motivo().contains("no es un numero"));
        assertTrue(r.errores().get(1).motivo().contains("negativo"));
    }

    @Test
    @DisplayName("CP-20: rechaza un stock maximo menor que el minimo")
    void maximoMenorQueElMinimo() {
        CargaMasivaResponse r = servicio.cargar(archivo(
                "AND-100,Marco,,300,50,100,,20,Andamios,"), "PARCIAL", false, 7L);

        assertEquals(1, r.rechazadas());
        assertTrue(r.errores().get(0).motivo().contains("maximo"));
    }

    @Test
    @DisplayName("CP-20: avisa que faltan columnas obligatorias en la cabecera")
    void cabeceraIncompleta() {
        ReglaNegocioException ex = assertThrows(ReglaNegocioException.class,
                () -> servicio.cargar("sku,nombre\nAND-100,Marco", null, false, 7L));

        assertEquals("archivo", ex.getCampo());
        assertTrue(ex.getMessage().contains("precio"));
        assertTrue(ex.getMessage().contains("categoria"));
    }

    @Test
    @DisplayName("CP-20: rechaza el archivo vacio y el que solo trae cabecera")
    void archivoSinDatos() {
        assertThrows(ReglaNegocioException.class,
                () -> servicio.cargar("", null, false, 7L));
        assertThrows(ReglaNegocioException.class,
                () -> servicio.cargar(CABECERA, null, false, 7L));
    }

    @Test
    @DisplayName("HU-17: el costo promedio inicial arranca en el precio informado")
    void costoPromedioInicial() {
        // Se recoge lo que llega a saveAll con una respuesta en lugar de un
        // ArgumentCaptor, porque capturar una lista con genericos obliga a un
        // casteo sin comprobacion que el compilador no puede verificar.
        List<Producto> guardados = new java.util.ArrayList<>();
        when(productos.saveAll(anyList())).thenAnswer(i -> {
            guardados.addAll(i.getArgument(0));
            return i.getArgument(0);
        });

        servicio.cargar(archivo(
                "AND-100,Marco,,300,50,,,,Andamios,"), null, false, 7L);

        assertEquals(1, guardados.size());
        Producto guardado = guardados.get(0);
        assertEquals(0, new java.math.BigDecimal("300.0").compareTo(guardado.getCostoPromedio()));
        assertEquals("AND-100", guardado.getSku());
        assertEquals(50, guardado.getStock());
        assertEquals("Andamios", guardado.getCategoria().getNombre());
        assertTrue(guardado.isActivo());
    }

    @Test
    @DisplayName("CP-20: las columnas se ubican por nombre, no por posicion")
    void ordenDeColumnasIndiferente() {
        String contenido = "nombre,categoria,sku,stock,precio\n"
                + "Marco de 1.50 m,Andamios,AND-100,50,300";

        CargaMasivaResponse r = servicio.cargar(contenido, null, false, 7L);

        assertEquals(1, r.aceptadas());
        assertEquals(List.of("AND-100"), r.skusCargados());
    }
}
