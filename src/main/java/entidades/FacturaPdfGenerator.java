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
 * 🆕 Genera el PDF de la factura de un pedido, para adjuntarlo a los
 * correos de confirmación (comprador, vendedor, admin) vía EmailService.
 *
 * Usa la librería OpenPDF (fork libre de iText 4). Hay que agregarla al
 * proyecto, por ejemplo en pom.xml:
 *
 *   <dependency>
 *     <groupId>com.github.librepdf</groupId>
 *     <artifactId>openpdf</artifactId>
 *     <version>1.3.39</version>
 *   </dependency>
 *
 * Si el proyecto no usa Maven, se puede descargar el .jar directamente
 * de https://github.com/LibrePDF/OpenPDF y ponerlo en las librerías del
 * servidor (ej. WEB-INF/lib).
 */
public class FacturaPdfGenerator {

    private static final Color ROJO = new Color(161, 51, 65);
    private static final Color GRIS_CLARO = new Color(240, 240, 240);

    /** Una línea de producto dentro de la factura. */
    public static class ItemFactura {
        public final String nombre;
        public final int cantidad;
        public final double precioUnitario;

        public ItemFactura(String nombre, int cantidad, double precioUnitario) {
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
        }
    }

    public static byte[] generar(int pedidoId, Date fecha, String metodoPago,
                                  String referenciaPago, double total,
                                  List<ItemFactura> items) throws Exception {
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, salida);
        doc.open();

        Font fuenteTitulo = new Font(Font.HELVETICA, 18, Font.BOLD, ROJO);
        Font fuenteSubtitulo = new Font(Font.HELVETICA, 11, Font.BOLD);
        Font fuenteNormal = new Font(Font.HELVETICA, 10);
        Font fuenteNegrita = new Font(Font.HELVETICA, 10, Font.BOLD);

        doc.add(new Paragraph("Tienda Monjarrez", fuenteTitulo));
        doc.add(new Paragraph("Factura de pedido #" + pedidoId, fuenteSubtitulo));
        doc.add(new Paragraph(new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("es", "CR")).format(fecha), fuenteNormal));
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
            tabla.addCell(celdaSimple(item.nombre, fuenteNormal));
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

    private static PdfPCell celdaSimple(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
        celda.setPadding(6);
        return celda;
    }

    private static String fmtCrc(double n) {
        return "\u20a1" + String.format(new Locale("es", "CR"), "%,.0f", n);
    }
}