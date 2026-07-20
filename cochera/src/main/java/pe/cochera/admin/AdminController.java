package pe.cochera.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import pe.cochera.auth.RegistroIn;
import pe.cochera.reserva.Reserva;
import pe.cochera.reserva.ReservaService;
import pe.cochera.sesion.SesionService;
import pe.cochera.usuario.UsuarioRow;
import pe.cochera.usuario.UsuarioService;
import pe.cochera.vehiculo.TipoVehiculoIn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static pe.cochera.util.Fechas.hhmm;

/**
 * API del admin -> /api/admin/**
 * Todas las rutas reciben sedeId (Cochera 1 = 1, Cochera 2 = 2, ...).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final JdbcTemplate db;
    private final SesionService sesiones;
    private final ReservaService reservas;
    private final UsuarioService usuarios;

    public AdminController(JdbcTemplate db, SesionService sesiones, ReservaService reservas, UsuarioService usuarios) {
        this.db = db;
        this.sesiones = sesiones;
        this.reservas = reservas;
        this.usuarios = usuarios;
    }

    /** Un solo request con todo lo que necesita el panel de una sede. */
    @GetMapping("/panel")
    public Map<String, Object> panel(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @RequestParam(defaultValue = "1") int sedeId) {
        sesiones.validar(auth, "ADMIN");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dashboard", dashboardData(sedeId));
        r.put("movimientos", movimientosData(sedeId));
        r.put("notificaciones", notificacionesData(sedeId));
        r.put("notificacionesNoLeidas", db.queryForObject(
                "SELECT COUNT(*) FROM notificacion WHERE sede_id=? AND usuario_id IS NULL AND leida=0", Integer.class, sedeId));
        return r;
    }

    /** Marca como leídas todas las notificaciones de una sede (se llama al abrir la campanita). */
    @PostMapping("/notificaciones/marcar-leidas")
    public Map<String, Object> marcarLeidas(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @RequestParam(defaultValue = "1") int sedeId) {
        sesiones.validar(auth, "ADMIN");
        db.update("UPDATE notificacion SET leida=1 WHERE sede_id=? AND usuario_id IS NULL AND leida=0", sedeId);
        return Map.of("ok", true);
    }

    /** Cambia el cupo máximo de una sede desde el panel. */
    @PutMapping("/cupo/{sedeId}/{maximo}")
    public Map<String, Object> cambiarCupo(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable int sedeId, @PathVariable int maximo) {
        sesiones.validar(auth, "ADMIN");
        db.update("UPDATE sede SET cupo_maximo=? WHERE id=?", maximo, sedeId);
        return Map.of("sedeId", sedeId, "cupoMaximo", maximo);
    }

    /** Cambia el precio por día de un tipo de vehículo (motos, trimotos, camionetas, etc.). */
    @PutMapping("/tipos-vehiculo/{id}")
    public ResponseEntity<?> actualizarPrecioTipo(@RequestHeader(value = "Authorization", required = false) String auth,
                                                    @PathVariable int id, @RequestBody TipoVehiculoIn in) {
        sesiones.validar(auth, "ADMIN");
        if (in.precioDia == null || in.precioDia.signum() < 0) {
            return ResponseEntity.status(400).body(Map.of("error", "El precio debe ser un número válido mayor o igual a 0."));
        }
        db.update("UPDATE tipo_vehiculo SET precio_dia=? WHERE id=?", in.precioDia, id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Crea un tipo de vehículo nuevo (ej. "Bicicleta") con su propio precio por día. */
    @PostMapping("/tipos-vehiculo")
    public ResponseEntity<?> crearTipoVehiculo(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @RequestBody TipoVehiculoIn in) {
        sesiones.validar(auth, "ADMIN");
        if (in.nombre == null || in.nombre.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "El nombre es obligatorio."));
        }
        if (in.precioDia == null || in.precioDia.signum() < 0) {
            return ResponseEntity.status(400).body(Map.of("error", "El precio debe ser un número válido mayor o igual a 0."));
        }
        Integer existe = db.queryForObject(
                "SELECT COUNT(*) FROM tipo_vehiculo WHERE nombre=?", Integer.class, in.nombre.trim());
        if (existe != null && existe > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe un tipo de vehículo con ese nombre."));
        }
        db.update("INSERT INTO tipo_vehiculo (nombre, precio_dia) VALUES (?,?)", in.nombre.trim(), in.precioDia);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Lista de clientes para el panel "Crear cliente", con búsqueda opcional por nombre/DNI/celular/correo. */
    @GetMapping("/clientes")
    public List<Map<String, Object>> listarClientes(@RequestHeader(value = "Authorization", required = false) String auth,
                                                       @RequestParam(required = false) String q) {
        sesiones.validar(auth, "ADMIN");
        return usuarios.listarClientes(q);
    }

    /** Datos de un cliente para precargar el formulario de edición. */
    @GetMapping("/clientes/{id}")
    public ResponseEntity<?> obtenerCliente(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @PathVariable int id) {
        sesiones.validar(auth, "ADMIN");
        Optional<Map<String, Object>> cliente = usuarios.obtenerCliente(id);
        if (cliente.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "No existe ese cliente."));
        }
        return ResponseEntity.ok(cliente.get());
    }

    /** Edita los datos de un cliente ya registrado. La contraseña solo se cambia si viene informada. */
    @PutMapping("/clientes/{id}")
    public ResponseEntity<?> editarCliente(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @PathVariable int id, @RequestBody RegistroIn in) {
        sesiones.validar(auth, "ADMIN");
        if (usuarios.obtenerCliente(id).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "No existe ese cliente."));
        }
        if (in.password != null && !in.password.isBlank() && in.password.length() < 4) {
            return ResponseEntity.status(400).body(Map.of("error", "La contraseña debe tener al menos 4 caracteres."));
        }
        if (usuarios.existeDniOtro(in.dni, id)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe otra cuenta con ese DNI."));
        }
        if (usuarios.existeCorreoOtro(in.correo, id)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe otra cuenta con ese correo."));
        }
        usuarios.actualizarCliente(id, in);
        return ResponseEntity.ok(Map.of("ok", true, "nombre", in.nombre));
    }

    /** Activa o desactiva la cuenta de un cliente (bloquea/permite su login sin borrar sus datos). */
    @PutMapping("/clientes/{id}/estado/{estado}")
    public ResponseEntity<?> cambiarEstadoCliente(@RequestHeader(value = "Authorization", required = false) String auth,
                                                    @PathVariable int id, @PathVariable String estado) {
        sesiones.validar(auth, "ADMIN");
        String estadoNorm = estado.toUpperCase();
        if (!estadoNorm.equals("ACTIVO") && !estadoNorm.equals("INACTIVO")) {
            return ResponseEntity.status(400).body(Map.of("error", "Estado inválido."));
        }
        if (!usuarios.cambiarEstadoCliente(id, estadoNorm)) {
            return ResponseEntity.status(404).body(Map.of("error", "No existe ese cliente."));
        }
        return ResponseEntity.ok(Map.of("ok", true, "estado", estadoNorm));
    }

    /** El vigilante marca el ingreso real del vehículo. */
    @PostMapping("/reservas/{id}/ingreso")
    public Map<String, Object> ingreso(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @PathVariable int id) {
        sesiones.validar(auth, "ADMIN");
        String horaReal = hhmm();

        db.update("UPDATE reserva SET estado='EN_COCHERA', hora_ingreso=NOW() " +
                "WHERE id=? AND estado='PENDIENTE'", id);

        // Notificación para el ADMIN (usuario_id NULL -> es de la sede)
        db.update("INSERT INTO notificacion (reserva_id, sede_id, tipo, mensaje) " +
                "SELECT ?, sede_id, 'INGRESO', CONCAT('Ingresó el vehículo de la reserva #', ?, ' a las ', ?) " +
                "FROM reserva WHERE id=?",
                id, id, horaReal, id);

        // Notificación para el CLIENTE (usuario_id de la reserva)
        db.update("INSERT INTO notificacion (reserva_id, sede_id, usuario_id, tipo, mensaje) " +
                "SELECT r.id, r.sede_id, r.usuario_id, 'INGRESO', " +
                "       CONCAT('Registramos el ingreso de tu vehículo ', v.placa, ' a las ', ?) " +
                "FROM reserva r JOIN vehiculo v ON v.id = r.vehiculo_id WHERE r.id=?",
                horaReal, id);

        return Map.of("ok", true);
    }

    /**
     * El admin reserva un espacio a nombre de un cliente ya registrado (por DNI) — para
     * "separarle" cupo a los clientes frecuentes sin que ellos mismos entren al sistema.
     * Usa las mismas reglas (horario, cupo por día, placa sin cruces) que el autoservicio.
     */
    @PostMapping("/reservas")
    public ResponseEntity<?> reservarParaCliente(@RequestHeader(value = "Authorization", required = false) String auth,
                                                   @RequestBody Reserva in) {
        sesiones.validar(auth, "ADMIN");
        Optional<UsuarioRow> clienteOpt = usuarios.buscarClientePorDni(in.dni);
        if (clienteOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "No existe un cliente con ese DNI. Créalo primero en \"Crear cliente\"."));
        }
        UsuarioRow cliente = clienteOpt.get();
        return reservas.crear(cliente.id(), cliente.nombre(), in.sedeId, in.placa, in.modelo, in.tipoVehiculoId, in.horaLlegada, in.dias);
    }

    /** El vigilante marca la salida y libera el cupo. */
    @PostMapping("/reservas/{id}/salida")
    public Map<String, Object> salida(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable int id) {
        sesiones.validar(auth, "ADMIN");
        String horaReal = hhmm();

        db.update("UPDATE reserva SET estado='SALIO', hora_salida=NOW() " +
                "WHERE id=? AND estado='EN_COCHERA'", id);

        // Notificación para el ADMIN (usuario_id NULL -> es de la sede)
        db.update("INSERT INTO notificacion (reserva_id, sede_id, tipo, mensaje) " +
                "SELECT ?, sede_id, 'SALIDA', CONCAT('Salió el vehículo de la reserva #', ?, ' — cupo liberado') " +
                "FROM reserva WHERE id=?",
                id, id, id);

        // Notificación para el CLIENTE (usuario_id de la reserva)
        db.update("INSERT INTO notificacion (reserva_id, sede_id, usuario_id, tipo, mensaje) " +
                "SELECT r.id, r.sede_id, r.usuario_id, 'SALIDA', " +
                "       CONCAT('Registramos la salida de tu vehículo ', v.placa, ' a las ', ?, '. ¡Gracias por tu visita!') " +
                "FROM reserva r JOIN vehiculo v ON v.id = r.vehiculo_id WHERE r.id=?",
                horaReal, id);

        return Map.of("ok", true);
    }

    /** Notificaciones de un cliente (las que tienen su usuario_id). Las usa el panel del usuario. */
    @GetMapping("/clientes/{id}/notificaciones")
    public List<Map<String, Object>> notificacionesCliente(@PathVariable int id) {
        return db.queryForList(
                "SELECT id, tipo, mensaje, leida, creado_en AS creadoEn " +
                "FROM notificacion WHERE usuario_id = ? ORDER BY creado_en DESC LIMIT 30", id);
    }

    private Map<String, Object> dashboardData(int sedeId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalUsuarios", db.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE rol='USUARIO'", Integer.class));
        r.put("cupoMaximo", reservas.cupoMaximo(sedeId));
        int ocupados = reservas.ocupados(sedeId);
        r.put("ocupados", ocupados);
        r.put("libres", reservas.cupoMaximo(sedeId) - ocupados);
        return r;
    }

    private List<Map<String, Object>> movimientosData(int sedeId) {
        return db.queryForList("""
            SELECT r.id, u.nombre, u.celular, v.placa,
                   r.hora_llegada AS horaLlegada,
                   r.hora_ingreso  AS horaIngreso,
                   r.hora_salida   AS horaSalida,
                   r.fecha_fin     AS fechaFin,
                   r.estado
              FROM reserva r
              JOIN usuario  u ON u.id = r.usuario_id
              JOIN vehiculo v ON v.id = r.vehiculo_id
             WHERE r.sede_id = ? AND r.fecha_inicio <= CURDATE() AND r.fecha_fin >= CURDATE()
             ORDER BY r.hora_llegada
        """, sedeId);
    }

    /** Solo notificaciones de la SEDE (para el admin): las que no tienen usuario_id. */
    private List<Map<String, Object>> notificacionesData(int sedeId) {
        return db.queryForList(
                "SELECT id, tipo, mensaje, leida, creado_en AS creadoEn " +
                "FROM notificacion WHERE sede_id = ? AND usuario_id IS NULL ORDER BY creado_en DESC LIMIT 30", sedeId);
    }
}