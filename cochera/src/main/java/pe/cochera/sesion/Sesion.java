package pe.cochera.sesion;

import java.time.Instant;

public record Sesion(int usuarioId, String nombre, String rol, Instant expira) {}
