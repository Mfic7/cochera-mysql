package pe.cochera.reporte;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.cochera.sesion.SesionService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static pe.cochera.util.Fechas.sql;

/**
 * Reportes (PDF / Excel) -> /api/admin/reportes/**
 * Días de estadía por cliente/placa dentro de un rango, a S/ precio_dia.
 */
@RestController
@RequestMapping("/api/admin/reportes")
public class ReporteController {

    private static final float MARGEN = 50, X_CLIENTE = 50, X_PLACA = 200, X_MODELO = 260, X_TIPO = 360, X_DIAS = 460, X_TOTAL = 495;

    private final JdbcTemplate db;
    private final SesionService sesiones;

    public ReporteController(JdbcTemplate db, SesionService sesiones) {
        this.db = db;
        this.sesiones = sesiones;
    }

    @GetMapping("/estadias.pdf")
    public ResponseEntity<byte[]> pdf(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestParam(defaultValue = "1") int sedeId,
                                       @RequestParam(required = false) String desde,
                                       @RequestParam(required = false) String hasta) throws IOException {
        sesiones.validar(auth, "ADMIN");
        LocalDate[] rango = rango(desde, hasta);
        byte[] pdf = generarPdf(estadiasData(sedeId, rango[0], rango[1]), rango[0], rango[1]);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estadias.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/estadias.xlsx")
    public ResponseEntity<byte[]> xlsx(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestParam(defaultValue = "1") int sedeId,
                                        @RequestParam(required = false) String desde,
                                        @RequestParam(required = false) String hasta) throws IOException {
        sesiones.validar(auth, "ADMIN");
        LocalDate[] rango = rango(desde, hasta);
        byte[] xlsx = generarExcel(estadiasData(sedeId, rango[0], rango[1]), rango[0], rango[1]);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=estadias.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }

    private LocalDate[] rango(String desde, String hasta) {
        LocalDate d = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : LocalDate.now().withDayOfMonth(1);
        LocalDate h = (hasta != null && !hasta.isBlank()) ? LocalDate.parse(hasta) : d.withDayOfMonth(d.lengthOfMonth());
        return new LocalDate[]{d, h};
    }

    /** Por cliente+placa, cuántos días de su(s) reserva(s) caen dentro de [desde, hasta], a precio de su tipo de vehículo. */
    private List<Map<String, Object>> estadiasData(int sedeId, LocalDate desde, LocalDate hasta) {
        return db.queryForList("""
            SELECT u.nombre AS cliente, v.placa, v.modelo, tv.nombre AS tipoVehiculo, tv.precio_dia AS precioDia,
                   SUM(DATEDIFF(LEAST(r.fecha_fin, ?), GREATEST(r.fecha_inicio, ?)) + 1) AS dias
              FROM reserva r
              JOIN usuario u ON u.id = r.usuario_id
              JOIN vehiculo v ON v.id = r.vehiculo_id
              JOIN tipo_vehiculo tv ON tv.id = v.tipo_vehiculo_id
             WHERE r.sede_id = ? AND r.estado <> 'CANCELADA'
               AND r.fecha_inicio <= ? AND r.fecha_fin >= ?
             GROUP BY u.id, v.id
             ORDER BY u.nombre
        """, sql(hasta), sql(desde), sedeId, sql(hasta), sql(desde));
    }

    private byte[] generarPdf(List<Map<String, Object>> filas, LocalDate desde, LocalDate hasta) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            PDPageContentStream cs = new PDPageContentStream(doc, pagina);
            float y = pagina.getMediaBox().getHeight() - MARGEN;

            texto(cs, bold, 15, MARGEN, y, "Reporte de estadías por cliente (" + desde + " a " + hasta + ")");
            y -= 28;
            fila(cs, bold, 10, y, "CLIENTE", "PLACA", "MODELO", "TIPO", "DÍAS", "TOTAL S/");
            y -= 16;

            BigDecimal granTotal = BigDecimal.ZERO;
            String clienteActual = null;
            BigDecimal subtotalCliente = BigDecimal.ZERO;
            for (Map<String, Object> f : filas) {
                if (y < MARGEN + 40) {
                    cs.close();
                    pagina = new PDPage(PDRectangle.A4);
                    doc.addPage(pagina);
                    cs = new PDPageContentStream(doc, pagina);
                    y = pagina.getMediaBox().getHeight() - MARGEN;
                }
                String cliente = (String) f.get("cliente");
                if (clienteActual != null && !clienteActual.equals(cliente)) {
                    texto(cs, bold, 9, X_TIPO, y, "Subtotal " + clienteActual + ": S/ " + String.format("%.2f", subtotalCliente));
                    y -= 18;
                    subtotalCliente = BigDecimal.ZERO;
                }
                clienteActual = cliente;

                long dias = ((Number) f.get("dias")).longValue();
                BigDecimal precioDia = (BigDecimal) f.get("precioDia");
                BigDecimal total = precioDia.multiply(BigDecimal.valueOf(dias));
                granTotal = granTotal.add(total);
                subtotalCliente = subtotalCliente.add(total);
                String modelo = (String) f.get("modelo");
                fila(cs, normal, 10, y, truncar(cliente, 22), (String) f.get("placa"),
                        truncar(modelo == null ? "" : modelo, 16), (String) f.get("tipoVehiculo"),
                        String.valueOf(dias), String.format("%.2f", total));
                y -= 15;
            }
            if (clienteActual != null) {
                texto(cs, bold, 9, X_TIPO, y, "Subtotal " + clienteActual + ": S/ " + String.format("%.2f", subtotalCliente));
                y -= 18;
            }

            y -= 12;
            texto(cs, bold, 12, MARGEN, y, "TOTAL GENERAL: S/ " + String.format("%.2f", granTotal));
            cs.close();

            doc.save(out);
            return out.toByteArray();
        }
    }

    private void texto(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private void fila(PDPageContentStream cs, PDType1Font font, float size, float y,
                       String cliente, String placa, String modelo, String tipo, String dias, String total) throws IOException {
        texto(cs, font, size, X_CLIENTE, y, cliente);
        texto(cs, font, size, X_PLACA, y, placa);
        texto(cs, font, size, X_MODELO, y, modelo);
        texto(cs, font, size, X_TIPO, y, tipo);
        texto(cs, font, size, X_DIAS, y, dias);
        texto(cs, font, size, X_TOTAL, y, total);
    }

    private String truncar(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private byte[] generarExcel(List<Map<String, Object>> filas, LocalDate desde, LocalDate hasta) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Estadías");
            CellStyle negrita = wb.createCellStyle();
            Font fuenteNegrita = wb.createFont();
            fuenteNegrita.setBold(true);
            negrita.setFont(fuenteNegrita);

            Row titulo = sheet.createRow(0);
            Cell tituloCell = titulo.createCell(0);
            tituloCell.setCellValue("Reporte de estadías por cliente (" + desde + " a " + hasta + ")");
            tituloCell.setCellStyle(negrita);

            Row header = sheet.createRow(2);
            String[] columnas = {"Cliente", "Placa", "Modelo", "Tipo", "Días", "Total (S/)"};
            for (int i = 0; i < columnas.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(columnas[i]);
                c.setCellStyle(negrita);
            }

            int filaIdx = 3;
            BigDecimal granTotal = BigDecimal.ZERO;
            String clienteActual = null;
            BigDecimal subtotalCliente = BigDecimal.ZERO;
            for (Map<String, Object> f : filas) {
                String cliente = (String) f.get("cliente");
                if (clienteActual != null && !clienteActual.equals(cliente)) {
                    Row subRow = sheet.createRow(filaIdx++);
                    Cell subLabel = subRow.createCell(3);
                    subLabel.setCellValue("Subtotal " + clienteActual);
                    subLabel.setCellStyle(negrita);
                    Cell subValue = subRow.createCell(5);
                    subValue.setCellValue(subtotalCliente.doubleValue());
                    subValue.setCellStyle(negrita);
                    subtotalCliente = BigDecimal.ZERO;
                }
                clienteActual = cliente;

                long dias = ((Number) f.get("dias")).longValue();
                BigDecimal precioDia = (BigDecimal) f.get("precioDia");
                BigDecimal total = precioDia.multiply(BigDecimal.valueOf(dias));
                granTotal = granTotal.add(total);
                subtotalCliente = subtotalCliente.add(total);
                String modelo = (String) f.get("modelo");
                Row row = sheet.createRow(filaIdx++);
                row.createCell(0).setCellValue(cliente);
                row.createCell(1).setCellValue((String) f.get("placa"));
                row.createCell(2).setCellValue(modelo == null ? "" : modelo);
                row.createCell(3).setCellValue((String) f.get("tipoVehiculo"));
                row.createCell(4).setCellValue(dias);
                row.createCell(5).setCellValue(total.doubleValue());
            }
            if (clienteActual != null) {
                Row subRow = sheet.createRow(filaIdx++);
                Cell subLabel = subRow.createCell(3);
                subLabel.setCellValue("Subtotal " + clienteActual);
                subLabel.setCellStyle(negrita);
                Cell subValue = subRow.createCell(5);
                subValue.setCellValue(subtotalCliente.doubleValue());
                subValue.setCellStyle(negrita);
            }

            Row totalRow = sheet.createRow(filaIdx + 1);
            Cell totalLabel = totalRow.createCell(0);
            totalLabel.setCellValue("TOTAL GENERAL");
            totalLabel.setCellStyle(negrita);
            Cell totalValue = totalRow.createCell(5);
            totalValue.setCellValue(granTotal.doubleValue());
            totalValue.setCellStyle(negrita);

            for (int i = 0; i < columnas.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }
}
