package com.proyecto.microservicio.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.proyecto.microservicio.model.Auditoria;
import com.proyecto.microservicio.model.MovimientoDTO;
import com.proyecto.microservicio.model.Usuario;
import com.proyecto.microservicio.repository.AuditoriaRepository;
import com.proyecto.microservicio.repository.MovimientoRepository;
import com.proyecto.microservicio.repository.ProductoRepository;
import com.proyecto.microservicio.repository.UsuarioRepository;

import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReporteService {
        private final UsuarioRepository usuarioRepository;
private final AuditoriaRepository auditoriaRepository;
    private final MovimientoRepository movimientoRepository;
private final ProductoRepository productoRepository;
public ReporteService(
        MovimientoRepository movimientoRepository,
        ProductoRepository productoRepository,
        AuditoriaRepository auditoriaRepository,
        UsuarioRepository usuarioRepository
) {

    this.movimientoRepository =
            movimientoRepository;

    this.productoRepository =
            productoRepository;

    this.auditoriaRepository =
            auditoriaRepository;

    this.usuarioRepository =
            usuarioRepository;
}

    /*
     * Encabezados de la tabla
     */
    private PdfPCell encabezado(
            String texto
    ){

        Font fuente =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        12,
                        Color.WHITE
                );

        PdfPCell cell =
                new PdfPCell(
                        new Phrase(
                                texto,
                                fuente
                        )
                );

        cell.setBackgroundColor(
                new Color(
                        52,
                        73,
                        94
                )
        );

        cell.setHorizontalAlignment(
                Element.ALIGN_CENTER
        );

        cell.setVerticalAlignment(
                Element.ALIGN_MIDDLE
        );

        cell.setPadding(8);

        return cell;
    }

    public ByteArrayInputStream generarReporteMovimientos(
            String tipo,
            String fechaInicio,
            String fechaFin
    ) {

        LocalDateTime inicio = null;
        LocalDateTime fin = null;

        /*
         * Conversión de fechas
         */

        if(
                fechaInicio != null &&
                !fechaInicio.isEmpty()
        ){

            inicio =
                    LocalDate
                            .parse(fechaInicio)
                            .atStartOfDay();
        }

        if(
                fechaFin != null &&
                !fechaFin.isEmpty()
        ){

            fin =
                    LocalDate
                            .parse(fechaFin)
                            .atTime(
                                    23,
                                    59,
                                    59
                            );
        }

        String filtroTipo =
                tipo.equals("TODOS")
                        ? null
                        : tipo;

        List<MovimientoDTO> movimientos =
                movimientoRepository
                        .buscarConFiltros(
                                inicio,
                                fin,
                                filtroTipo
                        );

        Document document =
                new Document(
                        PageSize.A4.rotate()
                );

        ByteArrayOutputStream out =
                new ByteArrayOutputStream();

        try {

            PdfWriter.getInstance(
                    document,
                    out
            );

            document.open();

            /*
             * LOGO
             */

            try {

                Image logo =
                        Image.getInstance(
                                getClass()
                                        .getResourceAsStream(
                                                "/static/logoempresa.png"
                                        )
                                        .readAllBytes()
                        );

                logo.scaleToFit(
                        100,
                        100
                );

                logo.setAlignment(
                        Element.ALIGN_CENTER
                );

                logo.setSpacingAfter(
                        15f
                );

                document.add(
                        logo
                );

            } catch (Exception e) {

                System.out.println(
                        "No se encontró el logo."
                );
            }

            /*
             * TITULO
             */

            Font titulo =
                    FontFactory.getFont(
                            FontFactory.HELVETICA_BOLD,
                            20
                    );

            Paragraph p =
                    new Paragraph(
                            "REPORTE DE MOVIMIENTOS",
                            titulo
                    );

            p.setAlignment(
                    Element.ALIGN_CENTER
            );

            document.add(
                    p
            );

            document.add(
                    new Paragraph(" ")
            );

            /*
             * Información del reporte
             */

            document.add(
                    new Paragraph(
                            "Fecha de generación: "
                                    + LocalDate.now()
                    )
            );

            document.add(
                    new Paragraph(
                            "Tipo de reporte: "
                                    + tipo
                    )
            );

            if(inicio != null){

                document.add(
                        new Paragraph(
                                "Desde: "
                                        + inicio.toLocalDate()
                        )
                );
            }

            if(fin != null){

                document.add(
                        new Paragraph(
                                "Hasta: "
                                        + fin.toLocalDate()
                        )
                );
            }

            document.add(
                    new Paragraph(
                            "Total de movimientos encontrados: "
                                    + movimientos.size()
                    )
            );

            document.add(
                    new Paragraph(" ")
            );

            /*
             * Tabla
             */

            PdfPTable tabla =
                    new PdfPTable(6);

            tabla.setWidthPercentage(
                    100
            );

            tabla.setWidths(
                    new float[]{
                            1.2f,
                            3f,
                            2f,
                            2f,
                            3f,
                            4f
                    }
            );

            tabla.addCell(
                    encabezado("ID")
            );

            tabla.addCell(
                    encabezado("Producto")
            );

            tabla.addCell(
                    encabezado("Tipo")
            );

            tabla.addCell(
                    encabezado("Cantidad")
            );

            tabla.addCell(
                    encabezado("Usuario")
            );

            tabla.addCell(
                    encabezado("Fecha")
            );

            /*
             * Datos
             */

            for(
                    MovimientoDTO m :
                    movimientos
            ){

                tabla.addCell(
                        String.valueOf(
                                m.getId()
                        )
                );

                tabla.addCell(
                        m.getProducto()
                );

                tabla.addCell(
                        m.getTipo()
                );

                tabla.addCell(
                        String.valueOf(
                                m.getCantidad()
                        )
                );

                tabla.addCell(
                        m.getUsuario()
                );

                tabla.addCell(
                        m.getFecha()
                                .toString()
                );
            }

            document.add(
                    tabla
            );

            document.close();

        } catch (Exception e) {

            e.printStackTrace();
        }

        return new ByteArrayInputStream(
                out.toByteArray()
        );
    }

   public ByteArrayInputStream generarReporteProductos(
        String categoria
) {
    if(
    categoria != null &&
    categoria.isBlank()
){
    categoria = null;
}

List<Object[]> productos =
        productoRepository
                .obtenerReporteProductos(
                        categoria
                );

    Document document =
            new Document();

    ByteArrayOutputStream out =
            new ByteArrayOutputStream();

    try {

        PdfWriter.getInstance(
                document,
                out
        );

        document.open();

        Image logo =
                Image.getInstance(
                        getClass()
                        .getResourceAsStream(
                                "/static/logoempresa.png"
                        )
                        .readAllBytes()
                );

        logo.scaleToFit(
                100,
                100
        );

        logo.setAlignment(
                Element.ALIGN_CENTER
        );

        document.add(logo);

        Font titulo =
                FontFactory.getFont(
                        FontFactory.HELVETICA_BOLD,
                        18
                );

        Paragraph p =
                new Paragraph(
                        "REPORTE DE PRODUCTOS",
                        titulo
                );

        p.setAlignment(
                Element.ALIGN_CENTER
        );

        document.add(p);

        document.add(
                new Paragraph(
                        "Fecha de generación: "
                        + LocalDate.now()
                )
        );

        document.add(
                new Paragraph(
                        "Total de productos: "
                        + productos.size()
                )
        );

        document.add(
                new Paragraph(" ")
        );

        PdfPTable tabla =
                new PdfPTable(6);

        tabla.setWidthPercentage(100);

        tabla.addCell(
                encabezado("ID")
        );

        tabla.addCell(
                encabezado("Producto")
        );

        tabla.addCell(
                encabezado("Categoría")
        );

        tabla.addCell(
                encabezado("Proveedor")
        );

        tabla.addCell(
                encabezado("Stock")
        );

        tabla.addCell(
                encabezado("Stock mínimo")
        );

        for(Object[] pdt : productos){

            tabla.addCell(
                    pdt[0].toString()
            );

            tabla.addCell(
                    pdt[1].toString()
            );

            tabla.addCell(
                    pdt[2].toString()
            );

            tabla.addCell(
                    pdt[3].toString()
            );

            tabla.addCell(
                    pdt[4].toString()
            );

            tabla.addCell(
                    pdt[5].toString()
            );
        }

        document.add(tabla);

        document.close();

    } catch(Exception e){

        e.printStackTrace();
    }

    return new ByteArrayInputStream(
            out.toByteArray()
    );
}

