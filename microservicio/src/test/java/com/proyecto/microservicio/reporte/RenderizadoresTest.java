package com.proyecto.microservicio.reporte;

import com.lowagie.text.pdf.PdfReader;
import com.proyecto.microservicio.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CP-27 (exportación a PDF, HU-26) y CP-28 (exportación a Excel, HU-27).
 */
class RenderizadoresTest {

    private static final LocalDateTime EMISION = LocalDateTime.of(2026, 10, 8, 14, 30);

    private static Reporte reporte(int filas) {
        List<List<Object>> datos = new ArrayList<>();
        for (int i = 0; i < filas; i++) {
            datos.add(Arrays.asList(LocalDateTime.of(2026, 10, 1, 9, 30), "Producto " + i, 10 + i,
                    new BigDecimal("280.50")));
        }
        return new Reporte("Movimientos de inventario", List.of("Periodo: octubre"),
                List.of(Reporte.fechaHora("Fecha"), Reporte.texto("Producto", 20), Reporte.entero("Cantidad"),
                        Reporte.moneda("Importe")),
                datos, Arrays.asList("Total", null, 99, new BigDecimal("1000.00")),
                List.of("Nota de prueba"), "Ana Pérez", EMISION);
    }

    private static Map<String, String> descomprimir(byte[] xlsx) throws IOException {
        Map<String, String> partes = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                partes.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return partes;
    }

    // --- PDF ---

    @Test
    @DisplayName("CP-27: genera un PDF válido con varias páginas cuando hay muchas filas")
    void pdfConVariasPaginas() throws IOException {
        byte[] pdf = new RenderizadorPdf().dibujar(reporte(200));

        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        PdfReader lector = new PdfReader(pdf);
        try {
            assertTrue(lector.getNumberOfPages() > 1, "200 filas no caben en una hoja");
        } finally {
            lector.close();
        }
    }

    @Test
    @DisplayName("CP-27: un reporte sin filas también produce un PDF")
    void pdfVacio() throws IOException {
        Reporte vacio = new Reporte("Vacío", List.of(), List.of(Reporte.texto("A", 5)), List.of(), null,
                null, null, EMISION);
        PdfReader lector = new PdfReader(new RenderizadorPdf().dibujar(vacio));
        try {
            assertEquals(1, lector.getNumberOfPages());
        } finally {
            lector.close();
        }
    }

    // --- Excel ---

    @Test
    @DisplayName("CP-28: el libro contiene las partes que Excel exige")
    void excelTieneLasPartes() throws IOException {
        Map<String, String> partes = descomprimir(new RenderizadorExcel().dibujar(reporte(3)));

        assertTrue(partes.containsKey("[Content_Types].xml"));
        assertTrue(partes.containsKey("_rels/.rels"));
        assertTrue(partes.containsKey("xl/workbook.xml"));
        assertTrue(partes.containsKey("xl/styles.xml"));
        assertTrue(partes.get("xl/workbook.xml").contains("name=\"Movimientos de inventario\""));
    }

    @Test
    @DisplayName("CP-28: los números se guardan como números y la cabecera queda fija con autofiltro")
    void excelNumerosYEncabezado() throws IOException {
        String hoja = descomprimir(new RenderizadorExcel().dibujar(reporte(3))).get("xl/worksheets/sheet1.xml");

        // Encabezado en la fila 5: título, emisión, un filtro y una fila en blanco.
        assertEquals(5, RenderizadorExcel.filaEncabezado(reporte(3)));
        assertTrue(hoja.contains("<pane ySplit=\"5\" topLeftCell=\"A6\""));
        assertTrue(hoja.contains("<autoFilter ref=\"A5:D8\"/>"));
        // La cantidad 10 de la primera fila es un valor numérico, no texto.
        assertTrue(hoja.contains("<c r=\"C6\" s=\"4\"><v>10</v></c>"));
        assertTrue(hoja.contains("<c r=\"D6\" s=\"6\"><v>280.5</v></c>"));
    }

