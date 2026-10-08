package com.proyecto.microservicio.reporte;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * HU-27: dibuja un {@link Reporte} como libro de Excel (.xlsx).
 *
 * Un .xlsx es un ZIP con unos pocos XML de Office Open XML. Se escribe
 * directamente en lugar de agregar Apache POI porque el reporte solo necesita
 * una hoja con texto, números y fechas, y POI suma varios megabytes y su propio
 * historial de vulnerabilidades por algo que se resuelve en esta clase.
 *
 * Decisiones que importan a quien abre el archivo:
 * <ul>
 *   <li>Los números y las fechas se guardan como valores, no como texto, para
 *       que se puedan sumar, filtrar y graficar.</li>
 *   <li>Los textos se guardan como cadenas en línea y nunca como fórmulas. Un
 *       nombre de producto que empiece con "=" no se ejecuta al abrir el
 *       archivo (inyección de fórmulas).</li>
 *   <li>La fila de encabezados queda fija y con autofiltro.</li>
 * </ul>
 */
public final class RenderizadorExcel {

    private static final LocalDate ORIGEN_EXCEL = LocalDate.of(1899, 12, 30);
    private static final DateTimeFormatter EMISION = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int LARGO_MAXIMO_HOJA = 31;

    // Índices de estilo definidos en styles.xml (cellXfs).
    private static final int ESTILO_TITULO = 1;
    private static final int ESTILO_ENCABEZADO = 2;
    private static final int ESTILO_TEXTO = 3;
    private static final int ESTILO_ENTERO = 4;
    private static final int ESTILO_DECIMAL = 5;
    private static final int ESTILO_MONEDA = 6;
    private static final int ESTILO_FECHA = 7;
    private static final int ESTILO_FECHA_HORA = 8;
    private static final int ESTILO_TOTAL_TEXTO = 9;
    private static final int ESTILO_TOTAL_ENTERO = 10;
    private static final int ESTILO_TOTAL_DECIMAL = 11;
    private static final int ESTILO_TOTAL_MONEDA = 12;
    private static final int ESTILO_NOTA = 13;

