package pe.cochera.seguridad;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Rate limiting simple contra fuerza bruta (login / recuperar contraseña). */
@Component
public class RateLimiter {

    private static final int MAX_INTENTOS = 5;
    private static final Duration VENTANA_BLOQUEO = Duration.ofMinutes(15);

    private record Intentos(int conteo, Instant desde) {}

    private final Map<String, Intentos> intentosFallidos = new ConcurrentHashMap<>();

    public boolean bloqueado(String clave) {
        Intentos i = intentosFallidos.get(clave);
        return i != null && i.conteo() >= MAX_INTENTOS
                && Duration.between(i.desde(), Instant.now()).compareTo(VENTANA_BLOQUEO) < 0;
    }

    public void registrarFallo(String clave) {
        intentosFallidos.compute(clave, (k, i) -> {
            if (i == null || Duration.between(i.desde(), Instant.now()).compareTo(VENTANA_BLOQUEO) >= 0) {
                return new Intentos(1, Instant.now());
            }
            return new Intentos(i.conteo() + 1, i.desde());
        });
    }

    public void limpiarFallos(String clave) {
        intentosFallidos.remove(clave);
    }
}
