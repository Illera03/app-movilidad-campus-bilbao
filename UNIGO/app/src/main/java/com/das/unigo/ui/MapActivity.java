package com.das.unigo.ui;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.das.unigo.BuildConfig;
import com.das.unigo.utils.JwtUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.das.unigo.R;
import com.das.unigo.data.api.DirectionsApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // Variables que recibimos del MainActivity
    private double destLat;
    private double destLng;
    private String destNombre;
    private String modoTransporte;

    // Info bar views
    private TextView tvWeather;
    private TextView tvWeatherIcon;
    private TextView tvPollution;
    private TextView tvPollutionIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Recoger los datos del Intent
        if (getIntent() != null) {
            destLat = getIntent().getDoubleExtra("DESTINO_LAT", 0);
            destLng = getIntent().getDoubleExtra("DESTINO_LNG", 0);
            destNombre = getIntent().getStringExtra("DESTINO_NOMBRE");
            modoTransporte = getIntent().getStringExtra("MODO_TRANSPORTE");
        }

        // Info bar views
        tvWeather = findViewById(R.id.tv_weather);
        tvWeatherIcon = findViewById(R.id.tv_weather_icon);
        tvPollution = findViewById(R.id.tv_pollution);
        tvPollutionIcon = findViewById(R.id.tv_pollution_icon);

        findViewById(R.id.fab_back).setOnClickListener(v -> finish());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Obtener datos meteorológicos y de calidad del aire para Bilbao
        fetchWeatherAndAirQuality();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacionYTrazarRuta();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void obtenerUbicacionYTrazarRuta() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                configurarMapaYRuta(location.getLatitude(), location.getLongitude());
            } else {
                // Fallback: solicitar ubicación actual al GPS
                CancellationTokenSource cts = new CancellationTokenSource();
                fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY, cts.getToken()
                ).addOnSuccessListener(loc -> {
                    if (loc != null) {
                        configurarMapaYRuta(loc.getLatitude(), loc.getLongitude());
                    } else {
                        Toast.makeText(this, getString(R.string.error_ubicacion), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void configurarMapaYRuta(double lat, double lng) {
        mMap.clear(); // Limpiar el mapa antes de trazar nueva ruta o marcadores
        LatLng origen = new LatLng(lat, lng);
        LatLng destino = new LatLng(destLat, destLng);

        // Añadir marcador en el destino
        agregarMarcadorDestino(destino, destNombre);

        // Llamada al API Client
        llamarAPIDirections(origen, destino);
    }

    /**
     * Gestiona la llamada a la API de Directions con colores diferenciados para bici.
     */
    private void llamarAPIDirections(LatLng origen, LatLng destino) {
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
            DirectionsApiClient apiClient = new DirectionsApiClient();

            if ("bicycling".equals(modoTransporte)) {
                new Thread(() -> {
                    com.das.unigo.data.TransitDatabase db = com.das.unigo.data.TransitDatabase.getInstance(this);
                    List<com.das.unigo.data.entity.StopEntity> estacionesBici = db.transitDao().getStopsByNodeType("BIKE");

                    if (estacionesBici != null && estacionesBici.size() >= 2) {
                        // Cálculo de estaciones óptimas
                        com.das.unigo.data.entity.StopEntity bO = getClosestStop(origen.latitude, origen.longitude, estacionesBici);
                        com.das.unigo.data.entity.StopEntity bD = getClosestStop(destLat, destLng, estacionesBici);

                        apiClient.getComplexBikeRoute(origen.latitude, origen.longitude, bO.stopLat, bO.stopLon,
                                bD.stopLat, bD.stopLon, destLat, destLng, apiKey,
                                new DirectionsApiClient.RouteCallback() {
                                    @Override public void onSuccess(List<LatLng> r, String d) {}

                                    @Override
                                    public void onComplexSuccess(List<LatLng> walk1, String d1, List<LatLng> bike, String d2, List<LatLng> walk2, String d3, String totalDuration) {
                                        runOnUiThread(() -> {
                                            // Dibujado de los tres tramos con sus respectivos colores
                                            pintarTramo(walk1, Color.BLUE);
                                            pintarTramo(bike, Color.GREEN);
                                            pintarTramo(walk2, Color.BLUE);

                                            // Marcadores de las estaciones de bici
                                            agregarMarcadorBici(new LatLng(bO.stopLat, bO.stopLon), bO.stopName);
                                            agregarMarcadorBici(new LatLng(bD.stopLat, bD.stopLon), bD.stopName);

                                            // Indicador de tiempos desglosados en el centro del tramo de bicicleta
                                            if (bike != null && !bike.isEmpty()) {
                                                mostrarInfoTiempo(bike.get(bike.size() / 2), "🚶 " + d1 + "  |  🚲 " + d2 + "  |  🚶 " + d3);
                                            }

                                            // Ajuste de cámara para que se vea toda la ruta multimodal
                                            enfocarRuta(origen, destino, java.util.Arrays.asList(walk1, bike, walk2));
                                        });
                                    }
                                    @Override public void onError(String msg) {
                                        runOnUiThread(() -> Toast.makeText(MapActivity.this, msg, Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }
                }).start();
            } else {
                // Ruta normal azul/roja para andar o bus
                apiClient.getRoute(origen.latitude, origen.longitude, destino.latitude, destino.longitude, modoTransporte, apiKey, new DirectionsApiClient.RouteCallback() {
                    @Override public void onSuccess(List<LatLng> p, String d) {
                        runOnUiThread(() -> {
                            pintarTramo(p, "transit".equals(modoTransporte) ? Color.RED : Color.BLUE);

                            // En ruta normal también mostramos el tiempo en el medio
                            if (p != null && !p.isEmpty()) {
                                mostrarInfoTiempo(p.get(p.size() / 2), "Tiempo estimado: " + d);
                            }
                            
                            enfocarRuta(origen, destino, java.util.Arrays.asList(p));
                        });
                    }

                    @Override
                    public void onComplexSuccess(List<LatLng> walk1, String d1, List<LatLng> bike, String d2, List<LatLng> walk2, String d3, String totalDuration) {}

                    @Override public void onError(String msg) { runOnUiThread(() -> Toast.makeText(MapActivity.this, msg, Toast.LENGTH_SHORT).show()); }
                });
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Pinta una polilínea en el mapa con el color indicado.
     */
    private void pintarTramo(List<LatLng> puntos, int color) {
        if (puntos != null && !puntos.isEmpty()) {
            mMap.addPolyline(new PolylineOptions().addAll(puntos).width(12f).color(color).geodesic(true));
        }
    }

    private void agregarMarcadorDestino(LatLng destino, String nombre) {
        mMap.addMarker(new MarkerOptions()
                .position(destino)
                .title(nombre)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
    }

    private void mostrarInfoTiempo(LatLng posicion, String mensaje) {
        if (posicion != null) {
            mMap.addMarker(new MarkerOptions()
                    .position(posicion)
                    .alpha(0.0f)
                    .infoWindowAnchor(0.5f, 0.5f)
                    .title(mensaje)).showInfoWindow();
        }
    }

    private void enfocarRuta(LatLng origen, LatLng destino, List<List<LatLng>> tramos) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(origen);
        builder.include(destino);
        if (tramos != null) {
            for (List<LatLng> tramo : tramos) {
                if (tramo != null) {
                    for (LatLng p : tramo) {
                        builder.include(p);
                    }
                }
            }
        }
        try {
            // Reducimos el padding a 100 para evitar desbordamientos en pantallas pequeñas
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
        } catch (IllegalStateException e) {
            // Si el mapa aún no tiene tamaño (size 0), evitamos hacer un moveCamera idéntico
            Log.e("MapActivity", "Error al centrar la cámara: el layout del mapa no está listo aún.");
        }
    }

    /**
     * Crea el efecto de círculo gris + chincheta personalizada para estaciones.
     */
    private void agregarMarcadorBici(LatLng pos, String nombre) {
        // El circulito gris en el suelo
        mMap.addCircle(new com.google.android.gms.maps.model.CircleOptions()
                .center(pos).radius(6).fillColor(Color.argb(70, 128, 128, 128))
                .strokeColor(Color.GRAY).strokeWidth(2));


        // 1. Cargamos el bitmap original
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.bici_chincheta);
        // 2. Lo escalamos a 100x100 píxeles
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(b, 100, 100, false);

        mMap.addMarker(new MarkerOptions()
                .position(pos)
                .title(nombre)
                .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap)) // Usamos fromBitmap en vez de fromResource
                .anchor(0.5f, 0.5f)); // Centrado exacto sobre el círculo gris
    }

    // ─── Weather & Air Quality (Open-Meteo, no API key required) ──────────

    /**
     * Llama a Open-Meteo para obtener la temperatura actual y el índice de
     * calidad del aire (European AQI) en Bilbao (43.26, -2.93).
     */
    private void fetchWeatherAndAirQuality() {

        // --- Temperatura actual (Euskalmet) ---
        new Thread(() -> {
            try {
                // Generar token JWT
                String token = JwtUtils.generateEuskalmetToken(BuildConfig.EUSKALMET_PRIVATE_KEY, "gonzaloperezgo@gmail.com");
                if (token == null) {
                    Log.e("Weather", "No se pudo generar el token JWT");
                    return;
                }

                // Generar URL con fechas (at = hoy, for = hoy)
                java.util.Calendar cal = java.util.Calendar.getInstance();
                String atYear = new SimpleDateFormat("yyyy", Locale.US).format(cal.getTime());
                String atMonth = new SimpleDateFormat("MM", Locale.US).format(cal.getTime());
                String atDay = new SimpleDateFormat("dd", Locale.US).format(cal.getTime());
                String forDate = new SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.getTime());

                String weatherUrl = String.format(Locale.US,
                        "https://api.euskadi.eus/euskalmet/weather/regions/basque_country/zones/great_bilbao/locations/bilbao/forecast/at/%s/%s/%s/for/%s",
                        atYear, atMonth, atDay, forDate);
                Log.d("Weather", "URL: " + weatherUrl);

                String json = httpGetWithAuth(weatherUrl, token);
                Log.d("Weather", "JSON: " + json);

                JSONObject root = new JSONObject(json);
                JSONObject temperatureObj = root.getJSONObject("temperature");
                double temp = temperatureObj.getDouble("value");
                Log.d("Weather", "Temperatura obtenida: " + temp);
                
                JSONObject forecastTextObj = root.optJSONObject("forecastText");
                String forecastText = "";
                if (forecastTextObj != null) {
                    forecastText = forecastTextObj.optString("SPANISH", "").toLowerCase(Locale.ROOT);
                }
                Log.d("Weather", "Texto de previsión: " + forecastText);

                String emoji = forecastTextToEmoji(forecastText);
                Log.d("Weather", "Emoji asignado: " + emoji);
                
                String tempStr = String.format(Locale.getDefault(), "%.0f", temp);
                String label = getString(R.string.weather_label, tempStr);

                runOnUiThread(() -> {
                    tvWeather.setText(label);
                    tvWeatherIcon.setText(emoji);
                });
            } catch (Exception e) {
                Log.e("Weather", "Error al obtener el tiempo de Euskalmet", e);
            }
        }).start();

        // --- Calidad del aire (Open Data Euskadi – municipio Bilbao) ---
        new Thread(() -> {
            try {
                // Rango de fechas: ayer → mañana (API usa gt/lt exclusivo)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
                String yesterday = sdf.format(cal.getTime());
                cal.add(java.util.Calendar.DAY_OF_YEAR, 2);
                String tomorrow = sdf.format(cal.getTime());

                // Todas las estaciones de Bilbao (county 48, municipality 020)
                String aqiUrl = "https://api.euskadi.eus/air-quality/measurements/daily/counties/48/municipalities/020/from/"
                        + yesterday + "/to/" + tomorrow;
                Log.d("AirQuality", "URL: " + aqiUrl);

                String json = httpGet(aqiUrl);
                JSONArray rootArray = new JSONArray(json);

                // Tomamos el último día (el más reciente)
                if (rootArray.length() > 0) {
                    JSONObject dayEntry = rootArray.getJSONObject(rootArray.length() - 1);
                    JSONArray stations = dayEntry.getJSONArray("station");
                    Log.d("AirQuality", "Fecha: " + dayEntry.getString("date")
                            + ", estaciones: " + stations.length());

                    // Buscar PM2,5 y PM10 en cualquier estación
                    double pm25 = -1;
                    double pm10 = -1;

                    for (int i = 0; i < stations.length(); i++) {
                        JSONObject station = stations.getJSONObject(i);
                        JSONArray measurements = station.getJSONArray("measurements");
                        for (int j = 0; j < measurements.length(); j++) {
                            JSONObject m = measurements.getJSONObject(j);
                            String name = m.getString("name");
                            double value = m.getDouble("value");
                            if ("PM2,5".equals(name) && value > 0 && pm25 < 0) pm25 = value;
                            if ("PM10".equals(name) && value > 0 && pm10 < 0)  pm10 = value;
                        }
                        if (pm25 > 0) break;
                    }

                    Log.d("AirQuality", "PM2,5=" + pm25 + "  PM10=" + pm10);

                    // Calcular calidad con umbrales EU AQI
                    String qualityLabel;
                    String emoji;
                    if (pm25 > 0) {
                        qualityLabel = pm25ToQuality(pm25);
                        emoji = qualityToEmoji(qualityLabel);
                    } else if (pm10 > 0) {
                        qualityLabel = pm10ToQuality(pm10);
                        emoji = qualityToEmoji(qualityLabel);
                    } else {
                        qualityLabel = "--";
                        emoji = "❓";
                    }

                    String label = getString(R.string.pollution_label, qualityLabel);
                    Log.d("AirQuality", "Resultado: " + label);

                    runOnUiThread(() -> {
                        tvPollution.setText(label);
                        tvPollutionIcon.setText(emoji);
                    });
                }
            } catch (Exception e) {
                Log.e("AirQuality", "Error al obtener calidad del aire", e);
            }
        }).start();
    }

    /** Simple HTTP GET that returns the response body as a String. */
    private String httpGet(String urlStr) throws Exception {
        return httpGetWithAuth(urlStr, null);
    }

    private String httpGetWithAuth(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "*/*");
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("HTTP GET Failed with Error code : " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    /** Convierte el texto de pronóstico de Euskalmet a un emoji representativo. */
    private String forecastTextToEmoji(String text) {
        if (text == null || text.isEmpty()) return "🌡️";
        if (text.contains("tormenta")) return "⛈️";
        if (text.contains("nieve")) return "🌨️";
        if (text.contains("lluvia") || text.contains("chubasco") || text.contains("precipitaci")) return "🌧️";
        if (text.contains("nuboso") || text.contains("nube")) {
            if (text.contains("claro") || text.contains("poco")) return "⛅";
            return "☁️";
        }
        if (text.contains("despejado") || text.contains("sol")) return "☀️";
        if (text.contains("niebla") || text.contains("bruma")) return "🌫️";
        return "🌡️";
    }

    /**
     * Calcula la calidad del aire a partir de PM2,5 (µg/m³).
     * Umbrales basados en el European AQI (media diaria).
     */
    private String pm25ToQuality(double pm25) {
        if (pm25 <= 10) return getString(R.string.air_quality_good);
        if (pm25 <= 20) return getString(R.string.air_quality_moderate);
        if (pm25 <= 25) return getString(R.string.air_quality_unhealthy_sensitive);
        if (pm25 <= 50) return getString(R.string.air_quality_unhealthy);
        if (pm25 <= 75) return getString(R.string.air_quality_very_unhealthy);
        return getString(R.string.air_quality_hazardous);
    }

    /**
     * Calcula la calidad del aire a partir de PM10 (µg/m³) como fallback.
     * Umbrales basados en el European AQI (media diaria).
     */
    private String pm10ToQuality(double pm10) {
        if (pm10 <= 20) return getString(R.string.air_quality_good);
        if (pm10 <= 40) return getString(R.string.air_quality_moderate);
        if (pm10 <= 50) return getString(R.string.air_quality_unhealthy_sensitive);
        if (pm10 <= 100) return getString(R.string.air_quality_unhealthy);
        if (pm10 <= 150) return getString(R.string.air_quality_very_unhealthy);
        return getString(R.string.air_quality_hazardous);
    }

    /** Convierte la etiqueta de calidad calculada a un emoji. */
    private String qualityToEmoji(String quality) {
        if (quality == null) return "❓";
        if (quality.equals(getString(R.string.air_quality_good)))                  return "🌿";
        if (quality.equals(getString(R.string.air_quality_moderate)))              return "🍃";
        if (quality.equals(getString(R.string.air_quality_unhealthy_sensitive)))   return "😷";
        if (quality.equals(getString(R.string.air_quality_unhealthy)))             return "😷";
        if (quality.equals(getString(R.string.air_quality_very_unhealthy)))        return "⚠️";
        if (quality.equals(getString(R.string.air_quality_hazardous)))             return "☠️";
        return "❓";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacionYTrazarRuta();
        }
    }

    /**
     * Algoritmo auxiliar para encontrar la estación de bicicleta más cercana a un punto geográfico.
     */
    private com.das.unigo.data.entity.StopEntity getClosestStop(double lat, double lon, List<com.das.unigo.data.entity.StopEntity> stops) {
        com.das.unigo.data.entity.StopEntity closest = null;
        float minDistance = Float.MAX_VALUE;
        float[] results = new float[1];

        for (com.das.unigo.data.entity.StopEntity stop : stops) {
            // Cálculo de distancia entre el punto dado y la parada/estación actual
            android.location.Location.distanceBetween(lat, lon, stop.stopLat, stop.stopLon, results);
            if (results[0] < minDistance) {
                minDistance = results[0];
                closest = stop;
            }
        }
        return closest;
    }
}