    public byte[] dibujar(Reporte reporte) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            agregar(zip, "[Content_Types].xml", TIPOS);
            agregar(zip, "_rels/.rels", RELACIONES);
            agregar(zip, "docProps/core.xml", propiedades(reporte));
            agregar(zip, "xl/workbook.xml", libro(reporte));
            agregar(zip, "xl/_rels/workbook.xml.rels", RELACIONES_LIBRO);
            agregar(zip, "xl/styles.xml", ESTILOS);
            agregar(zip, "xl/worksheets/sheet1.xml", hoja(reporte));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel", e);
        }
        return salida.toByteArray();
    }

    // ------------------------------------------------------------------
    // Hoja
    // ------------------------------------------------------------------

    /** Fila (base 1) donde quedan los encabezados de la tabla. */
    static int filaEncabezado(Reporte reporte) {
        // Título, emisión, un renglón por filtro y una fila en blanco.
        return 2 + reporte.filtros().size() + 2;
    }

    private String hoja(Reporte r) {
        List<Reporte.Columna> columnas = r.columnas();
        int encabezado = filaEncabezado(r);
        int ultimaColumna = columnas.size();
        int ultimaFilaDatos = encabezado + Math.max(r.filas().size(), 1);

        StringBuilder x = new StringBuilder(4096 + r.filas().size() * columnas.size() * 40);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
         .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
         .append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
         .append("<sheetViews><sheetView workbookViewId=\"0\">")
         .append("<pane ySplit=\"").append(encabezado).append("\" topLeftCell=\"A").append(encabezado + 1)
         .append("\" activePane=\"bottomLeft\" state=\"frozen\"/>")
         .append("</sheetView></sheetViews>")
         .append("<cols>");
        for (int i = 0; i < columnas.size(); i++) {
            x.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
             .append("\" width=\"").append(Math.max(columnas.get(i).ancho(), columnas.get(i).titulo().length() + 2))
             .append("\" customWidth=\"1\"/>");
        }
        x.append("</cols><sheetData>");

        int fila = 1;
        celdaUnica(x, fila++, r.titulo(), ESTILO_TITULO);
        celdaUnica(x, fila++, "Emitido por " + texto(r.emitidoPor()) + " el " + r.emision().format(EMISION), ESTILO_NOTA);
        for (String filtro : r.filtros()) {
            celdaUnica(x, fila++, filtro, ESTILO_NOTA);
        }
        fila++;

        x.append("<row r=\"").append(fila).append("\">");
        for (int c = 0; c < columnas.size(); c++) {
            celdaTexto(x, ref(c, fila), columnas.get(c).titulo(), ESTILO_ENCABEZADO);
        }
        x.append("</row>");
        fila++;

        if (r.filas().isEmpty()) {
            celdaUnica(x, fila++, "No hay registros para los filtros indicados.", ESTILO_NOTA);
        }
        for (List<Object> valores : r.filas()) {
            x.append("<row r=\"").append(fila).append("\">");
            for (int c = 0; c < columnas.size(); c++) {
                celda(x, ref(c, fila), valores.get(c), columnas.get(c).tipo(), false);
            }
            x.append("</row>");
            fila++;
        }

        if (r.totales() != null && !r.filas().isEmpty()) {
            x.append("<row r=\"").append(fila).append("\">");
            for (int c = 0; c < columnas.size(); c++) {
                celda(x, ref(c, fila), r.totales().get(c), columnas.get(c).tipo(), true);
            }
            x.append("</row>");
            fila++;
        }

        fila++;
        for (String nota : r.notas()) {
            celdaUnica(x, fila++, nota, ESTILO_NOTA);
        }

        x.append("</sheetData>");
        if (!r.filas().isEmpty()) {
            x.append("<autoFilter ref=\"").append(ref(0, encabezado)).append(':')
             .append(ref(ultimaColumna - 1, ultimaFilaDatos)).append("\"/>");
        }
        x.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
         .append("<pageSetup orientation=\"landscape\" paperSize=\"9\" fitToWidth=\"1\" fitToHeight=\"0\"/>")
         .append("</worksheet>");
        return x.toString();
    }

    private static void celdaUnica(StringBuilder x, int fila, String valor, int estilo) {
        x.append("<row r=\"").append(fila).append("\">");
        celdaTexto(x, ref(0, fila), valor, estilo);
        x.append("</row>");
    }

    private static void celda(StringBuilder x, String ref, Object valor, Reporte.Tipo tipo, boolean total) {
        if (valor == null) {
            if (total) {
                // La fila de totales lleva fondo en todas sus celdas.
                x.append("<c r=\"").append(ref).append("\" s=\"").append(ESTILO_TOTAL_TEXTO).append("\"/>");
            }
            return;
        }
        if (valor instanceof Number n && tipo.esNumero()) {
            int estilo = switch (tipo) {
                case ENTERO -> total ? ESTILO_TOTAL_ENTERO : ESTILO_ENTERO;
                case MONEDA -> total ? ESTILO_TOTAL_MONEDA : ESTILO_MONEDA;
                default -> total ? ESTILO_TOTAL_DECIMAL : ESTILO_DECIMAL;
            };
            x.append("<c r=\"").append(ref).append("\" s=\"").append(estilo).append("\"><v>")
             .append(numero(n)).append("</v></c>");
        } else if (valor instanceof LocalDateTime f) {
            x.append("<c r=\"").append(ref).append("\" s=\"").append(ESTILO_FECHA_HORA).append("\"><v>")
             .append(serial(f)).append("</v></c>");
        } else if (valor instanceof LocalDate f) {
            x.append("<c r=\"").append(ref).append("\" s=\"").append(ESTILO_FECHA).append("\"><v>")
             .append(ChronoUnit.DAYS.between(ORIGEN_EXCEL, f)).append("</v></c>");
        } else {
            celdaTexto(x, ref, String.valueOf(valor), total ? ESTILO_TOTAL_TEXTO : ESTILO_TEXTO);
        }
    }

    private static void celdaTexto(StringBuilder x, String ref, String valor, int estilo) {
        x.append("<c r=\"").append(ref).append("\" s=\"").append(estilo)
         .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(valor))
         .append("</t></is></c>");
    }

    /** Referencia A1 de una celda; la columna es base 0 y la fila base 1. */
    static String ref(int columna, int fila) {
        StringBuilder letras = new StringBuilder();
        int n = columna + 1;
        while (n > 0) {
            int resto = (n - 1) % 26;
            letras.insert(0, (char) ('A' + resto));
            n = (n - 1) / 26;
        }
        return letras.append(fila).toString();
    }

    /** Número de serie de Excel: días desde el 30/12/1899 más la fracción del día. */
    static String serial(LocalDateTime f) {
        long dias = ChronoUnit.DAYS.between(ORIGEN_EXCEL, f.toLocalDate());
        double fraccion = f.toLocalTime().toSecondOfDay() / 86400.0;
        return BigDecimal.valueOf(dias + fraccion).stripTrailingZeros().toPlainString();
    }

    private static String numero(Number n) {
        BigDecimal valor = n instanceof BigDecimal b ? b : new BigDecimal(n.toString());
        return valor.stripTrailingZeros().toPlainString();
    }

    /**
     * Escapa los caracteres especiales de XML y descarta los de control, que
     * XML 1.0 no admite y que harían que Excel declare el archivo dañado.
     */
    static String xml(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder s = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '&' -> s.append("&amp;");
                case '<' -> s.append("&lt;");
                case '>' -> s.append("&gt;");
                case '"' -> s.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        s.append(c);
                    }
                }
            }
        }
        return s.toString();
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? "el sistema" : valor;
    }

    /** Nombre de hoja válido: hasta 31 caracteres y sin los símbolos que Excel prohíbe. */
    static String nombreHoja(String titulo) {
        String limpio = titulo == null ? "Reporte" : titulo.replaceAll("[\\\\/?*\\[\\]:]", " ").replaceAll("\\s+", " ").trim();
        if (limpio.isEmpty()) {
            limpio = "Reporte";
        }
        return limpio.length() > LARGO_MAXIMO_HOJA ? limpio.substring(0, LARGO_MAXIMO_HOJA).trim() : limpio;
    }

    // ------------------------------------------------------------------
    // Partes fijas del paquete
    // ------------------------------------------------------------------

    private static void agregar(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String libro(Reporte r) {
        int encabezado = filaEncabezado(r);
        String hoja = nombreHoja(r.titulo());
        StringBuilder x = new StringBuilder()
            .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
            .append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
            .append("<sheets><sheet name=\"").append(xml(hoja)).append("\" sheetId=\"1\" r:id=\"rId1\"/></sheets>");
        if (!r.filas().isEmpty()) {
            String desde = ref(0, encabezado).replaceAll("([A-Z]+)(\\d+)", "\\$$1\\$$2");
            String hasta = ref(r.columnas().size() - 1, encabezado + r.filas().size())
                    .replaceAll("([A-Z]+)(\\d+)", "\\$$1\\$$2");
            x.append("<definedNames><definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"0\" hidden=\"1\">'")
             .append(xml(hoja.replace("'", "''"))).append("'!").append(desde).append(':').append(hasta)
             .append("</definedName></definedNames>");
        }
        return x.append("</workbook>").toString();
    }

    private static String propiedades(Reporte r) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<dc:title>" + xml(r.titulo()) + "</dc:title>"
            + "<dc:creator>Andamios Sansón - Control de inventario</dc:creator>"
            + "</cp:coreProperties>";
    }

    private static final String TIPOS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
        + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
        + "</Types>";

    private static final String RELACIONES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
        + "</Relationships>";

    private static final String RELACIONES_LIBRO =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
        + "</Relationships>";

    /*
     * cellXfs, en este orden:
     *  0 predeterminado        5 decimal #,##0.00       10 total entero
     *  1 título                6 moneda S/ #,##0.00     11 total decimal
     *  2 encabezado            7 fecha dd/mm/aaaa       12 total moneda
     *  3 texto                 8 fecha y hora           13 nota
     *  4 entero #,##0          9 total texto
     */
    private static final String ESTILOS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
        + "<numFmts count=\"3\">"
        + "<numFmt numFmtId=\"164\" formatCode=\"&quot;S/ &quot;#,##0.00\"/>"
        + "<numFmt numFmtId=\"165\" formatCode=\"dd/mm/yyyy\"/>"
        + "<numFmt numFmtId=\"166\" formatCode=\"dd/mm/yyyy hh:mm\"/>"
        + "</numFmts>"
        + "<fonts count=\"5\">"
        + "<font><sz val=\"10\"/><name val=\"Calibri\"/></font>"
        + "<font><b/><sz val=\"14\"/><color rgb=\"FF1F3A5F\"/><name val=\"Calibri\"/></font>"
        + "<font><b/><sz val=\"10\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
        + "<font><b/><sz val=\"10\"/><name val=\"Calibri\"/></font>"
        + "<font><i/><sz val=\"9\"/><color rgb=\"FF555555\"/><name val=\"Calibri\"/></font>"
        + "</fonts>"
        + "<fills count=\"4\">"
        + "<fill><patternFill patternType=\"none\"/></fill>"
        + "<fill><patternFill patternType=\"gray125\"/></fill>"
        + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F3A5F\"/><bgColor indexed=\"64\"/></patternFill></fill>"
        + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE8EDF3\"/><bgColor indexed=\"64\"/></patternFill></fill>"
        + "</fills>"
        + "<borders count=\"2\">"
        + "<border><left/><right/><top/><bottom/><diagonal/></border>"
        + "<border><left/><right/><top style=\"thin\"><color rgb=\"FF1F3A5F\"/></top><bottom/><diagonal/></border>"
        + "</borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"14\">"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
        + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\"/></xf>"
        + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        + "<xf numFmtId=\"4\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
        + "<xf numFmtId=\"165\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\"/></xf>"
        + "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyAlignment=\"1\"><alignment horizontal=\"left\"/></xf>"
        + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
        + "<xf numFmtId=\"3\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
        + "<xf numFmtId=\"4\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
        + "<xf numFmtId=\"164\" fontId=\"3\" fillId=\"3\" borderId=\"1\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"/>"
        + "<xf numFmtId=\"0\" fontId=\"4\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
        + "</cellXfs>"
        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
        + "</styleSheet>";
}
