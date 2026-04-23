package com.das.unigo.data.importer;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.das.unigo.data.TransitDatabase;
import com.das.unigo.data.dao.TransitDao;
import com.das.unigo.data.entity.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestor de importación de datos de transporte a Room.
 *
 * Lee los archivos CSV (GTFS) y JSON (bici/centros) desde la carpeta assets/
 * y los inserta en la base de datos con el prefijado de IDs adecuado.
 *
 * Estructura esperada en assets/:
 *   assets/
 *     bilbobus_gtfs/   → routes.csv, stops.csv, trips.csv, stop_times.csv,
 *                        shapes.csv, calendar.txt, calendar_dates.txt
 *     bizkaibus_gtfs/  → routes.csv, stops.csv, trips.csv, stop_times.csv,
 *                        shapes.csv, calendar.csv, calendar_dates.csv
 *     bici/            → EstacionesPrestamo.json
 *     centros/         → centros.json
 *
 * Uso:
 *   DataImportManager manager = new DataImportManager(context);
 *   manager.importAllData();  // Ejecutar en hilo de fondo
 */
public class DataImportManager {

    private static final String TAG = "DataImportManager";

    // Tamaño de lote para inserciones masivas (evita OutOfMemoryError)
    private static final int BATCH_SIZE = 1000;

    // Prefijos para evitar colisiones de IDs entre agencias
    private static final String PREFIX_BILBOBUS = "BB_";
    private static final String PREFIX_BIZKAIBUS = "BK_";
    private static final String PREFIX_BIKE = "BICI_";
    private static final String PREFIX_CAMPUS = "UNI_";

    private final Context context;
    private final TransitDao dao;

    public interface ImportProgressListener {
        void onProgress(String stage, int current, int total);
        void onComplete();
        void onError(String message);
    }

    private ImportProgressListener listener;

