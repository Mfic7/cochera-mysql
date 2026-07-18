package pe.cochera.sesion;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sesiones en memoria (token simple, sin Spring Security). */
@Service
public class SesionService {

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();

    public String crear(int usuarioId, String nombre, String rol) {
        String token = UUID.randomUUID().toString();
        sesiones.put(token, new Sesion(usuarioId, nombre, rol, Instant.now().plus(Duration.ofHours(8))));
        return token;
    }

    /** Valida el header Authorization: Bearer &lt;token&gt; y, si se pide, el rol. */
    public Sesion validar(String authHeader, String rolRequerido) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión requerida");
        }
        String token = authHeader.substring(7);
        Sesion s = sesiones.get(token);
        if (s == null || s.expira().isBefore(Instant.now())) {
            sesiones.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión expirada, vuelve a iniciar sesión");
        }
        if (rolRequerido != null && !rolRequerido.equals(s.rol())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }
        return s;
    }

    public void invalidar(String token) {
        sesiones.remove(token);
    }

    /** Invalida todas las sesiones activas de un usuario (se usa al cambiar su password). */
    public void invalidarSesionesDe(int usuarioId) {
        sesiones.entrySet().removeIf(e -> e.getValue().usuarioId() == usuarioId);
    }
}
