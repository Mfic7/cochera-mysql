package pe.cochera.sede;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Sedes (locales) -> /api/sedes (público: lo usan admin y usuario). */
@RestController
@RequestMapping("/api/sedes")
public class SedeController {

    private final JdbcTemplate db;

    public SedeController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return db.queryForList("SELECT id, nombre, cupo_maximo AS cupoMaximo FROM sede ORDER BY id");
    }
}
