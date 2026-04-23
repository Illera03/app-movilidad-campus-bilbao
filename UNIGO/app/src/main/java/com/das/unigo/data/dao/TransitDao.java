package com.das.unigo.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.das.unigo.data.entity.*;

import java.util.List;

/**
 * DAO principal para consultas de transporte y routing.
 * Incluye inserciones masivas y queries optimizadas para el algoritmo de pathfinding.
 */
@Dao
public interface TransitDao {

    // ========================
    // INSERCIONES MASIVAS
    // ========================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAgencies(List<AgencyEntity> agencies);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRoutes(List<RouteEntity> routes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStops(List<StopEntity> stops);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTrips(List<TripEntity> trips);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStopTimes(List<StopTimeEntity> stopTimes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertShapes(List<ShapeEntity> shapes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCalendars(List<CalendarEntity> calendars);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCalendarDates(List<CalendarDateEntity> calendarDates);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransfers(List<TransferEntity> transfers);

    // ========================
    // QUERIES DE ROUTING
    // ========================

    /**
     * Obtiene los service_ids activos para una fecha y día de la semana.
     * Combina el patrón semanal de calendar con las excepciones de calendar_dates.
     *
     * @param dayColumn nombre del día ("monday", "tuesday", etc.) — se usa en la query dinámica
     * @param date      fecha en formato "YYYYMMDD"
     */
    @Query("SELECT service_id FROM calendar " +
           "WHERE :date >= start_date AND :date <= end_date " +
           "AND (" +
           "  (:dayOfWeek = 1 AND monday = 1) OR " +
           "  (:dayOfWeek = 2 AND tuesday = 1) OR " +
           "  (:dayOfWeek = 3 AND wednesday = 1) OR " +
           "  (:dayOfWeek = 4 AND thursday = 1) OR " +
           "  (:dayOfWeek = 5 AND friday = 1) OR " +
           "  (:dayOfWeek = 6 AND saturday = 1) OR " +
           "  (:dayOfWeek = 7 AND sunday = 1)" +
           ") " +
           "AND service_id NOT IN (" +
           "  SELECT service_id FROM calendar_dates WHERE date = :date AND exception_type = 2" +
           ") " +
           "UNION " +
           "SELECT service_id FROM calendar_dates " +
           "WHERE date = :date AND exception_type = 1")
    List<String> getActiveServiceIds(int dayOfWeek, String date);

    /**
     * Paradas ordenadas de un viaje.
     */
    @Query("SELECT * FROM stop_times WHERE trip_id = :tripId ORDER BY stop_sequence ASC")
    List<StopTimeEntity> getStopTimesForTrip(String tripId);

    /**
     * Viajes de una ruta para un service_id concreto.
     */
    @Query("SELECT * FROM trips WHERE route_id = :routeId AND service_id = :serviceId")
    List<TripEntity> getTripsForRouteAndService(String routeId, String serviceId);

    /**
     * Busca paradas cercanas a un punto geográfico dentro de un radio aproximado.
     * Usa un bounding box para filtrar rápidamente antes de calcular distancia exacta.
     *
     * @param lat    Latitud del punto de referencia
     * @param lon    Longitud del punto de referencia
     * @param latDelta Delta de latitud (~0.0045 para 500m)
     * @param lonDelta Delta de longitud (~0.006 para 500m en Bilbao)
     */
    @Query("SELECT * FROM stops " +
           "WHERE stop_lat BETWEEN (:lat - :latDelta) AND (:lat + :latDelta) " +
           "AND stop_lon BETWEEN (:lon - :lonDelta) AND (:lon + :lonDelta)")
    List<StopEntity> getNearbyStops(double lat, double lon, double latDelta, double lonDelta);

    /**
     * Transbordos disponibles desde una parada.
     */
    @Query("SELECT * FROM transfers WHERE from_stop_id = :stopId")
    List<TransferEntity> getTransfersFromStop(String stopId);

    /**
     * Próximas salidas desde una parada a partir de una hora, para service_ids activos.
     * Devuelve los stop_times con sus trip_ids para saber qué línea pasa.
     */
    @Query("SELECT st.* FROM stop_times st " +
           "INNER JOIN trips t ON st.trip_id = t.trip_id " +
           "WHERE st.stop_id = :stopId " +
           "AND st.departure_time >= :time " +
           "AND t.service_id IN (:activeServiceIds) " +
           "ORDER BY st.departure_time ASC " +
           "LIMIT :limit")
    List<StopTimeEntity> getNextDepartures(String stopId, String time,
                                           List<String> activeServiceIds, int limit);

    /**
     * Obtiene todas las paradas de un tipo de nodo (para listados).
     */
    @Query("SELECT * FROM stops WHERE node_type = :nodeType ORDER BY stop_name ASC")
    List<StopEntity> getStopsByNodeType(String nodeType);

    /**
     * Obtiene todos los centros universitarios.
     */
    @Query("SELECT * FROM stops WHERE node_type = 'CAMPUS' ORDER BY stop_name ASC")
    List<StopEntity> getCampusStops();

    /**
     * Obtiene todas las rutas de una agencia.
     */
    @Query("SELECT * FROM routes WHERE agency_id = :agencyId ORDER BY route_short_name ASC")
    List<RouteEntity> getRoutesByAgency(String agencyId);

    /**
     * Obtiene los puntos de shape para dibujar una ruta en el mapa.
     */
    @Query("SELECT * FROM shapes WHERE shape_id = :shapeId ORDER BY shape_pt_sequence ASC")
    List<ShapeEntity> getShapePoints(String shapeId);

    /**
     * Obtiene una parada por su ID.
     */
    @Query("SELECT * FROM stops WHERE stop_id = :stopId LIMIT 1")
    StopEntity getStopById(String stopId);

    /**
     * Obtiene la ruta asociada a un trip.
     */
    @Query("SELECT r.* FROM routes r " +
           "INNER JOIN trips t ON r.route_id = t.route_id " +
           "WHERE t.trip_id = :tripId LIMIT 1")
    RouteEntity getRouteForTrip(String tripId);

    // ========================
    // CONTADORES (para verificación)
    // ========================

    @Query("SELECT COUNT(*) FROM stops")
    int getStopCount();

    @Query("SELECT COUNT(*) FROM routes")
    int getRouteCount();

    @Query("SELECT COUNT(*) FROM trips")
    int getTripCount();

    @Query("SELECT COUNT(*) FROM stop_times")
    int getStopTimeCount();

    @Query("SELECT COUNT(*) FROM transfers")
    int getTransferCount();

    @Query("SELECT COUNT(*) FROM calendar")
    int getCalendarCount();

    @Query("SELECT COUNT(*) FROM calendar_dates")
    int getCalendarDateCount();
}
