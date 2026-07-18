package pe.cochera.scheduler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada día (8:05 am), registra un recordatorio por cada viaje en curso que ya pasó su
 * primer día, para que el admin (y el cliente, cuando entre a usuario.html) vean que
 * ese auto sigue reservado. No duplica el recordatorio si ya se generó uno hoy para
 * la misma reserva.
 */
@Component
public class RecordatorioScheduler {

    private final JdbcTemplate db;

    public RecordatorioScheduler(JdbcTemplate db) {
        this.db = db;
    }

    @Scheduled(cron = "0 5 8 * * *")
    public void recordarViajesActivos() {
        db.update("""
            INSERT INTO notificacion (reserva_id, sede_id, tipo, mensaje)
            SELECT r.id, r.sede_id, 'RECORDATORIO',
                   CONCAT(u.nombre, ' (', v.placa, ') sigue reservado hasta el ', r.fecha_fin)
              FROM reserva r
              JOIN usuario u ON u.id = r.usuario_id
              JOIN vehiculo v ON v.id = r.vehiculo_id
             WHERE r.estado IN ('PENDIENTE','EN_COCHERA')
               AND r.fecha_fin > r.fecha_inicio
               AND CURDATE() > r.fecha_inicio AND CURDATE() <= r.fecha_fin
               AND NOT EXISTS (
                     SELECT 1 FROM notificacion n
                      WHERE n.reserva_id = r.id AND n.tipo = 'RECORDATORIO' AND DATE(n.creado_en) = CURDATE()
                   )
        """);
    }
}
