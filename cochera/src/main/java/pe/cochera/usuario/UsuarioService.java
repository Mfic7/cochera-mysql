package pe.cochera.usuario;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import pe.cochera.auth.RegistroIn;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UsuarioService {

    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public UsuarioService(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    public Optional<UsuarioRow> buscarPorDniYRol(String dni, String rol) {
        List<Map<String, Object>> filas = db.queryForList(
                "SELECT id, nombre, password FROM usuario WHERE dni=? AND rol=?", dni, rol);
        if (filas.isEmpty()) return Optional.empty();
        Map<String, Object> u = filas.get(0);
        return Optional.of(new UsuarioRow((Integer) u.get("id"), (String) u.get("nombre"), (String) u.get("password")));
    }

    /** Cliente ya registrado (rol USUARIO) que el admin busca por DNI para reservarle un espacio. */
    public Optional<UsuarioRow> buscarClientePorDni(String dni) {
        List<Map<String, Object>> filas = db.queryForList(
                "SELECT id, nombre FROM usuario WHERE dni=? AND rol='USUARIO'", dni);
        if (filas.isEmpty()) return Optional.empty();
        Map<String, Object> u = filas.get(0);
        return Optional.of(new UsuarioRow((Integer) u.get("id"), (String) u.get("nombre"), null));
    }

    public boolean existeDni(String dni) {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM usuario WHERE dni=?", Integer.class, dni);
        return n != null && n > 0;
    }

    public boolean existeCorreo(String correo) {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM usuario WHERE correo=?", Integer.class, correo);
        return n != null && n > 0;
    }

    public int totalAdmins() {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM usuario WHERE rol='ADMIN'", Integer.class);
        return n == null ? 0 : n;
    }

    public int crear(RegistroIn in, String rol) {
        String hash = encoder.encode(in.password);
        KeyHolder kh = new GeneratedKeyHolder();
        db.update(c -> {
            PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO usuario (nombre, dni, celular, correo, password, rol) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, in.nombre);
            ps.setString(2, in.dni);
            ps.setString(3, in.celular);
            ps.setString(4, in.correo);
            ps.setString(5, hash);
            ps.setString(6, rol);
            return ps;
        }, kh);
        return kh.getKey().intValue();
    }

    public Optional<Integer> buscarParaRecuperar(String dni, String correo, String celular, String rol) {
        List<Integer> ids = db.queryForList(
                "SELECT id FROM usuario WHERE dni=? AND correo=? AND celular=? AND rol=?",
                Integer.class, dni, correo, celular, rol);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public void actualizarPassword(int id, String passwordPlano) {
        db.update("UPDATE usuario SET password=? WHERE id=?", encoder.encode(passwordPlano), id);
    }
}
