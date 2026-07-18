# Sistema Cochera — demo separado (HTML + Java + MySQL)

## Estructura

```
cochera/
├── cochera.sql                 <- base de datos MySQL
├── pom.xml
└── src/main/
    ├── java/pe/cochera/
    │   └── CocheraApp.java     <- backend: API admin + API usuario
    └── resources/
        ├── application.properties   <- usuario y clave de MySQL
        └── static/
            ├── admin.html      <- PÁGINA 1: panel del admin
            └── usuario.html    <- PÁGINA 2: registro del usuario
```

## Pasos

1. **Crear la base**
   ```
   mysql -u root -p < cochera.sql
   ```

2. **Poner tu clave de MySQL** en `src/main/resources/application.properties`
   (línea `spring.datasource.password=`).

3. **Levantar el backend**
   ```
   mvn spring-boot:run
   ```

4. **Abrir las páginas** (ya las sirve el mismo servidor):
   - Usuario: http://localhost:8080/usuario.html
   - Admin:   http://localhost:8080/admin.html

Ábrelas en dos ventanas: cuando el usuario avisa su llegada, el panel del
admin lo muestra en menos de 5 segundos.

## Cómo funciona el cupo máximo (el `while (maximo >= 3)`)

Se valida en dos capas:
- **Java** — `UsuarioController.reservar()` no inserta si `ocupados >= cupoMaximo`;
  devuelve HTTP 409 y registra la alerta CUPO_LLENO.
- **MySQL** — el trigger `trg_reserva_cupo` bloquea el INSERT igual, aunque
  alguien escriba directo en la base.

El admin puede cambiar el máximo desde la tarjeta "CANTIDAD MÁXIMA DE AUTOS".

## Endpoints (los mismos que usará la app móvil)

| Método | Ruta | Para qué |
|---|---|---|
| GET  | /api/usuario/cupo | ¿hay espacio? |
| POST | /api/usuario/reservar | registro + aviso de hora |
| GET  | /api/admin/dashboard | usuarios, cupo, ocupados, libres |
| GET  | /api/admin/movimientos | horas de aviso, ingreso y salida |
| GET  | /api/admin/notificaciones | alertas |
| PUT  | /api/admin/cupo/{n} | cambiar máximo |
| POST | /api/admin/reservas/{id}/ingreso | marcar ingreso |
| POST | /api/admin/reservas/{id}/salida | marcar salida |