public ByteArrayInputStream generarReporteStockCritico(
        String categoria
) {

    if(
    categoria != null &&
    categoria.isBlank()
){
    categoria = null;
}

List<Object[]> productos =
        productoRepository
                .obtenerStockCritico(
                        categoria
                );
    Document document =
            new Document();

    ByteArrayOutputStream out =
            new ByteArrayOutputStream();

    try {

        PdfWriter.getInstance(
                document,
                out
        );

        document.open();

        Paragraph titulo =
                new Paragraph(
                        "REPORTE DE STOCK CRÍTICO",
                        FontFactory.getFont(
                                FontFactory.HELVETICA_BOLD,
                                18
                        )
                );

        titulo.setAlignment(
                Element.ALIGN_CENTER
        );

        document.add(titulo);

        document.add(
                new Paragraph(
                        "Productos encontrados: "
                        + productos.size()
                )
        );

        document.add(
                new Paragraph(" ")
        );

        PdfPTable tabla =
                new PdfPTable(5);

        tabla.setWidthPercentage(100);

        tabla.addCell(
                encabezado("ID")
        );

        tabla.addCell(
                encabezado("Producto")
        );

        tabla.addCell(
                encabezado("Categoría")
        );

        tabla.addCell(
                encabezado("Stock")
        );

        tabla.addCell(
                encabezado("Stock mínimo")
        );

        for(Object[] p : productos){

            tabla.addCell(
                    p[0].toString()
            );

            tabla.addCell(
                    p[1].toString()
            );

            tabla.addCell(
                    p[2].toString()
            );

            tabla.addCell(
                    p[3].toString()
            );

            tabla.addCell(
                    p[4].toString()
            );
        }

        document.add(tabla);

        document.close();

    }catch(Exception e){

        e.printStackTrace();
    }

    return new ByteArrayInputStream(
            out.toByteArray()
    );
}

