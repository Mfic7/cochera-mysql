package pe.cochera.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** A los usuarios sembrados por cochera.sql (sin password) se les asigna una clave temporal. */
@Component
public class DemoDataInitializer implements CommandLineRunner {

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public DemoDataInitializer(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        List<Map<String, Object>> sinClave = db.queryForList(
                "SELECT id, dni FROM usuario WHERE password IS NULL OR password = ''");
        if (sinClave.isEmpty()) return;
        String hash = encoder.encode("cochera123");
        for (Map<String, Object> u : sinClave) {
            db.update("UPDATE usuario SET password=? WHERE id=?", hash, u.get("id"));
        }
        System.out.println(">> Clave temporal 'cochera123' asignada a " + sinClave.size()
                + " usuario(s) demo sin password (tabla usuario). Cámbiala antes de producción.");
    }
}
