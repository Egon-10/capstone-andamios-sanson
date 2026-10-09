package com.proyecto.microservicio.reporte;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * HU-26: dibuja un {@link Reporte} como documento PDF.
 *
 * Cada página repite el encabezado de la tabla y lleva al pie el nombre del
 * reporte, quién lo emitió y "Página X de Y", porque un reporte impreso suele
 * separarse y cada hoja debe poder identificarse sola.
 */
public final class RenderizadorPdf {

    private static final Color AZUL = new Color(0x1F, 0x3A, 0x5F);
    private static final Color GRIS_TEXTO = new Color(0x55, 0x55, 0x55);
    private static final Color GRIS_LINEA = new Color(0xD5, 0xDB, 0xE3);
    private static final Color FONDO_ALTERNO = new Color(0xF5, 0xF7, 0xFA);
    private static final Color FONDO_TOTAL = new Color(0xE8, 0xED, 0xF3);

    private static final Font TITULO = new Font(Font.HELVETICA, 14, Font.BOLD, AZUL);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 8, Font.ITALIC, GRIS_TEXTO);
    private static final Font ENCABEZADO = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font CELDA = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
    private static final Font TOTAL = new Font(Font.HELVETICA, 8, Font.BOLD, Color.BLACK);

    /** A partir de este número de columnas la hoja se pone apaisada. */
    private static final int COLUMNAS_PARA_APAISADO = 6;

    public byte[] dibujar(Reporte reporte) {
        Rectangle tamano = reporte.columnas().size() >= COLUMNAS_PARA_APAISADO
                ? PageSize.A4.rotate() : PageSize.A4;
        Document documento = new Document(tamano, 36, 36, 40, 48);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            escritor.setPageEvent(new PiePagina(reporte));
            documento.addTitle(reporte.titulo());
            documento.addCreator("Andamios Sansón - Control de inventario");
            documento.open();

            documento.add(new Paragraph(reporte.titulo(), TITULO));
            documento.add(new Paragraph("Emitido por " + nombre(reporte.emitidoPor()) + " el "
                    + Valores.fechaHora(reporte.emision()), SUBTITULO));
            for (String filtro : reporte.filtros()) {
                documento.add(new Paragraph(filtro, SUBTITULO));
            }

            documento.add(tabla(reporte));

            for (String nota : reporte.notas()) {
                Paragraph p = new Paragraph(nota, SUBTITULO);
                p.setSpacingBefore(4);
                documento.add(p);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
        return salida.toByteArray();
    }

    private static PdfPTable tabla(Reporte r) throws DocumentException {
        List<Reporte.Columna> columnas = r.columnas();
        float[] anchos = new float[columnas.size()];
        for (int i = 0; i < anchos.length; i++) {
            anchos[i] = (float) columnas.get(i).ancho();
        }

        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(10);
        tabla.setHeaderRows(1);

        for (Reporte.Columna c : columnas) {
            PdfPCell celda = new PdfPCell(new Phrase(c.titulo(), ENCABEZADO));
            celda.setBackgroundColor(AZUL);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setPadding(4);
            celda.setHorizontalAlignment(c.tipo().esNumero() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            tabla.addCell(celda);
        }

        if (r.filas().isEmpty()) {
            PdfPCell vacia = new PdfPCell(new Phrase("No hay registros para los filtros indicados.", SUBTITULO));
            vacia.setColspan(columnas.size());
            vacia.setBorder(Rectangle.NO_BORDER);
            vacia.setPadding(6);
            tabla.addCell(vacia);
            return tabla;
        }

        int n = 0;
        for (List<Object> fila : r.filas()) {
            Color fondo = n++ % 2 == 1 ? FONDO_ALTERNO : null;
            for (int c = 0; c < columnas.size(); c++) {
                tabla.addCell(celda(fila.get(c), columnas.get(c).tipo(), CELDA, fondo));
            }
        }

        if (r.totales() != null) {
            for (int c = 0; c < columnas.size(); c++) {
                PdfPCell celda = celda(r.totales().get(c), columnas.get(c).tipo(), TOTAL, FONDO_TOTAL);
                celda.setBorder(Rectangle.TOP);
                celda.setBorderColor(AZUL);
                tabla.addCell(celda);
            }
        }
        return tabla;
    }

    private static PdfPCell celda(Object valor, Reporte.Tipo tipo, Font fuente, Color fondo) {
        PdfPCell celda = new PdfPCell(new Phrase(Valores.texto(valor, tipo), fuente));
        celda.setBorder(Rectangle.BOTTOM);
        celda.setBorderColor(GRIS_LINEA);
        celda.setPadding(3.5f);
        celda.setHorizontalAlignment(tipo.esNumero() ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        if (fondo != null) {
            celda.setBackgroundColor(fondo);
        }
        return celda;
    }

    private static String nombre(String valor) {
        return valor == null || valor.isBlank() ? "el sistema" : valor;
    }

    /** Pie de cada página con el total de páginas, que solo se conoce al cerrar. */
    private static final class PiePagina extends PdfPageEventHelper {

        private final Reporte reporte;
        private PdfTemplate totalPaginas;
        private BaseFont fuente;

        PiePagina(Reporte reporte) {
            this.reporte = reporte;
        }

        @Override
        public void onOpenDocument(PdfWriter escritor, Document documento) {
            totalPaginas = escritor.getDirectContent().createTemplate(30, 12);
            try {
                fuente = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("No se pudo cargar la fuente del pie de página", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            PdfContentByte lienzo = escritor.getDirectContent();
            float y = documento.bottom() - 22;

            ColumnText.showTextAligned(lienzo, Element.ALIGN_LEFT,
                    new Phrase("Andamios Sansón · " + reporte.titulo(), SUBTITULO),
                    documento.left(), y, 0);

            String pagina = "Página " + escritor.getPageNumber() + " de ";
            float ancho = fuente.getWidthPoint(pagina, 8);
            float x = documento.right() - 30;
            lienzo.beginText();
            lienzo.setFontAndSize(fuente, 8);
            lienzo.setColorFill(GRIS_TEXTO);
            lienzo.setTextMatrix(x - ancho, y);
            lienzo.showText(pagina);
            lienzo.endText();
            lienzo.addTemplate(totalPaginas, x, y);
        }

        @Override
        public void onCloseDocument(PdfWriter escritor, Document documento) {
            totalPaginas.beginText();
            totalPaginas.setFontAndSize(fuente, 8);
            totalPaginas.setColorFill(GRIS_TEXTO);
            totalPaginas.setTextMatrix(0, 0);
            totalPaginas.showText(String.valueOf(escritor.getPageNumber() - 1));
            totalPaginas.endText();
        }
    }
}
