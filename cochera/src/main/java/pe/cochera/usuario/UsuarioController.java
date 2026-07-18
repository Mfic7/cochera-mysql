package pe.cochera.usuario;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import pe.cochera.reserva.Reserva;
import pe.cochera.reserva.ReservaService;
import pe.cochera.sesion.Sesion;
import pe.cochera.sesion.SesionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {

    private final JdbcTemplate db;
    private final SesionService sesiones;
    private final ReservaService reservas;

    public UsuarioController(JdbcTemplate db, SesionService sesiones, ReservaService reservas) {
        this.db = db;
        this.sesiones = sesiones;
        this.reservas = reservas;
    }

    /** El formulario consulta esto para bloquearse cuando no hay cupo (público, no requiere login). */
    @GetMapping("/cupo")
    public Map<String, Object> cupo(@RequestParam(defaultValue = "1") int sedeId) {
        int max = reservas.cupoMaximo(sedeId), ocu = reservas.ocupados(sedeId);
        return Map.of("cupoMaximo", max, "ocupados", ocu,
                      "libres", max - ocu, "hayCupo", ocu < max);
    }

    /** Vehículos ya registrados por el usuario logueado, para no pedirle los datos de nuevo. */
    @GetMapping("/mis-vehiculos")
    public List<Map<String, Object>> misVehiculos(@RequestHeader(value = "Authorization", required = false) String auth) {
        Sesion s = sesiones.validar(auth, "USUARIO");
        return db.queryForList(
                "SELECT id, placa, modelo FROM vehiculo WHERE usuario_id=? ORDER BY id DESC", s.usuarioId());
    }

    /** Reservas de días con estadía en curso (fecha_fin > fecha_inicio) que ya pasaron su primer día. */
    @GetMapping("/recordatorios")
    public List<Map<String, Object>> recordatorios(@RequestHeader(value = "Authorization", required = false) String auth) {
        Sesion s = sesiones.validar(auth, "USUARIO");
        return db.queryForList("""
            SELECT v.placa, se.nombre AS sede, r.fecha_fin AS fechaFin
              FROM reserva r
              JOIN vehiculo v ON v.id = r.vehiculo_id
              JOIN sede se ON se.id = r.sede_id
             WHERE r.usuario_id = ? AND r.estado IN ('PENDIENTE','EN_COCHERA')
               AND r.fecha_fin > r.fecha_inicio
               AND CURDATE() > r.fecha_inicio AND CURDATE() <= r.fecha_fin
        """, s.usuarioId());
    }

    /**
     * Aviso de llegada a una sede. El usuario ya está identificado por su sesión (token);
     * solo se reciben sede, placa, modelo, hora y (opcional) días si se va de viaje.
     */
    @PostMapping("/reservar")
    public ResponseEntity<?> reservar(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Reserva in) {
        Sesion s = sesiones.validar(auth, "USUARIO");
        return reservas.crear(s.usuarioId(), s.nombre(), in.sedeId, in.placa, in.modelo, in.tipoVehiculoId, in.horaLlegada, in.dias);
    }
}
