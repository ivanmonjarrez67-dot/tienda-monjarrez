package entidades;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Genera el PDF de la factura de un pedido, para adjuntarlo a los correos
 * de confirmación (comprador, vendedor, admin) vía EmailService.
 *
 * 🆕 Ahora incluye los datos del cliente (nombre, correo, teléfono y
 * dirección de entrega) y las especificaciones de cada producto
 * (color, talla, etc.) para que el vendedor sepa qué enviar y a dónde.
 */
public class FacturaPdfGenerator {

    private static final Color ROJO = new Color(161, 51, 65);
    private static final Color GRIS_CLARO = new Color(240, 240, 240);
    private static final Color GRIS_TEXTO = new Color(90, 90, 90);

    /** Una línea de producto dentro de la factura. */
    public static class ItemFactura {
        public final String nombre;
        public final int cantidad;
        public final double precioUnitario;
        public final String especificaciones; // 🆕 puede ser null

        public ItemFactura(String nombre, int cantidad, double precioUnitario, String especificaciones) {
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
            this.especificaciones = especificaciones;
        }

        /** Compatibilidad con código anterior (sin especificaciones). */
        public ItemFactura(String nombre, int cantidad, double precioUnitario) {
            this(nombre, cantidad, precioUnitario, null);
        }
    }

    public static byte[] generar(int pedidoId, Date fecha, String metodoPago,
                                  String referenciaPago, double total,
                                  List<ItemFactura> items,
                                  String nombreCliente, String correoCliente,
                                  String telefonoCliente, String direccionEntrega) throws Exception {
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, salida);
        doc.open();

        Font fuenteTitulo = new Font(Font.HELVETICA, 18, Font.BOLD, ROJO);
        Font fuenteSubtitulo = new Font(Font.HELVETICA, 11, Font.BOLD);
        Font fuenteNormal = new Font(Font.HELVETICA, 10);
        Font fuenteNegrita = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font fuenteSpec = new Font(Font.HELVETICA, 9, Font.ITALIC, GRIS_TEXTO);
        Font fuenteSeccion = new Font(Font.HELVETICA, 11, Font.BOLD, ROJO);

        doc.add(new Paragraph("Tienda Monjarrez", fuenteTitulo));
        doc.add(new Paragraph("Factura de pedido #" + pedidoId, fuenteSubtitulo));
        doc.add(new Paragraph(new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("es", "CR")).format(fecha), fuenteNormal));
        doc.add(new Paragraph(" "));

        // 🆕 Datos del cliente / entrega
        doc.add(new Paragraph("Datos del cliente y entrega", fuenteSeccion));
        PdfPTable datos = new PdfPTable(2);
        datos.setWidthPercentage(100);
        datos.setWidths(new float[]{1.3f, 4f});
        agregarFilaDato(datos, "Cliente", nombreCliente, fuenteNegrita, fuenteNormal);
        agregarFilaDato(datos, "Correo", correoCliente, fuenteNegrita, fuenteNormal);
        agregarFilaDato(datos, "Teléfono", telefonoCliente, fuenteNegrita, fuenteNormal);
        agregarFilaDato(datos, "Dirección de entrega", direccionEntrega, fuenteNegrita, fuenteNormal);
        doc.add(datos);
        doc.add(new Paragraph(" "));

        String metodoTexto = "sinpe".equals(metodoPago) ? "SINPE Móvil" : "Efectivo contra entrega";
        doc.add(new Paragraph("Método de pago: " + metodoTexto, fuenteNormal));
        if (referenciaPago != null && !referenciaPago.isEmpty()) {
            doc.add(new Paragraph("N.º de comprobante: " + referenciaPago, fuenteNormal));
        }
        doc.add(new Paragraph(" "));

        PdfPTable tabla = new PdfPTable(4);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{4f, 1f, 1.5f, 1.5f});

        for (String encabezado : new String[]{"Producto", "Cant.", "Precio", "Subtotal"}) {
            PdfPCell celda = new PdfPCell(new Phrase(encabezado, fuenteNegrita));
            celda.setBackgroundColor(GRIS_CLARO);
            celda.setPadding(6);
            tabla.addCell(celda);
        }

        for (ItemFactura item : items) {
            // 🆕 Nombre + especificaciones del cliente debajo, en una sola celda
            Phrase producto = new Phrase();
            producto.add(new com.lowagie.text.Chunk(item.nombre, fuenteNormal));
            if (item.especificaciones != null && !item.especificaciones.trim().isEmpty()) {
                producto.add(new com.lowagie.text.Chunk("\nEspecificaciones: " + item.especificaciones.trim(), fuenteSpec));
            }
            PdfPCell celdaProducto = new PdfPCell(producto);
            celdaProducto.setPadding(6);
            tabla.addCell(celdaProducto);

            tabla.addCell(celdaSimple(String.valueOf(item.cantidad), fuenteNormal));
            tabla.addCell(celdaSimple(fmtCrc(item.precioUnitario), fuenteNormal));
            tabla.addCell(celdaSimple(fmtCrc(item.precioUnitario * item.cantidad), fuenteNormal));
        }
        doc.add(tabla);
        doc.add(new Paragraph(" "));

        Paragraph totalPar = new Paragraph("Total: " + fmtCrc(total), fuenteSubtitulo);
        totalPar.setAlignment(Paragraph.ALIGN_RIGHT);
        doc.add(totalPar);

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph(
                "Si pagaste por SINPE Móvil, conserva el comprobante de tu banco. "
                        + "Si elegiste efectivo, ten el monto exacto listo al momento de la entrega.",
                fuenteNormal));

        doc.close();
        return salida.toByteArray();
    }

    private static void agregarFilaDato(PdfPTable tabla, String etiqueta, String valor, Font fEtiqueta, Font fValor) {
        PdfPCell c1 = new PdfPCell(new Phrase(etiqueta, fEtiqueta));
        c1.setPadding(5);
        c1.setBackgroundColor(GRIS_CLARO);
        tabla.addCell(c1);
        String texto = (valor == null || valor.trim().isEmpty()) ? "No indicado" : valor.trim();
        PdfPCell c2 = new PdfPCell(new Phrase(texto, fValor));
        c2.setPadding(5);
        tabla.addCell(c2);
    }

    private static PdfPCell celdaSimple(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setPadding(6);
        return celda;
    }

    private static String fmtCrc(double n) {
        return "\u20a1" + String.format(new Locale("es", "CR"), "%,.0f", n);
    }
}