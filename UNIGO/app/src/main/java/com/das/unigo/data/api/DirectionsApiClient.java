package com.das.unigo.data.api;

import android.os.Handler;
import android.os.Looper;

import com.das.unigo.utils.PolylineDecoder;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirectionsApiClient {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface RouteCallback {
        void onSuccess(List<LatLng> routeDecoded, String duration);
        void onComplexSuccess(List<LatLng> walk1, String d1, List<LatLng> bike, String d2, List<LatLng> walk2, String d3, String totalDuration);
        void onError(String errorMessage);
    }

    /**
     * Calcula la ruta multimodal (Andar -> X -> Andar) y suma los tiempos totales.
     */
    public void getComplexBikeRoute(double latOri, double lngOri, double latBici1, double lngBici1,
                                    double latBici2, double lngBici2, double latDest, double lngDest,
                                    String apiKey, RouteCallback callback) {
        executor.execute(() -> {
            try {
                RouteSyncRes r1 = fetchRouteSync(latOri, lngOri, latBici1, lngBici1, "walking", apiKey);
                RouteSyncRes r2 = fetchRouteSync(latBici1, lngBici1, latBici2, lngBici2, "bicycling", apiKey);
                RouteSyncRes r3 = fetchRouteSync(latBici2, lngBici2, latDest, lngDest, "walking", apiKey);

                if (r1.success && r2.success && r3.success) {
                    // Cálculo de la duración total del viaje sumando los tres tramos
                    int totalSeconds = r1.durationSeconds + r2.durationSeconds + r3.durationSeconds;
                    String totalDuration = Math.round((float) totalSeconds / 60) + " min";

                    mainHandler.post(() -> callback.onComplexSuccess(
                            r1.path, r1.durationText,
                            r2.path, r2.durationText,
                            r3.path, r3.durationText,
                            totalDuration));
                } else {
                    mainHandler.post(() -> callback.onError("Error al calcular la ruta multimodal"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Obtiene una ruta simple (Andando) desde Google Maps Directions API.
     */
    public void getRoute(double latOrigen, double lngOrigen, double latDest, double lngDest, String mode, String apiKey, RouteCallback callback) {
        executor.execute(() -> {
            try {
                RouteSyncRes result = fetchRouteSync(latOrigen, lngOrigen, latDest, lngDest, mode, apiKey);
                if (result.success) {
                    mainHandler.post(() -> callback.onSuccess(result.path, result.durationText));
                } else {
                    mainHandler.post(() -> callback.onError("No se encontraron rutas"));
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onError("Excepción: " + e.getMessage()));
            }
        });
    }


    // Estructura interna mejorada para incluir el texto de duración
    private static class RouteSyncRes {
        boolean success = false;
        List<LatLng> path = new ArrayList<>();
        int durationSeconds = 0;
        String durationText = "";
    }

    /**
     * Realiza una petición HTTP síncrona a la API de Directions.
     */
    private RouteSyncRes fetchRouteSync(double lat1, double lng1, double lat2, double lng2, String mode, String apiKey) throws Exception {
        RouteSyncRes res = new RouteSyncRes();
        String urlString = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=" + lat1 + "," + lng1 +
                "&destination=" + lat2 + "," + lng2 +
                "&mode=" + mode + "&key=" + apiKey;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);

            JSONObject jsonObject = new JSONObject(response.toString());
            JSONArray routes = jsonObject.getJSONArray("routes");

            if (routes.length() > 0) {
                JSONObject firstLeg = routes.getJSONObject(0).getJSONArray("legs").getJSONObject(0);

                // Capturamos tanto los segundos como el texto (ej: "12 min")
                res.durationSeconds = firstLeg.getJSONObject("duration").getInt("value");
                res.durationText = firstLeg.getJSONObject("duration").getString("text");

                JSONArray steps = firstLeg.getJSONArray("steps");
                for (int i = 0; i < steps.length(); i++) {
                    String poly = steps.getJSONObject(i).getJSONObject("polyline").getString("points");
                    res.path.addAll(PolylineDecoder.decode(poly));
                }
                res.success = true;
            }
        }
        conn.disconnect();
        return res;
    }
}