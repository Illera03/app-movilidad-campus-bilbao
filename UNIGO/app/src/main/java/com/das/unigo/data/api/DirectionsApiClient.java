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
        void onError(String errorMessage);
    }

    public void getRoute(double latOrigen, double lngOrigen, double latDest, double lngDest, String mode, String apiKey, RouteCallback callback) {
        executor.execute(() -> {
            try {
                String urlString = "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=" + latOrigen + "," + lngOrigen +
                        "&destination=" + latDest + "," + lngDest +
                        "&mode=" + mode +
                        "&key=" + apiKey;

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray routes = jsonObject.getJSONArray("routes");

                    if (routes.length() > 0) {
                        JSONObject firstRoute = routes.getJSONObject(0);
                        JSONObject firstLeg = firstRoute.getJSONArray("legs").getJSONObject(0);

                        // Extraemos el tiempo estimado general
                        String durationText = firstLeg.getJSONObject("duration").getString("text");

                        // Extraemos la ruta paso a paso
                        List<LatLng> rutaDetallada = new ArrayList<>();
                        JSONArray steps = firstLeg.getJSONArray("steps");

                        for (int i = 0; i < steps.length(); i++) {
                            JSONObject step = steps.getJSONObject(i);
                            String polylineFragment = step.getJSONObject("polyline").getString("points");

                            // Decodificamos el fragmento y lo añadimos a la lista maestra
                            rutaDetallada.addAll(PolylineDecoder.decode(polylineFragment));
                        }

                        // Devolvemos la ruta detallada al hilo principal
                        mainHandler.post(() -> callback.onSuccess(rutaDetallada, durationText));
                    } else {
                        mainHandler.post(() -> callback.onError("No se encontraron rutas"));
                    }
                } else {
                    mainHandler.post(() -> {
                        try {
                            callback.onError("Error de conexión: " + conn.getResponseCode());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onError("Excepción: " + e.getMessage()));
            }
        });
    }
}