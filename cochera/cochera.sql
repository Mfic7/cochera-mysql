-- =========================================================
--  SISTEMA COCHERA - Base de datos demo (MySQL 8)
--  Ejecutar:  mysql -u root -p < cochera.sql
-- =========================================================
DROP DATABASE IF EXISTS cochera;
CREATE DATABASE cochera CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cochera;

-- ---------------------------------------------------------
-- Sedes (locales físicos). El sistema opera varias sedes
-- desde el mismo panel de admin; cada una tiene su propio cupo.
-- ---------------------------------------------------------
CREATE TABLE sede (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  nombre      VARCHAR(80) NOT NULL,
  cupo_maximo INT NOT NULL DEFAULT 10
) ENGINE=InnoDB;

INSERT INTO sede (id, nombre, cupo_maximo) VALUES
 (1, 'Cochera 1', 10),
 (2, 'Cochera 2', 10);

-- ---------------------------------------------------------
-- Tipos de vehículo y su precio por día (catálogo editable
-- por el admin: motos lineales, trimotos, camionetas, etc.
-- pagan distinto). Mismo precio en ambas sedes.
-- ---------------------------------------------------------
CREATE TABLE tipo_vehiculo (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  nombre     VARCHAR(40) NOT NULL UNIQUE,
  precio_dia DECIMAL(6,2) NOT NULL
) ENGINE=InnoDB;

INSERT INTO tipo_vehiculo (id, nombre, precio_dia) VALUES
 (1, 'Auto', 5.00),
 (2, 'Camioneta', 8.00),
 (3, 'Moto lineal', 3.00),
 (4, 'Trimoto', 4.00);

