package pe.cochera.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class Fechas {

    private Fechas() {}

    /**
     * java.sql.Date.valueOf() parsea el LocalDate como texto ISO, sin pasar por ninguna
     * zona horaria — evita que el driver de MySQL corra la fecha un día si el timezone
     * de la JVM y el de la conexión (serverTimezone) no coinciden exactamente.
     */
    public static java.sql.Date sql(LocalDate d) {
        return java.sql.Date.valueOf(d);
    }

    public static String hhmm() {
        return LocalDateTime.now().toLocalTime().toString().substring(0, 5);
    }
}
