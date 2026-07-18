package pe.cochera.reserva;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static pe.cochera.util.Fechas.sql;

/**
 * Reglas del aviso de llegada (usadas tanto por el usuario como por el admin al reservar
 * a nombre de un cliente):
 *  - solo entre las 8:00 am y las 11:00 pm;
 *  - solo se crea la reserva mientras (ocupados &lt; maximo) de esa sede, para CADA día del rango;
 *  - una placa no puede tener dos reservas activas en fechas que se crucen (en cualquier sede).
 */
@Service
public class ReservaService {

    private static final LocalTime HORARIO_DESDE = LocalTime.of(8, 0);
    private static final LocalTime HORARIO_HASTA = LocalTime.of(23, 0);

    private final JdbcTemplate db;

    public ReservaService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional
    public ResponseEntity<?> crear(int usuarioId, String nombreUsuario,
                                    int sedeIdIn, String placaIn, String modelo, int tipoVehiculoId,
                                    String horaLlegada, int diasIn) {
        if (!dentroDeHorario()) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Fuera de horario. El aviso de llegada solo se puede registrar entre las 8:00 am y las 11:00 pm."));
        }

        int sedeId = sedeIdIn <= 0 ? 1 : sedeIdIn;
        int dias = diasIn <= 0 ? 1 : diasIn;
        LocalDate fechaInicio = LocalDate.now();
        LocalDate fechaFin = fechaInicio.plusDays(dias - 1L);

        int maximo = cupoMaximo(sedeId);
        for (LocalDate dia = fechaInicio; !dia.isAfter(fechaFin); dia = dia.plusDays(1)) {
            if (ocupados(sedeId, dia) >= maximo) {
                db.update("INSERT INTO notificacion (sede_id, tipo, mensaje) VALUES (?,'CUPO_LLENO', ?)",
                        sedeId, "Solicitud rechazada de " + nombreUsuario + ": cochera llena el " + dia);
                return ResponseEntity.status(409).body(Map.of(
                        "error", "Cochera llena el " + dia + ". El máximo es " + maximo + " autos."));
            }
        }

        String placa = placaIn.trim().toUpperCase();

        Integer activa = db.queryForObject(
            "SELECT COUNT(*) FROM reserva r JOIN vehiculo v ON v.id=r.vehiculo_id " +
            "WHERE v.placa=? AND r.estado IN ('PENDIENTE','EN_COCHERA') " +
            "AND r.fecha_inicio <= ? AND r.fecha_fin >= ?",
            Integer.class, placa, sql(fechaFin), sql(fechaInicio));
        if (activa != null && activa > 0) {
            return ResponseEntity.status(409).body(
                    Map.of("error", "La placa " + placa + " ya tiene una reserva activa en esas fechas."));
        }

        int vehiculoId = buscarOCrearVehiculo(usuarioId, placa, modelo, tipoVehiculoId);

        db.update("INSERT INTO reserva (usuario_id, vehiculo_id, sede_id, fecha_inicio, fecha_fin, hora_llegada, estado) " +
                  "VALUES (?,?,?,?,?,?, 'PENDIENTE')",
                  usuarioId, vehiculoId, sedeId, sql(fechaInicio), sql(fechaFin), horaLlegada);

        String detalleViaje = dias > 1 ? " Se queda hasta el " + fechaFin + " (" + dias + " días)." : "";
        db.update("INSERT INTO notificacion (sede_id, tipo, mensaje) VALUES (?,'LLEGADA', ?)",
                sedeId, nombreUsuario + " (" + placa + ") avisó que llega a las " + horaLlegada + "." + detalleViaje);

        int libres = maximo - ocupados(sedeId, fechaInicio);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "mensaje", "Listo, " + nombreUsuario + ". El admin fue notificado: llegas a las "
                           + horaLlegada + "." + detalleViaje,
                "libres", libres));
    }

    /** Si la placa ya existe se reutiliza tal cual (conserva su tipo de vehículo original). */
    public int buscarOCrearVehiculo(int usuarioId, String placa, String modelo, int tipoVehiculoId) {
        List<Integer> ids = db.queryForList(
                "SELECT id FROM vehiculo WHERE placa=?", Integer.class, placa);
        if (!ids.isEmpty()) return ids.get(0);

        int tipo = tipoVehiculoId <= 0 ? 1 : tipoVehiculoId;
        KeyHolder kh = new GeneratedKeyHolder();
        db.update(c -> {
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO vehiculo (usuario_id, placa, modelo, tipo_vehiculo_id) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, usuarioId);
            ps.setString(2, placa);
            ps.setString(3, modelo == null ? "" : modelo);
            ps.setInt(4, tipo);
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }

    public int cupoMaximo(int sedeId) {
        return db.queryForObject("SELECT cupo_maximo FROM sede WHERE id=?", Integer.class, sedeId);
    }

    public int ocupados(int sedeId) {
        return ocupados(sedeId, LocalDate.now());
    }

    /** Cuántos autos ocupan la sede en un día puntual (cuenta también los viajes en curso ese día). */
    public int ocupados(int sedeId, LocalDate dia) {
        return db.queryForObject(
            "SELECT COUNT(*) FROM reserva WHERE sede_id=? AND estado IN ('PENDIENTE','EN_COCHERA') " +
            "AND fecha_inicio <= ? AND fecha_fin >= ?", Integer.class, sedeId, sql(dia), sql(dia));
    }

    public boolean dentroDeHorario() {
        LocalTime ahora = LocalTime.now();
        return !ahora.isBefore(HORARIO_DESDE) && !ahora.isAfter(HORARIO_HASTA);
    }
}
