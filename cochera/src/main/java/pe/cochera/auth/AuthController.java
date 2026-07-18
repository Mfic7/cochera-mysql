package pe.cochera.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import pe.cochera.seguridad.RateLimiter;
import pe.cochera.sesion.Sesion;
import pe.cochera.sesion.SesionService;
import pe.cochera.usuario.UsuarioRow;
import pe.cochera.usuario.UsuarioService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int MAX_ADMINS = 3;

    private final UsuarioService usuarios;
    private final SesionService sesiones;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder encoder;

    public AuthController(UsuarioService usuarios, SesionService sesiones, RateLimiter rateLimiter, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.rateLimiter = rateLimiter;
        this.encoder = encoder;
    }

    @PostMapping("/login/admin")
    public ResponseEntity<?> loginAdmin(@RequestBody CredencialesIn in) {
        return login(in, "ADMIN");
    }

    @PostMapping("/login/usuario")
    public ResponseEntity<?> loginUsuario(@RequestBody CredencialesIn in) {
        return login(in, "USUARIO");
    }

    private ResponseEntity<?> login(CredencialesIn in, String rol) {
        String claveIntentos = "login:" + rol + ":" + in.dni;
        if (rateLimiter.bloqueado(claveIntentos)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo."));
        }

        Optional<UsuarioRow> filaOpt = usuarios.buscarPorDniYRol(in.dni, rol);
        if (filaOpt.isEmpty() || !encoder.matches(
                in.password == null ? "" : in.password, filaOpt.get().password())) {
            rateLimiter.registrarFallo(claveIntentos);
            return ResponseEntity.status(401).body(Map.of("error", "DNI o contraseña incorrectos."));
        }
        rateLimiter.limpiarFallos(claveIntentos);
        UsuarioRow u = filaOpt.get();
        String token = sesiones.crear(u.id(), u.nombre(), rol);
        return ResponseEntity.ok(Map.of("token", token, "nombre", u.nombre()));
    }

    @PostMapping("/registro")
    public ResponseEntity<?> registro(@RequestBody RegistroIn in) {
        ResponseEntity<?> err = validarNuevaCuenta(in);
        if (err != null) return err;

        int id = usuarios.crear(in, "USUARIO");
        String token = sesiones.crear(id, in.nombre, "USUARIO");
        return ResponseEntity.ok(Map.of("token", token, "nombre", in.nombre));
    }

    /**
     * Crear un administrador nuevo desde el panel. Requiere sesión ADMIN activa
     * (no es autoservicio público) y respeta el tope de MAX_ADMINS cuentas ADMIN.
     */
    @PostMapping("/crear-admin")
    public ResponseEntity<?> crearAdmin(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody RegistroIn in) {
        sesiones.validar(auth, "ADMIN");

        if (usuarios.totalAdmins() >= MAX_ADMINS) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Ya existen " + MAX_ADMINS + " administradores (máximo permitido)."));
        }

        ResponseEntity<?> err = validarNuevaCuenta(in);
        if (err != null) return err;

        usuarios.crear(in, "ADMIN");
        return ResponseEntity.ok(Map.of("ok", true, "nombre", in.nombre));
    }

    /** El admin crea directamente una cuenta de cliente (sin autoservicio ni contraseña que recordar ahora mismo). */
    @PostMapping("/crear-usuario")
    public ResponseEntity<?> crearUsuarioAdmin(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @RequestBody RegistroIn in) {
        sesiones.validar(auth, "ADMIN");
        ResponseEntity<?> err = validarNuevaCuenta(in);
        if (err != null) return err;
        usuarios.crear(in, "USUARIO");
        return ResponseEntity.ok(Map.of("ok", true, "nombre", in.nombre));
    }

    private ResponseEntity<?> validarNuevaCuenta(RegistroIn in) {
        if (in.password == null || in.password.length() < 4) {
            return ResponseEntity.status(400).body(Map.of("error", "La contraseña debe tener al menos 4 caracteres."));
        }
        if (usuarios.existeDni(in.dni)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe una cuenta con ese DNI."));
        }
        if (usuarios.existeCorreo(in.correo)) {
            return ResponseEntity.status(409).body(Map.of("error", "Ya existe una cuenta con ese correo."));
        }
        return null;
    }

    /**
     * Recuperar contraseña sin correo: el demo no tiene servidor de email, así que
     * se verifica identidad con DNI + correo + celular (los mismos datos del registro)
     * antes de dejar poner una contraseña nueva. Al cambiarla se cierran las sesiones activas.
     */
    @PostMapping("/recuperar")
    public ResponseEntity<?> recuperar(@RequestBody RecuperarIn in) {
        String rol = "ADMIN".equalsIgnoreCase(in.rol) ? "ADMIN" : "USUARIO";
        String claveIntentos = "recuperar:" + rol + ":" + in.dni;
        if (rateLimiter.bloqueado(claveIntentos)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo."));
        }

        if (in.passwordNueva == null || in.passwordNueva.length() < 4) {
            return ResponseEntity.status(400).body(Map.of("error", "La nueva contraseña debe tener al menos 4 caracteres."));
        }

        Optional<Integer> idOpt = usuarios.buscarParaRecuperar(in.dni, in.correo, in.celular, rol);
        if (idOpt.isEmpty()) {
            rateLimiter.registrarFallo(claveIntentos);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Los datos no coinciden con ninguna cuenta de " +
                             (rol.equals("ADMIN") ? "administrador" : "usuario") + "."));
        }
        rateLimiter.limpiarFallos(claveIntentos);

        int id = idOpt.get();
        usuarios.actualizarPassword(id, in.passwordNueva);
        sesiones.invalidarSesionesDe(id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) sesiones.invalidar(auth.substring(7));
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        Sesion s = sesiones.validar(auth, null);
        return Map.of("nombre", s.nombre(), "rol", s.rol());
    }
}