-- ---------------------------------------------------------
-- Usuarios (registro: nombre, DNI, celular, correo)
-- ---------------------------------------------------------
CREATE TABLE usuario (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  nombre     VARCHAR(100) NOT NULL,
  dni        CHAR(8)      NOT NULL UNIQUE,
  celular    VARCHAR(15)  NOT NULL,
  correo     VARCHAR(120) NOT NULL UNIQUE,
  password   VARCHAR(100) NOT NULL DEFAULT '',   -- hash BCrypt; se autocompleta al iniciar la app
  rol        ENUM('ADMIN','USUARIO') NOT NULL DEFAULT 'USUARIO',
  creado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Vehículos (un usuario puede tener varias placas)
-- ---------------------------------------------------------
CREATE TABLE vehiculo (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id       INT NOT NULL,
  placa            VARCHAR(10) NOT NULL UNIQUE,
  modelo           VARCHAR(60),
  tipo_vehiculo_id INT NOT NULL DEFAULT 1,
  FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
  FOREIGN KEY (tipo_vehiculo_id) REFERENCES tipo_vehiculo(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Reservas: el usuario avisa "llego a las 10:00" a una sede.
--   fecha_inicio = fecha_fin  -> reserva normal de un solo día.
--   fecha_fin > fecha_inicio  -> "viaje": deja el carro varios días.
--   estado: PENDIENTE -> EN_COCHERA -> SALIO | CANCELADA
-- ---------------------------------------------------------
CREATE TABLE reserva (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  usuario_id     INT NOT NULL,
  vehiculo_id    INT NOT NULL,
  sede_id        INT NOT NULL DEFAULT 1,
  fecha_inicio   DATE NOT NULL,
  fecha_fin      DATE NOT NULL,
  hora_llegada   TIME NOT NULL,          -- hora que el usuario avisó
  hora_ingreso   DATETIME NULL,          -- hora real de ingreso
  hora_salida    DATETIME NULL,
  estado         ENUM('PENDIENTE','EN_COCHERA','SALIO','CANCELADA')
                 NOT NULL DEFAULT 'PENDIENTE',
  creado_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (usuario_id)  REFERENCES usuario(id),
  FOREIGN KEY (vehiculo_id) REFERENCES vehiculo(id),
  FOREIGN KEY (sede_id)     REFERENCES sede(id),
  INDEX idx_sede_estado_rango (sede_id, estado, fecha_inicio, fecha_fin)
) ENGINE=InnoDB;

-- ---------------------------------------------------------
-- Notificaciones al admin (alertas del sistema, por sede)
-- ---------------------------------------------------------
CREATE TABLE notificacion (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  reserva_id  INT NULL,
  sede_id     INT NULL,
  tipo        ENUM('LLEGADA','INGRESO','SALIDA','CUPO_LLENO','RECHAZO','RECORDATORIO') NOT NULL,
  mensaje     VARCHAR(255) NOT NULL,
  leida       TINYINT(1) NOT NULL DEFAULT 0,
  creado_en   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (reserva_id) REFERENCES reserva(id) ON DELETE SET NULL,
  FOREIGN KEY (sede_id) REFERENCES sede(id) ON DELETE SET NULL,
  INDEX idx_creado_en (creado_en)
) ENGINE=InnoDB;

-- =========================================================
--  REGLA DEL CUPO (por sede):  do while (ocupados < maximo)
--  Se aplica en el servidor (Java) y también con este trigger.
-- =========================================================
DELIMITER $$
CREATE TRIGGER trg_reserva_cupo
BEFORE INSERT ON reserva
FOR EACH ROW
BEGIN
  DECLARE v_ocupados INT;
  DECLARE v_maximo   INT;

  SELECT cupo_maximo INTO v_maximo FROM sede WHERE id = NEW.sede_id;

  SELECT COUNT(*) INTO v_ocupados
    FROM reserva
   WHERE sede_id = NEW.sede_id
     AND estado IN ('PENDIENTE','EN_COCHERA')
     AND fecha_inicio <= NEW.fecha_inicio
     AND fecha_fin >= NEW.fecha_inicio;

  IF v_ocupados >= v_maximo THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Cochera llena: no hay cupo disponible';
  END IF;
END$$
DELIMITER ;

-- =========================================================
--  VISTA PARA EL DASHBOARD DEL ADMIN
-- =========================================================
CREATE VIEW v_total_usuarios AS
SELECT COUNT(*) AS total_usuarios FROM usuario WHERE rol = 'USUARIO';

-- =========================================================
--  DATOS DE PRUEBA
-- =========================================================
INSERT INTO usuario (nombre, dni, celular, correo, rol) VALUES
 ('Admin Cochera', '00000000', '999000000', 'admin@cochera.pe', 'ADMIN'),
 ('Luis Ramos',    '45871236', '987654321', 'luis@mail.com',   'USUARIO'),
 ('Ana Torres',    '70112458', '912345678', 'ana@mail.com',    'USUARIO'),
 ('Carlos Díaz',   '41236987', '955112233', 'carlos@mail.com', 'USUARIO');

INSERT INTO vehiculo (usuario_id, placa, modelo) VALUES
 (2, 'ABC-123', 'Toyota Yaris'),
 (3, 'XYZ-987', 'Kia Rio'),
 (4, 'PIU-555', 'Hyundai Accent');

INSERT INTO reserva (usuario_id, vehiculo_id, sede_id, fecha_inicio, fecha_fin, hora_llegada, estado) VALUES
 (2, 1, 1, CURDATE(), CURDATE(), '09:00:00', 'EN_COCHERA'),
 (3, 2, 1, CURDATE(), CURDATE(), '10:00:00', 'PENDIENTE');

UPDATE reserva SET hora_ingreso = CONCAT(CURDATE(),' 09:04:00') WHERE id = 1;

INSERT INTO notificacion (reserva_id, sede_id, tipo, mensaje) VALUES
 (1, 1, 'INGRESO', 'Luis Ramos (ABC-123) ingresó 09:04'),
 (2, 1, 'LLEGADA', 'Ana Torres (XYZ-987) avisó llegada a las 10:00');
