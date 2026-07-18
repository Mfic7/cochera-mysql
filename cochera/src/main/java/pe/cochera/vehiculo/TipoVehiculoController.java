package pe.cochera.vehiculo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Tipos de vehículo -> /api/tipos-vehiculo (público, lo usa usuario.html).
 * El precio por día se administra desde /api/admin/tipos-vehiculo/**.
 */
@RestController
@RequestMapping("/api/tipos-vehiculo")
public class TipoVehiculoController {

    private final JdbcTemplate db;

    public TipoVehiculoController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return db.queryForList("SELECT id, nombre, precio_dia AS precioDia FROM tipo_vehiculo ORDER BY id");
    }
}