    public DataImportManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.dao = TransitDatabase.getInstance(this.context).transitDao();
    }

    public void setProgressListener(ImportProgressListener listener) {
        this.listener = listener;
    }

    /**
     * Importa todos los datos. DEBE ejecutarse en un hilo de fondo.
     */
    public void importAllData() {
        try {
            long start = System.currentTimeMillis();

            // 1. Agencias
            notifyProgress("Insertando agencias", 0, 8);
            insertAgencies();

            // 2. Bilbobus GTFS
            notifyProgress("Importando Bilbobus", 1, 8);
            importGtfs("bilbobus_gtfs", PREFIX_BILBOBUS, "BILBOBUS", "BUS_BILBOBUS");

            // 3. Bizkaibus GTFS
            notifyProgress("Importando Bizkaibus", 2, 8);
            importGtfs("bizkaibus_gtfs", PREFIX_BIZKAIBUS, "BIZKAIBUS", "BUS_BIZKAIBUS");

            // 4. Estaciones de bici
            notifyProgress("Importando estaciones de bici", 6, 8);
            importBikeStations();

            // 5. Centros universitarios
            notifyProgress("Importando centros universitarios", 7, 8);
            importCampusStops();

            long elapsed = (System.currentTimeMillis() - start) / 1000;
            Log.i(TAG, "Importación completada en " + elapsed + "s");
            Log.i(TAG, "Stops: " + dao.getStopCount() +
                    ", Routes: " + dao.getRouteCount() +
                    ", Trips: " + dao.getTripCount() +
                    ", StopTimes: " + dao.getStopTimeCount() +
                    ", Calendar: " + dao.getCalendarCount() +
                    ", CalendarDates: " + dao.getCalendarDateCount());

            if (listener != null) listener.onComplete();

        } catch (Exception e) {
            Log.e(TAG, "Error en importación", e);
            if (listener != null) listener.onError(e.getMessage());
        }
    }

    // ================================================================
    // AGENCIAS
    // ================================================================

    private void insertAgencies() {
        List<AgencyEntity> agencies = new ArrayList<>();
        agencies.add(new AgencyEntity("BILBOBUS", "Bilbobus", "BUS"));
        agencies.add(new AgencyEntity("BIZKAIBUS", "Bizkaibus", "BUS"));
        agencies.add(new AgencyEntity("BILBONBIZI", "Bilbonbizi", "BIKE"));
        agencies.add(new AgencyEntity("UNIVERSIDAD", "Centros Universitarios", "CAMPUS"));
        dao.insertAgencies(agencies);
    }

    // ================================================================
    // GTFS (Bilbobus / Bizkaibus)
    // ================================================================

    private void importGtfs(String folder, String prefix, String agencyId, String nodeType)
            throws IOException {

        importRoutes(folder, prefix, agencyId);
        importStops(folder, prefix, nodeType);
        importCalendar(folder, prefix);
        importCalendarDates(folder, prefix);
        importTrips(folder, prefix);
        importStopTimes(folder, prefix);
        importShapes(folder, prefix);
    }

    private void importRoutes(String folder, String prefix, String agencyId) throws IOException {
        List<RouteEntity> batch = new ArrayList<>();
        parseCSV(folder + "/routes.csv", row -> {
            RouteEntity r = new RouteEntity();
            r.routeId = prefix + row.get("route_id");
            r.agencyId = agencyId;
            r.routeShortName = row.get("route_short_name");
            r.routeLongName = row.get("route_long_name");
            r.routeType = safeInt(row.get("route_type"), 3);
            r.routeColor = row.get("route_color");
            r.routeTextColor = row.get("route_text_color");
            batch.add(r);

            if (batch.size() >= BATCH_SIZE) {
                dao.insertRoutes(new ArrayList<>(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) dao.insertRoutes(batch);
        Log.d(TAG, prefix + "routes importadas");
    }

    private void importStops(String folder, String prefix, String nodeType) throws IOException {
        List<StopEntity> batch = new ArrayList<>();
        parseCSV(folder + "/stops.csv", row -> {
            StopEntity s = new StopEntity();
            s.stopId = prefix + row.get("stop_id");
            s.stopCode = row.get("stop_code");
            s.stopName = row.get("stop_name");
            s.stopLat = safeDouble(row.get("stop_lat"), 0);
            s.stopLon = safeDouble(row.get("stop_lon"), 0);
            s.locationType = safeInt(row.get("location_type"), 0);
            String parent = row.get("parent_station");
            s.parentStation = (parent != null && !parent.isEmpty()) ? prefix + parent : null;
            s.nodeType = nodeType;
            batch.add(s);

            if (batch.size() >= BATCH_SIZE) {
                dao.insertStops(new ArrayList<>(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) dao.insertStops(batch);
        Log.d(TAG, prefix + "stops importadas");
    }

    private void importTrips(String folder, String prefix) throws IOException {
        List<TripEntity> batch = new ArrayList<>();
        parseCSV(folder + "/trips.csv", row -> {
            TripEntity t = new TripEntity();
            t.tripId = prefix + row.get("trip_id");
            t.routeId = prefix + row.get("route_id");
            t.serviceId = prefix + row.get("service_id");
            t.tripHeadsign = row.get("trip_headsign");
            t.directionId = safeInt(row.get("direction_id"), 0);
            String shapeId = row.get("shape_id");
            t.shapeId = (shapeId != null && !shapeId.isEmpty()) ? prefix + shapeId : null;
            t.wheelchairAccessible = safeInt(row.get("wheelchair_accessible"), 0);
            batch.add(t);

            if (batch.size() >= BATCH_SIZE) {
                dao.insertTrips(new ArrayList<>(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) dao.insertTrips(batch);
        Log.d(TAG, prefix + "trips importados");
    }

    private void importStopTimes(String folder, String prefix) throws IOException {
        List<StopTimeEntity> batch = new ArrayList<>();
        int count = 0;
        final int[] countWrapper = {0};

        parseCSV(folder + "/stop_times.csv", row -> {
            StopTimeEntity st = new StopTimeEntity();
            st.tripId = prefix + row.get("trip_id");
            st.arrivalTime = row.get("arrival_time");
            st.departureTime = row.get("departure_time");
            st.stopId = prefix + row.get("stop_id");
            st.stopSequence = safeInt(row.get("stop_sequence"), 0);
            st.pickupType = safeInt(row.get("pickup_type"), 0);
            st.dropOffType = safeInt(row.get("drop_off_type"), 0);
            batch.add(st);
            countWrapper[0]++;

            if (batch.size() >= BATCH_SIZE) {
                dao.insertStopTimes(new ArrayList<>(batch));
                batch.clear();
                if (countWrapper[0] % 50000 == 0) {
                    Log.d(TAG, prefix + "stop_times: " + countWrapper[0] + " filas...");
                }
            }
        });
        if (!batch.isEmpty()) dao.insertStopTimes(batch);
        Log.d(TAG, prefix + "stop_times importados: " + countWrapper[0] + " filas");
    }

    private void importShapes(String folder, String prefix) throws IOException {
        List<ShapeEntity> batch = new ArrayList<>();
        final int[] countWrapper = {0};

        parseCSV(folder + "/shapes.csv", row -> {
            ShapeEntity sh = new ShapeEntity();
            sh.shapeId = prefix + row.get("shape_id");
            sh.shapePtLat = safeDouble(row.get("shape_pt_lat"), 0);
            sh.shapePtLon = safeDouble(row.get("shape_pt_lon"), 0);
            sh.shapePtSequence = safeInt(row.get("shape_pt_sequence"), 0);
            sh.shapeDistTraveled = safeDouble(row.get("shape_dist_traveled"), 0);
            batch.add(sh);
            countWrapper[0]++;

            if (batch.size() >= BATCH_SIZE) {
                dao.insertShapes(new ArrayList<>(batch));
                batch.clear();
                if (countWrapper[0] % 100000 == 0) {
                    Log.d(TAG, prefix + "shapes: " + countWrapper[0] + " puntos...");
                }
            }
        });
        if (!batch.isEmpty()) dao.insertShapes(batch);
        Log.d(TAG, prefix + "shapes importados: " + countWrapper[0] + " puntos");
    }

    private void importCalendar(String folder, String prefix) throws IOException {
        // Bilbobus usa .txt, Bizkaibus usa .csv — intentamos ambos
        String filename = tryFindFile(folder, "calendar");
        if (filename == null) {
            Log.w(TAG, prefix + "No se encontró archivo de calendar");
            return;
        }

        List<CalendarEntity> batch = new ArrayList<>();
        parseCSV(filename, row -> {
            CalendarEntity c = new CalendarEntity();
            c.serviceId = prefix + row.get("service_id");
            c.monday = safeInt(row.get("monday"), 0);
            c.tuesday = safeInt(row.get("tuesday"), 0);
            c.wednesday = safeInt(row.get("wednesday"), 0);
            c.thursday = safeInt(row.get("thursday"), 0);
            c.friday = safeInt(row.get("friday"), 0);
            c.saturday = safeInt(row.get("saturday"), 0);
            c.sunday = safeInt(row.get("sunday"), 0);
            c.startDate = row.get("start_date");
            c.endDate = row.get("end_date");
            batch.add(c);
        });
        if (!batch.isEmpty()) dao.insertCalendars(batch);
        Log.d(TAG, prefix + "calendar importado: " + batch.size() + " servicios");
    }

    private void importCalendarDates(String folder, String prefix) throws IOException {
        String filename = tryFindFile(folder, "calendar_dates");
        if (filename == null) {
            Log.w(TAG, prefix + "No se encontró archivo de calendar_dates");
            return;
        }

        List<CalendarDateEntity> batch = new ArrayList<>();
        parseCSV(filename, row -> {
            String serviceId = row.get("service_id");
            String date = row.get("date");
            // Ignorar filas vacías (Bilbobus calendar_dates.txt está vacío)
            if (serviceId == null || serviceId.isEmpty() || date == null || date.isEmpty()) return;

            CalendarDateEntity cd = new CalendarDateEntity();
            cd.serviceId = prefix + serviceId;
            cd.date = date;
            cd.exceptionType = safeInt(row.get("exception_type"), 1);
            batch.add(cd);

            if (batch.size() >= BATCH_SIZE) {
                dao.insertCalendarDates(new ArrayList<>(batch));
                batch.clear();
            }
        });
        if (!batch.isEmpty()) dao.insertCalendarDates(batch);
        Log.d(TAG, prefix + "calendar_dates importado: " + batch.size() + " excepciones");
    }

    // ================================================================
    // ESTACIONES DE BICICLETA
    // ================================================================

    private void importBikeStations() throws IOException {
        String json = readAssetFile("bici/EstacionesPrestamo.json");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray features = root.getAsJsonArray("features");

        List<StopEntity> stops = new ArrayList<>();
        for (JsonElement elem : features) {
            JsonObject feature = elem.getAsJsonObject();
            JsonObject props = feature.getAsJsonObject("properties");
            JsonObject geometry = feature.getAsJsonObject("geometry");

            int id = props.get("Id").getAsInt();
            String name = props.get("NombreEstacion").getAsString();

            // Calcular centroide del polígono/multipolígono
            double[] centroid = calculateCentroid(geometry);

            StopEntity s = new StopEntity();
            s.stopId = PREFIX_BIKE + id;
            s.stopCode = String.valueOf(id);
            s.stopName = "Bilbonbizi - " + name;
            s.stopLat = centroid[0];
            s.stopLon = centroid[1];
            s.locationType = 0;
            s.parentStation = null;
            s.nodeType = "BIKE";
            stops.add(s);
        }
        dao.insertStops(stops);
        Log.d(TAG, "Estaciones de bici importadas: " + stops.size());
    }

    /**
     * Calcula el centroide (punto medio) de una geometría GeoJSON.
     * Soporta Polygon y MultiPolygon.
     *
     * @return [lat, lon]
     */
    private double[] calculateCentroid(JsonObject geometry) {
        String type = geometry.get("type").getAsString();
        JsonArray coordinates = geometry.getAsJsonArray("coordinates");

        double sumLat = 0, sumLon = 0;
        int count = 0;

        if ("Polygon".equals(type)) {
            // coordinates = [ [ [lon,lat], [lon,lat], ... ] ]
            JsonArray ring = coordinates.get(0).getAsJsonArray();
            for (JsonElement point : ring) {
                JsonArray coord = point.getAsJsonArray();
                sumLon += coord.get(0).getAsDouble();
                sumLat += coord.get(1).getAsDouble();
                count++;
            }
        } else if ("MultiPolygon".equals(type)) {
            // coordinates = [ [ [ [lon,lat], ... ] ], [ [ [lon,lat], ... ] ] ]
            for (JsonElement polygon : coordinates) {
                JsonArray ring = polygon.getAsJsonArray().get(0).getAsJsonArray();
                for (JsonElement point : ring) {
                    JsonArray coord = point.getAsJsonArray();
                    sumLon += coord.get(0).getAsDouble();
                    sumLat += coord.get(1).getAsDouble();
                    count++;
                }
            }
        }

        if (count == 0) return new double[]{0, 0};
        return new double[]{sumLat / count, sumLon / count};
    }

    // ================================================================
    // CENTROS UNIVERSITARIOS
    // ================================================================

    private void importCampusStops() throws IOException {
        String json = readAssetFile("centros/centros.json");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject destinos = root.getAsJsonObject("destinos_universitarios");

        List<StopEntity> stops = new ArrayList<>();
        int autoId = 0;

        for (Map.Entry<String, JsonElement> uni : destinos.entrySet()) {
            String uniName = uni.getKey(); // "EHU", "Mondragon", "Deusto"
            JsonArray centros = uni.getValue().getAsJsonArray();

            for (JsonElement elem : centros) {
                JsonObject centro = elem.getAsJsonObject();
                autoId++;

                // Algunos centros no tienen código (Mondragon, Deusto)
                String codigo;
                if (centro.has("codigo") && !centro.get("codigo").isJsonNull()) {
                    codigo = centro.get("codigo").getAsString();
                } else {
                    codigo = uniName.toUpperCase() + "_" + autoId;
                }

                StopEntity s = new StopEntity();
                s.stopId = PREFIX_CAMPUS + codigo;
                s.stopCode = codigo;
                s.stopName = uniName + " - " + centro.get("nombre").getAsString();
                s.stopLat = centro.get("latitud").getAsDouble();
                s.stopLon = centro.get("longitud").getAsDouble();
                s.locationType = 0;
                s.parentStation = null;
                s.nodeType = "CAMPUS";
                stops.add(s);
            }
        }
        dao.insertStops(stops);
        Log.d(TAG, "Centros universitarios importados: " + stops.size());
    }

    // ================================================================
    // PARSER CSV GENÉRICO CON CABECERAS
    // ================================================================

    @FunctionalInterface
    private interface RowConsumer {
        void accept(Map<String, String> row);
    }

    /**
     * Lee un CSV desde assets, detecta las cabeceras de la primera línea,
     * y emite cada fila como un Map<columna, valor>.
     * Soporta tanto .csv como .txt (mismo formato).
     */
    private void parseCSV(String assetPath, RowConsumer consumer) throws IOException {
        try (InputStream is = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {

            // Leer cabecera
            String headerLine = reader.readLine();
            if (headerLine == null) return;

            // Limpiar BOM si existe
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }
            headerLine = headerLine.trim().replace("\r", "");

            String[] headers = headerLine.split(",", -1);

            // Leer filas
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().replace("\r", "");
                if (line.isEmpty()) continue;

                String[] values = line.split(",", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    String val = values[i].trim();
                    row.put(headers[i].trim(), val.isEmpty() ? null : val);
                }
                consumer.accept(row);
            }
        }
    }

    /**
     * Busca un archivo en assets con extensión .csv o .txt.
     * Bilbobus usa .txt para calendar, Bizkaibus usa .csv.
     */
    private String tryFindFile(String folder, String baseName) {
        String[] extensions = {".csv", ".txt"};
        for (String ext : extensions) {
            String path = folder + "/" + baseName + ext;
            try {
                context.getAssets().open(path).close();
                return path;
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private String readAssetFile(String path) throws IOException {
        try (InputStream is = context.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    // ================================================================
    // UTILIDADES
    // ================================================================

    private static int safeInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double safeDouble(String value, double defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void notifyProgress(String stage, int current, int total) {
        Log.i(TAG, stage + " (" + current + "/" + total + ")");
        if (listener != null) listener.onProgress(stage, current, total);
    }
}