public ByteArrayInputStream generarReporteAuditoria(
        String accion,
        String fechaInicio,
        String fechaFin
) {
LocalDateTime inicio = null;
LocalDateTime fin = null;

if(
    fechaInicio != null &&
    !fechaInicio.isEmpty()
){
    inicio =
            LocalDate.parse(
                    fechaInicio
            ).atStartOfDay();
}

if(
    fechaFin != null &&
    !fechaFin.isEmpty()
){
    fin =
            LocalDate.parse(
                    fechaFin
            ).atTime(
                    23,
                    59,
                    59
            );
}

if(
    accion != null &&
    accion.isBlank()
){
    accion = null;
}

List<Auditoria> auditorias =
        auditoriaRepository
                .obtenerReporteAuditoria(
                        accion,
                        inicio,
                        fin
                );

    Document document =
            new Document();

    ByteArrayOutputStream out =
            new ByteArrayOutputStream();

    try{

        PdfWriter.getInstance(
                document,
                out
        );

        document.open();

        Paragraph titulo =
                new Paragraph(
                        "REPORTE DE AUDITORÍA",
                        FontFactory.getFont(
                                FontFactory.HELVETICA_BOLD,
                                18
                        )
                );

        titulo.setAlignment(
                Element.ALIGN_CENTER
        );

        document.add(titulo);

        document.add(
                new Paragraph(" ")
        );

        PdfPTable tabla =
                new PdfPTable(5);

        tabla.setWidthPercentage(100);

        tabla.addCell(encabezado("ID"));
        tabla.addCell(encabezado("Acción"));
        tabla.addCell(encabezado("Fecha"));
        tabla.addCell(encabezado("Usuario"));
        tabla.addCell(encabezado("Rol"));

        for(Auditoria a : auditorias){

            tabla.addCell(
                    a.getId().toString()
            );

            tabla.addCell(
                    a.getAccion()
            );

            tabla.addCell(
                    a.getFecha().toString()
            );

            tabla.addCell(
                    a.getUsuario().getNombre()
            );

            tabla.addCell(
                    a.getUsuario()
                    .getRol()
                    .getNombre()
            );
        }

        document.add(tabla);

        document.close();

    }catch(Exception e){

        e.printStackTrace();
    }

    return new ByteArrayInputStream(
            out.toByteArray()
    );
}

public ByteArrayInputStream generarReporteUsuarios(
        String rol
) {

    if(
    rol != null &&
    rol.isBlank()
){
    rol = null;
}

List<Usuario> usuarios =
        usuarioRepository
                .obtenerReporteUsuarios(
                        rol
                );

    Document document =
            new Document();

    ByteArrayOutputStream out =
            new ByteArrayOutputStream();

    try{

        PdfWriter.getInstance(
                document,
                out
        );

        document.open();

        Paragraph titulo =
                new Paragraph(
                        "REPORTE DE USUARIOS",
                        FontFactory.getFont(
                                FontFactory.HELVETICA_BOLD,
                                18
                        )
                );

        titulo.setAlignment(
                Element.ALIGN_CENTER
        );

        document.add(titulo);

        document.add(
                new Paragraph(" ")
        );

        PdfPTable tabla =
                new PdfPTable(4);

        tabla.setWidthPercentage(100);

        tabla.addCell(encabezado("ID"));
        tabla.addCell(encabezado("Nombre"));
        tabla.addCell(encabezado("Correo"));
        tabla.addCell(encabezado("Rol"));

        for(Usuario u : usuarios){

            tabla.addCell(
                    u.getId().toString()
            );

            tabla.addCell(
                    u.getNombre()
            );

            tabla.addCell(
                    u.getCorreo()
            );

            tabla.addCell(
                    u.getRol().getNombre()
            );
        }

        document.add(tabla);

        document.close();

    }catch(Exception e){

        e.printStackTrace();
    }

    return new ByteArrayInputStream(
            out.toByteArray()
    );
}
}