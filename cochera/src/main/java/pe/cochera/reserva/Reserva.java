package pe.cochera.reserva;

/**
 * Cuerpo JSON del aviso de llegada. El usuario normal viene identificado por el token
 * (campo dni se ignora); cuando lo usa el admin para reservar a nombre de un cliente,
 * dni identifica a ese cliente.
 */
public class Reserva {
    public int sedeId, dias, tipoVehiculoId;
    public String placa, modelo, horaLlegada, dni;
}
