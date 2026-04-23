package com.das.unigo.data.importer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.das.unigo.data.TransitDatabase;
import com.das.unigo.data.dao.TransitDao;
import com.das.unigo.data.entity.StopEntity;
import com.das.unigo.data.entity.TransferEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera las entidades TransferEntity (transbordos) pre-calculando
 * las distancias entre nodos cercanos del grafo de transporte.
 *
 * NOTA: No se generan transfers bus↔bus porque el routing solo considera
 * rutas de una sola línea de bus (sin transbordos entre líneas).
 *
 * Estrategia:
 * 1. Bus ↔ Bici — caminar de parada de bus a estación de bici
 * 2. Bus ↔ Campus — caminar de parada de bus a centro universitario
 * 3. Bici ↔ Campus — caminar de estación de bici a centro universitario
 *
 * Se generan transfers bidireccionales (A→B y B→A) para que el
 * algoritmo de routing pueda recorrer en ambas direcciones.
 *
 * Uso:
 *   TransferGenerator generator = new TransferGenerator(context);
 *   generator.generateTransfers();  // Ejecutar en hilo de fondo
 */
public class TransferGenerator {

    private static final String TAG = "TransferGenerator";

    // Radio máximo en metros para considerar un transbordo a pie
    private static final double MAX_WALK_RADIUS_METERS = 500.0;

    // Deltas de lat/lon para el bounding box (aproximado para latitud ~43° en Bilbao)
    // 1° latitud ≈ 111 km → 500m ≈ 0.0045°
    // 1° longitud a 43° ≈ 81 km → 500m ≈ 0.0062°
    private static final double LAT_DELTA = 0.0045;
    private static final double LON_DELTA = 0.0062;

    // Radio de la Tierra en metros (para fórmula Haversine)
    private static final double EARTH_RADIUS = 6_371_000;

    // Velocidad peatón en m/s (~5 km/h)
    private static final double WALK_SPEED = 1.4;

    private static final int BATCH_SIZE = 1000;

    private final TransitDao dao;

    public TransferGenerator(@NonNull Context context) {
        this.dao = TransitDatabase.getInstance(context.getApplicationContext()).transitDao();
    }

    /**
     * Genera todos los transbordos. DEBE ejecutarse en hilo de fondo.
     *
     * Solo genera transfers entre modos distintos (bus↔bici, bus↔campus, bici↔campus).
     * NO genera transfers bus↔bus porque el routing solo usa una línea de bus por viaje.
     */
    public void generateTransfers() {
        Log.i(TAG, "Iniciando generación de transfers...");
        long start = System.currentTimeMillis();

        // Obtener todos los nodos por tipo
        List<StopEntity> busStopsBB = dao.getStopsByNodeType("BUS_BILBOBUS");
        List<StopEntity> busStopsBK = dao.getStopsByNodeType("BUS_BIZKAIBUS");
        List<StopEntity> bikeStops = dao.getStopsByNodeType("BIKE");
        List<StopEntity> campusStops = dao.getStopsByNodeType("CAMPUS");

        Log.i(TAG, "Nodos: BB=" + busStopsBB.size() + " BK=" + busStopsBK.size()
                + " BICI=" + bikeStops.size() + " CAMPUS=" + campusStops.size());

        List<TransferEntity> allTransfers = new ArrayList<>();

        // 1. BUS ↔ BIKE (caminar de parada de bus a estación de bici)
        Log.d(TAG, "Generando transfers Bus ↔ Bici...");
        generateBetweenLists(busStopsBB, bikeStops, allTransfers);
        generateBetweenLists(busStopsBK, bikeStops, allTransfers);

        // 2. BUS ↔ CAMPUS (caminar de parada de bus a centro universitario)
        Log.d(TAG, "Generando transfers Bus ↔ Campus...");
        generateBetweenLists(busStopsBB, campusStops, allTransfers);
        generateBetweenLists(busStopsBK, campusStops, allTransfers);

        // 3. BIKE ↔ CAMPUS (caminar de estación de bici a centro universitario)
        Log.d(TAG, "Generando transfers Bici ↔ Campus...");
        generateBetweenLists(bikeStops, campusStops, allTransfers);

        // Insertar en lotes
        Log.i(TAG, "Insertando " + allTransfers.size() + " transfers...");
        for (int i = 0; i < allTransfers.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, allTransfers.size());
            dao.insertTransfers(allTransfers.subList(i, end));
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        Log.i(TAG, "Transfers generados: " + allTransfers.size() + " en " + elapsed + "s");
    }

    /**
     * Genera transfers bidireccionales entre dos listas de nodos distintos.
     */
    private void generateBetweenLists(List<StopEntity> listA, List<StopEntity> listB,
                                       List<TransferEntity> result) {
        for (StopEntity a : listA) {
            for (StopEntity b : listB) {
                // Filtro rápido por bounding box (evita cálculo Haversine innecesario)
                if (Math.abs(a.stopLat - b.stopLat) > LAT_DELTA) continue;
                if (Math.abs(a.stopLon - b.stopLon) > LON_DELTA) continue;

                double distance = haversine(a.stopLat, a.stopLon, b.stopLat, b.stopLon);
                if (distance <= MAX_WALK_RADIUS_METERS) {
                    int walkTime = (int) Math.ceil(distance / WALK_SPEED);

                    // A → B
                    TransferEntity t1 = new TransferEntity();
                    t1.fromStopId = a.stopId;
                    t1.toStopId = b.stopId;
                    t1.transferType = 2; // requiere tiempo mínimo
                    t1.minTransferTime = walkTime;
                    t1.distanceMeters = Math.round(distance * 10.0) / 10.0;
                    result.add(t1);

                    // B → A
                    TransferEntity t2 = new TransferEntity();
                    t2.fromStopId = b.stopId;
                    t2.toStopId = a.stopId;
                    t2.transferType = 2;
                    t2.minTransferTime = walkTime;
                    t2.distanceMeters = t1.distanceMeters;
                    result.add(t2);
                }
            }
        }
    }

    /**
     * Fórmula Haversine: calcula la distancia en metros entre dos puntos (lat/lon).
     */
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}