    @Test
    @DisplayName("CP-28: un texto que parece fórmula se guarda como texto (sin inyección de fórmulas)")
    void excelNoEjecutaFormulas() throws IOException {
        Reporte r = new Reporte("Prueba", List.of(), List.of(Reporte.texto("Producto", 20)),
                List.of(List.<Object>of("=HYPERLINK(\"http://x\")")), null, null, null, EMISION);

        String hoja = descomprimir(new RenderizadorExcel().dibujar(r)).get("xl/worksheets/sheet1.xml");

        assertFalse(hoja.contains("<f>"), "No debe haber fórmulas en el libro");
        assertTrue(hoja.contains("t=\"inlineStr\"><is><t xml:space=\"preserve\">=HYPERLINK(&quot;http://x&quot;)</t>"));
    }

    @Test
    @DisplayName("CP-28: escapa XML y descarta caracteres de control")
    void escapaXml() {
        assertEquals("a &lt;b&gt; &amp; &quot;c&quot;", RenderizadorExcel.xml("a <b> & \"c\"\u0001"));
        assertEquals("", RenderizadorExcel.xml(null));
    }

    @Test
    @DisplayName("Calcula referencias de columna más allá de la Z")
    void referencias() {
        assertEquals("A1", RenderizadorExcel.ref(0, 1));
        assertEquals("Z3", RenderizadorExcel.ref(25, 3));
        assertEquals("AA3", RenderizadorExcel.ref(26, 3));
        assertEquals("AZ10", RenderizadorExcel.ref(51, 10));
    }

    @Test
    @DisplayName("Convierte fechas al número de serie de Excel")
    void serialDeFecha() {
        // 01/01/2026 = 46023 en Excel.
        assertEquals("46023", RenderizadorExcel.serial(LocalDate.of(2026, 1, 1).atStartOfDay()));
        assertEquals("46023.5", RenderizadorExcel.serial(LocalDateTime.of(2026, 1, 1, 12, 0)));
    }

    @Test
    @DisplayName("El nombre de la hoja respeta las reglas de Excel")
    void nombreDeHoja() {
        assertEquals("Kardex de A B", RenderizadorExcel.nombreHoja("Kardex de A/[B]"));
        assertEquals(31, RenderizadorExcel.nombreHoja("x".repeat(40)).length());
        assertEquals("Reporte", RenderizadorExcel.nombreHoja(":::"));
    }

    // --- Modelo y formatos ---

    @Test
    @DisplayName("Rechaza filas que no coinciden con las columnas")
    void filasInconsistentes() {
        List<Reporte.Columna> columnas = List.of(Reporte.texto("A", 5), Reporte.texto("B", 5));
        List<List<Object>> filas = List.of(List.<Object>of("solo una"));
        assertThrows(IllegalArgumentException.class,
                () -> new Reporte("X", null, columnas, filas, null, null, null, EMISION));
    }

    @Test
    @DisplayName("Interpreta el formato pedido y rechaza uno desconocido")
    void formato() {
        assertEquals(Formato.PDF, Formato.desde(null));
        assertEquals(Formato.PDF, Formato.desde("PDF"));
        assertEquals(Formato.EXCEL, Formato.desde("xlsx"));
        assertEquals(Formato.EXCEL, Formato.desde("excel"));
        assertThrows(ReglaNegocioException.class, () -> Formato.desde("docx"));
    }

    @Test
    @DisplayName("Formatea importes, enteros y fechas para leerlos impresos")
    void valores() {
        assertEquals("S/ 1,234.50", Valores.moneda(new BigDecimal("1234.5")));
        assertEquals("1,250", Valores.entero(1250));
        assertEquals("08/10/2026 14:30", Valores.fechaHora(EMISION));
        assertEquals("08/10/2026", Valores.fecha(EMISION.toLocalDate()));
        assertEquals("", Valores.texto(null, Reporte.Tipo.TEXTO));
        assertEquals("12.30", Valores.texto(new BigDecimal("12.3"), Reporte.Tipo.DECIMAL));
    }
}
