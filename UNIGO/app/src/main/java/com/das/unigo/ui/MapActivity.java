package com.das.unigo.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.das.unigo.BuildConfig;
import com.das.unigo.data.api.DirectionsApiClient;
import com.das.unigo.data.entity.RouteEntity;
import com.das.unigo.utils.JwtUtils;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.das.unigo.R;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.maps.android.PolyUtil;
import android.view.View;
import com.google.android.material.card.MaterialCardView;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // Variables de control de ruta
    private double destLat, destLng;
    private String destNombre, modoTransporte, tiempoEstimado;

    // Datos específicos por modo
    private String encodedPath, pathW1, pathB, pathW2;
    private String shapeId, routeId, stopOrigenName, stopDestinoName;
    private String bikeOrigenName, bikeDestinoName;
    private double busDestLat, busDestLon;
    private double busOriLat, busOriLon; // FIX #3: añadidos para recibir la parada de subida
    private double bikeOriLat, bikeOriLon;
    private double bikeDestLat, bikeDestLon; // FIX #2: añadidos para recibir la estación destino de bici

    private double tramOriLat, tramOriLon;
    private double tramDestLat, tramDestLon;

    // Views de la interfaz
    private TextView tvWeather, tvWeatherIcon, tvPollution, tvPollutionIcon;
    private MaterialCardView cardRouteDetails;
    private TextView tvLineLabel, tvTotalTime, tvOriginName, tvDestinationName;

    // FIX #1: la ubicación real del usuario se obtiene en tiempo real, no del
    // Intent
    private double userLat, userLon;
    private boolean userLocationReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        recuperarDatosIntent();
        vincularVistas();

        findViewById(R.id.fab_back).setOnClickListener(v -> finish());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fetchWeatherAndAirQuality();
    }

    private void recuperarDatosIntent() {
        if (getIntent() != null) {
            // FIX #1: ya no leemos ORIGEN_LAT/ORIGEN_LNG porque MainActivity no los envía.
            // La ubicación real se obtiene con FusedLocationProvider en onMapReady().

            destLat = getIntent().getDoubleExtra("DESTINO_LAT", 0);
            destLng = getIntent().getDoubleExtra("DESTINO_LNG", 0);
            destNombre = getIntent().getStringExtra("DESTINO_NOMBRE");
            modoTransporte = getIntent().getStringExtra("MODO_TRANSPORTE");
            tiempoEstimado = getIntent().getStringExtra("TIEMPO_ESTIMADO");

            // Datos de rutas codificadas (Walk/Bike)
            encodedPath = getIntent().getStringExtra("ENCODED_PATH");
            pathW1 = getIntent().getStringExtra("PATH_W1");
            pathB = getIntent().getStringExtra("PATH_B");
            pathW2 = getIntent().getStringExtra("PATH_W2");

            // Datos de Bus
            shapeId = getIntent().getStringExtra("SHAPE_ID");
            routeId = getIntent().getStringExtra("ROUTE_ID");
            busDestLat = getIntent().getDoubleExtra("BUS_DEST_LAT", 0);
            busDestLon = getIntent().getDoubleExtra("BUS_DEST_LON", 0);
            // FIX #3: recibir coordenadas de la parada de subida al bus
            busOriLat = getIntent().getDoubleExtra("BUS_ORI_LAT", 0);
            busOriLon = getIntent().getDoubleExtra("BUS_ORI_LON", 0);

            stopOrigenName = getIntent().getStringExtra("STOP_ORIGEN_NAME");
            stopDestinoName = getIntent().getStringExtra("STOP_DESTINO_NAME");

            // Datos de tranvía
            tramOriLat = getIntent().getDoubleExtra("TRAM_ORI_LAT", 0);
            tramOriLon = getIntent().getDoubleExtra("TRAM_ORI_LON", 0);
            tramDestLat = getIntent().getDoubleExtra("TRAM_DEST_LAT", 0);
            tramDestLon = getIntent().getDoubleExtra("TRAM_DEST_LON", 0);

            // Datos de Bici
            bikeOriLat = getIntent().getDoubleExtra("BIKE_ORI_LAT", 0);
            bikeOriLon = getIntent().getDoubleExtra("BIKE_ORI_LON", 0);
            // FIX #2: recibir coordenadas de la estación destino de bici
            bikeDestLat = getIntent().getDoubleExtra("BIKE_DEST_LAT", 0);
            bikeDestLon = getIntent().getDoubleExtra("BIKE_DEST_LON", 0);
            bikeOrigenName = getIntent().getStringExtra("BIKE_ORIGEN_NAME");
            bikeDestinoName = getIntent().getStringExtra("BIKE_DESTINO_NAME");

            Log.d("BusDebug", "Intent busOriLat=" + busOriLat + " busOriLon=" + busOriLon);
            Log.d("BusDebug", "Intent busDestLat=" + busDestLat + " busDestLon=" + busDestLon);
            Log.d("BusDebug", "Intent shapeId=" + shapeId);
            Log.d("BusDebug", "Intent userLat=" + userLat + " userLon=" + userLon);
        }
    }

    private void vincularVistas() {
        tvWeather = findViewById(R.id.tv_weather);
        tvWeatherIcon = findViewById(R.id.tv_weather_icon);
        tvPollution = findViewById(R.id.tv_pollution);
        tvPollutionIcon = findViewById(R.id.tv_pollution_icon);

        cardRouteDetails = findViewById(R.id.card_route_details);
        tvLineLabel = findViewById(R.id.tv_line_label);
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvOriginName = findViewById(R.id.tv_origin_name);
        tvDestinationName = findViewById(R.id.tv_destination_name);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            // FIX #1: obtenemos la ubicación real antes de dibujar
            obtenerUbicacionYDibujar();
        } else {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * FIX #1: Obtiene la ubicación real del usuario mediante FusedLocationProvider
     * y solo entonces dibuja la ruta, garantizando que userLat/userLon son
     * correctos.
     */
    private void obtenerUbicacionYDibujar() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLat = location.getLatitude();
                userLon = location.getLongitude();
                userLocationReady = true;
                dibujarRutaElegida();
            } else {
                // Fallback al GPS en tiempo real si getLastLocation() devuelve null
                CancellationTokenSource cts = new CancellationTokenSource();
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                        .addOnSuccessListener(loc -> {
                            if (loc != null) {
                                userLat = loc.getLatitude();
                                userLon = loc.getLongitude();
                                userLocationReady = true;
                            }
                            // Dibujamos igualmente; si userLocationReady=false,
                            // los tramos a pie del bus simplemente no se pintarán.
                            dibujarRutaElegida();
                        });
            }
        });
    }

    /**
     * Dibuja la ruta usando los datos pasados sin recalcular nada.
     */
    private void dibujarRutaElegida() {
        mMap.clear();
        LatLng destinoFinal = new LatLng(destLat, destLng);
        agregarMarcadorDestino(destinoFinal, destNombre);

        if (modoTransporte == null)
            return;

        switch (modoTransporte) {
            case "WALK":
                trazarRutaSimple(encodedPath, Color.BLUE, getString(R.string.route_walking));
                break;

            case "BIKE":
                trazarRutaBiciMultimodal();
                break;

            case "BUS":
                trazarRutaBusGTFS(destinoFinal);
                break;

            case "TRAM":
                // Toast.makeText(this, getString(R.string.tram_not_implemented),
                // Toast.LENGTH_SHORT).show();
                trazarRutaTramGTFS(destinoFinal);
                break;

            default:
                Log.w("MapActivity", "Modo de transporte desconocido: " + modoTransporte);
                break;
        }
    }

    private void trazarRutaSimple(String encoded, int color, String label) {
        if (encoded == null)
            return;
        List<LatLng> points = PolyUtil.decode(encoded);
        pintarTramo(points, color);
        actualizarCardDetalles(label, getString(R.string.origin_label), destNombre);
        enfocarListaPuntos(points);
    }

    private void trazarRutaBiciMultimodal() {
        // FIX #5: comprobación de nulos antes de decodificar
        if (pathW1 == null || pathB == null || pathW2 == null) {
            Log.e("MapActivity", "Faltan tramos codificados para la ruta de bici");
            // Toast.makeText(this, getString(R.string.error_ruta_bici),
            // Toast.LENGTH_SHORT).show();
            return;
        }

        List<LatLng> w1 = PolyUtil.decode(pathW1);
        List<LatLng> b = PolyUtil.decode(pathB);
        List<LatLng> w2 = PolyUtil.decode(pathW2);

        pintarTramo(w1, Color.GRAY);
        pintarTramo(b, Color.GREEN);
        pintarTramo(w2, Color.GRAY);

        // Pines en las estaciones de bici
        agregarMarcadorBici(new LatLng(bikeOriLat, bikeOriLon),
                bikeOrigenName != null ? bikeOrigenName : getString(R.string.station_origin_fallback));
        // FIX #2: ahora bikeDestLat/bikeDestLon tienen los valores correctos
        agregarMarcadorBici(new LatLng(bikeDestLat, bikeDestLon),
                bikeDestinoName != null ? bikeDestinoName : getString(R.string.station_destination_fallback));

        // FIX #4: usar los nombres de estación de bici, no los de la parada de bus
        actualizarCardDetalles(getString(R.string.route_bike_label),
                bikeOrigenName != null ? bikeOrigenName : getString(R.string.station_origin_fallback),
                bikeDestinoName != null ? bikeDestinoName : getString(R.string.station_destination_fallback));

        java.util.List<LatLng> total = new java.util.ArrayList<>(w1);
        total.addAll(b);
        total.addAll(w2);
        enfocarListaPuntos(total);
    }

    private void trazarRutaBusGTFS(LatLng destinoFinal) {
        // Obtenemos la API key igual que en MainActivity
        String apiKey;
        try {
            android.content.pm.ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("MapActivity", "No se pudo obtener la API key", e);
            return;
        }

        LatLng paradaOrigen = new LatLng(busOriLat, busOriLon);
        LatLng paradaDestino = new LatLng(busDestLat, busDestLon);
        LatLng usuarioOrigen = new LatLng(userLat, userLon);

        // 1. Cargamos el shape del bus en un hilo de fondo
        new Thread(() -> {
            com.das.unigo.data.TransitDatabase db = com.das.unigo.data.TransitDatabase.getInstance(this);
            java.util.List<com.das.unigo.data.entity.ShapeEntity> rawPoints = db.transitDao().getShapePoints(shapeId);

            java.util.List<LatLng> rutaCompleta = new java.util.ArrayList<>();
            for (com.das.unigo.data.entity.ShapeEntity p : rawPoints)
                rutaCompleta.add(new LatLng(p.shapePtLat, p.shapePtLon));

            List<LatLng> busRoute = recortarRuta(rutaCompleta, paradaOrigen, paradaDestino);

            // 2. Con el shape listo, pedimos los dos tramos a pie a la API (en paralelo)
            DirectionsApiClient apiClient = new DirectionsApiClient();

            // Usamos un contador atómico para saber cuándo han terminado las dos llamadas
            java.util.concurrent.atomic.AtomicInteger pendingCalls = new java.util.concurrent.atomic.AtomicInteger(2);
            List<LatLng>[] walkRoutes = new List[2]; // [0]=usuario→parada, [1]=parada→campus

            DirectionsApiClient.RouteCallback callbackW1 = new DirectionsApiClient.RouteCallback() {
                @Override
                public void onSuccess(List<LatLng> route, String duration) {
                    walkRoutes[0] = route;
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], busRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }

                @Override
                public void onComplexSuccess(List<LatLng> w1, String d1, List<LatLng> b,
                        String d2, List<LatLng> w2, String d3, String total) {
                }

                @Override
                public void onError(String error) {
                    Log.e("MapActivity", "Error tramo a pie 1: " + error);
                    // Fallback: línea recta
                    walkRoutes[0] = java.util.Arrays.asList(usuarioOrigen, paradaOrigen);
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], busRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }
            };

            DirectionsApiClient.RouteCallback callbackW2 = new DirectionsApiClient.RouteCallback() {
                @Override
                public void onSuccess(List<LatLng> route, String duration) {
                    walkRoutes[1] = route;
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], busRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }

                @Override
                public void onComplexSuccess(List<LatLng> w1, String d1, List<LatLng> b,
                        String d2, List<LatLng> w2, String d3, String total) {
                }

                @Override
                public void onError(String error) {
                    Log.e("MapActivity", "Error tramo a pie 2: " + error);
                    // Fallback: línea recta
                    walkRoutes[1] = java.util.Arrays.asList(paradaDestino, destinoFinal);
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], busRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }
            };

            // Tramo a pie 1: ubicación usuario → parada de subida
            if (userLocationReady) {
                apiClient.getRoute(userLat, userLon,
                        busOriLat, busOriLon,
                        "walking", apiKey, callbackW1);
            } else {
                // Si no tenemos ubicación, fallback a línea recta y decrementamos
                walkRoutes[0] = java.util.Arrays.asList(paradaOrigen, paradaOrigen);
                pendingCalls.decrementAndGet();
            }

            // Tramo a pie 2: parada de bajada → destino campus
            apiClient.getRoute(busDestLat, busDestLon,
                    destLat, destLng,
                    "walking", apiKey, callbackW2);

            // Pintamos el shape del bus inmediatamente (no espera a los tramos a pie)

            RouteEntity route = db.transitDao().getRouteById(routeId);

            if (route == null) {
                Log.e("MapActivity", "No se pudo obtener la ruta del bus");
                finish();
            }

            runOnUiThread(() -> {
                pintarTramo(busRoute, Color.RED);
                agregarMarcadorConEstilo(paradaOrigen, stopOrigenName, R.drawable.ic_bus);
                agregarMarcadorConEstilo(paradaDestino, stopDestinoName, R.drawable.ic_bus);
                actualizarCardDetalles(getString(R.string.route_bus_line, route.routeShortName), stopOrigenName, stopDestinoName);
                enfocarListaPuntos(busRoute);
            });

        }).start();
    }

    private void trazarRutaTramGTFS(LatLng destinoFinal) {
        // Obtenemos la API key igual que en MainActivity
        String apiKey;
        try {
            android.content.pm.ApplicationInfo appInfo = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("MapActivity", "No se pudo obtener la API key", e);
            return;
        }

        LatLng paradaOrigen = new LatLng(tramOriLat, tramOriLon);
        LatLng paradaDestino = new LatLng(tramDestLat, tramDestLon);
        LatLng usuarioOrigen = new LatLng(userLat, userLon);

        Log.d("TramDebug", "trazarRutaTramGTFS: shapeId=" + shapeId + ", routeId=" + routeId);
        Log.d("TramDebug", "paradaOrigen=" + tramOriLat + "," + tramOriLon);
        Log.d("TramDebug", "paradaDestino=" + tramDestLat + "," + tramDestLon);

        // 1. Cargamos el shape del tranvía en un hilo de fondo
        new Thread(() -> {
            com.das.unigo.data.TransitDatabase db = com.das.unigo.data.TransitDatabase.getInstance(this);
            java.util.List<com.das.unigo.data.entity.ShapeEntity> rawPoints = db.transitDao().getShapePoints(shapeId);

            java.util.List<LatLng> rutaCompleta = new java.util.ArrayList<>();
            for (com.das.unigo.data.entity.ShapeEntity p : rawPoints)
                rutaCompleta.add(new LatLng(p.shapePtLat, p.shapePtLon));

            Log.d("TramDebug", "rawPoints.size()=" + rawPoints.size());
            List<LatLng> tramRoute = recortarRuta(rutaCompleta, paradaOrigen, paradaDestino);
            Log.d("TramDebug", "tramRoute.size()=" + (tramRoute != null ? tramRoute.size() : "null"));

            // 2. Con el shape listo, pedimos los dos tramos a pie a la API (en paralelo)
            DirectionsApiClient apiClient = new DirectionsApiClient();

            // Usamos un contador atómico para saber cuándo han terminado las dos llamadas
            java.util.concurrent.atomic.AtomicInteger pendingCalls = new java.util.concurrent.atomic.AtomicInteger(2);
            List<LatLng>[] walkRoutes = new List[2]; // [0]=usuario→parada, [1]=parada→campus

            DirectionsApiClient.RouteCallback callbackW1 = new DirectionsApiClient.RouteCallback() {
                @Override
                public void onSuccess(List<LatLng> route, String duration) {
                    walkRoutes[0] = route;
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], tramRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }

                @Override
                public void onComplexSuccess(List<LatLng> w1, String d1, List<LatLng> b,
                        String d2, List<LatLng> w2, String d3, String total) {
                }

                @Override
                public void onError(String error) {
                    Log.e("MapActivity", "Error tramo a pie 1: " + error);
                    // Fallback: línea recta
                    walkRoutes[0] = java.util.Arrays.asList(usuarioOrigen, paradaOrigen);
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], tramRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }
            };

            DirectionsApiClient.RouteCallback callbackW2 = new DirectionsApiClient.RouteCallback() {
                @Override
                public void onSuccess(List<LatLng> route, String duration) {
                    walkRoutes[1] = route;
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], tramRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }

                @Override
                public void onComplexSuccess(List<LatLng> w1, String d1, List<LatLng> b,
                        String d2, List<LatLng> w2, String d3, String total) {
                }

                @Override
                public void onError(String error) {
                    Log.e("MapActivity", "Error tramo a pie 2: " + error);
                    // Fallback: línea recta
                    walkRoutes[1] = java.util.Arrays.asList(paradaDestino, destinoFinal);
                    if (pendingCalls.decrementAndGet() == 0)
                        dibujarTodo(walkRoutes[0], tramRoute, walkRoutes[1],
                                paradaOrigen, paradaDestino, destinoFinal);
                }
            };

            // Tramo a pie 1: ubicación usuario → parada de subida
            if (userLocationReady) {
                apiClient.getRoute(userLat, userLon,
                        tramOriLat, tramOriLon,
                        "walking", apiKey, callbackW1);
            } else {
                // Si no tenemos ubicación, fallback a línea recta y decrementamos
                walkRoutes[0] = java.util.Arrays.asList(paradaOrigen, paradaOrigen);
                pendingCalls.decrementAndGet();
            }

            // Tramo a pie 2: parada de bajada → destino campus
            apiClient.getRoute(tramDestLat, tramDestLon,
                    destLat, destLng,
                    "walking", apiKey, callbackW2);

            // Pintamos el shape del bus inmediatamente (no espera a los tramos a pie)

            RouteEntity route = db.transitDao().getRouteById(routeId);

            if (route == null) {
                Log.e("MapActivity", "No se pudo obtener la ruta del tranvia");
                finish();
            }

            runOnUiThread(() -> {
                Log.d("TramDebug", "Dibujando tranvía: " + (tramRoute != null ? tramRoute.size() : "null") + " puntos");
                pintarTramo(tramRoute, Color.DKGRAY);
                agregarMarcadorConEstilo(paradaOrigen, stopOrigenName, R.drawable.ic_tranvia);
                agregarMarcadorConEstilo(paradaDestino, stopDestinoName, R.drawable.ic_tranvia);
                actualizarCardDetalles(getString(R.string.route_tram_line, route.routeShortName), stopOrigenName, stopDestinoName);
                enfocarListaPuntos(tramRoute);
            });

        }).start();
    }

    /**
     * Se llama cuando los dos tramos a pie han terminado de calcularse.
     * Añade las polilíneas al mapa ya en el hilo principal.
     */
    private void dibujarTodo(List<LatLng> walk1, List<LatLng> route,
            List<LatLng> walk2, LatLng paradaOrigen,
            LatLng paradaDestino, LatLng destinoFinal) {
        runOnUiThread(() -> {
            pintarTramo(walk1, Color.BLUE); // usuario → parada subida
            pintarTramo(walk2, Color.BLUE); // parada bajada → campus

            // Reenfocamos incluyendo todos los tramos
            java.util.List<LatLng> total = new java.util.ArrayList<>();
            if (walk1 != null)
                total.addAll(walk1);
            if (route != null)
                total.addAll(route);
            if (walk2 != null)
                total.addAll(walk2);
            enfocarListaPuntos(total);
        });
    }

    private void actualizarCardDetalles(String linea, String origen, String destino) {
        cardRouteDetails.setVisibility(View.VISIBLE);
        tvLineLabel.setText(linea);
        tvTotalTime.setText(tiempoEstimado);
        tvOriginName.setText(origen);
        tvDestinationName.setText(destino);
    }

    private void enfocarListaPuntos(List<LatLng> puntos) {
        if (puntos == null || puntos.isEmpty())
            return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng p : puntos)
            builder.include(p);
        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
        } catch (IllegalStateException e) {
            Log.e("MapActivity", "Error al centrar la cámara: el layout del mapa no está listo aún.");
        }
    }

    private void pintarTramo(List<LatLng> puntos, int color) {
        if (puntos != null && !puntos.isEmpty()) {
            mMap.addPolyline(new PolylineOptions()
                    .addAll(puntos).width(12f).color(color).geodesic(true));
        }
    }

    private void agregarMarcadorDestino(LatLng destino, String nombre) {
        agregarMarcadorConEstilo(destino, nombre, R.drawable.ic_edificio);
    }

    private void agregarMarcadorBici(LatLng pos, String nombre) {
        mMap.addCircle(new com.google.android.gms.maps.model.CircleOptions()
                .center(pos).radius(6)
                .fillColor(Color.argb(70, 128, 128, 128))
                .strokeColor(Color.GRAY).strokeWidth(2));

        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.bici_chincheta);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(b, 100, 100, false);

        mMap.addMarker(new MarkerOptions()
                .position(pos)
                .title(nombre)
                .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                .anchor(0.5f, 0.5f));
    }

    // ─── Weather & Air Quality ────────────────────────────────────────────────

    private void fetchWeatherAndAirQuality() {

        new Thread(() -> {
            try {
                String token = JwtUtils.generateEuskalmetToken(BuildConfig.EUSKALMET_PRIVATE_KEY,
                        BuildConfig.EUSKALMET_EMAIL);
                if (token == null) {
                    Log.e("Weather", "No se pudo generar el token JWT");
                    return;
                }

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

                JSONObject forecastTextObj = root.optJSONObject("forecastText");
                String forecastText = "";
                if (forecastTextObj != null) {
                    forecastText = forecastTextObj.optString("SPANISH", "").toLowerCase(Locale.ROOT);
                }

                String emoji = forecastTextToEmoji(forecastText);
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

        new Thread(() -> {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1);
                String yesterday = sdf.format(cal.getTime());
                cal.add(java.util.Calendar.DAY_OF_YEAR, 2);
                String tomorrow = sdf.format(cal.getTime());

                String aqiUrl = "https://api.euskadi.eus/air-quality/measurements/daily/counties/48/municipalities/020/from/"
                        + yesterday + "/to/" + tomorrow;
                Log.d("AirQuality", "URL: " + aqiUrl);

                String json = httpGet(aqiUrl);
                JSONArray rootArray = new JSONArray(json);

                if (rootArray.length() > 0) {
                    JSONObject dayEntry = rootArray.getJSONObject(rootArray.length() - 1);
                    JSONArray stations = dayEntry.getJSONArray("station");

                    double pm25 = -1;
                    double pm10 = -1;

                    for (int i = 0; i < stations.length(); i++) {
                        JSONObject station = stations.getJSONObject(i);
                        JSONArray measurements = station.getJSONArray("measurements");
                        for (int j = 0; j < measurements.length(); j++) {
                            JSONObject m = measurements.getJSONObject(j);
                            String name = m.getString("name");
                            double value = m.getDouble("value");
                            if ("PM2,5".equals(name) && value > 0 && pm25 < 0)
                                pm25 = value;
                            if ("PM10".equals(name) && value > 0 && pm10 < 0)
                                pm10 = value;
                        }
                        if (pm25 > 0)
                            break;
                    }

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
        while ((line = reader.readLine()) != null)
            sb.append(line);
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    private String forecastTextToEmoji(String text) {
        if (text == null || text.isEmpty())
            return "🌡️";
        if (text.contains("tormenta"))
            return "⛈️";
        if (text.contains("nieve"))
            return "🌨️";
        if (text.contains("lluvia") || text.contains("chubasco") || text.contains("precipitaci"))
            return "🌧️";
        if (text.contains("nuboso") || text.contains("nube")) {
            if (text.contains("claro") || text.contains("poco"))
                return "⛅";
            return "☁️";
        }
        if (text.contains("despejado") || text.contains("sol"))
            return "☀️";
        if (text.contains("niebla") || text.contains("bruma"))
            return "🌫️";
        return "🌡️";
    }

    private String pm25ToQuality(double pm25) {
        if (pm25 <= 10)
            return getString(R.string.air_quality_good);
        if (pm25 <= 20)
            return getString(R.string.air_quality_moderate);
        if (pm25 <= 25)
            return getString(R.string.air_quality_unhealthy_sensitive);
        if (pm25 <= 50)
            return getString(R.string.air_quality_unhealthy);
        if (pm25 <= 75)
            return getString(R.string.air_quality_very_unhealthy);
        return getString(R.string.air_quality_hazardous);
    }

    private String pm10ToQuality(double pm10) {
        if (pm10 <= 20)
            return getString(R.string.air_quality_good);
        if (pm10 <= 40)
            return getString(R.string.air_quality_moderate);
        if (pm10 <= 50)
            return getString(R.string.air_quality_unhealthy_sensitive);
        if (pm10 <= 100)
            return getString(R.string.air_quality_unhealthy);
        if (pm10 <= 150)
            return getString(R.string.air_quality_very_unhealthy);
        return getString(R.string.air_quality_hazardous);
    }

    private String qualityToEmoji(String quality) {
        if (quality == null)
            return "❓";
        if (quality.equals(getString(R.string.air_quality_good)))
            return "🌿";
        if (quality.equals(getString(R.string.air_quality_moderate)))
            return "🍃";
        if (quality.equals(getString(R.string.air_quality_unhealthy_sensitive)))
            return "😷";
        if (quality.equals(getString(R.string.air_quality_unhealthy)))
            return "😷";
        if (quality.equals(getString(R.string.air_quality_very_unhealthy)))
            return "⚠️";
        if (quality.equals(getString(R.string.air_quality_hazardous)))
            return "☠️";
        return "❓";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                if (ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.setMyLocationEnabled(true);
                }
                // FIX #1: también usamos el flujo correcto al conceder permiso tarde
                obtenerUbicacionYDibujar();
            }
        } else {
            Toast.makeText(this, getString(R.string.permission_denied_location),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private List<LatLng> recortarRuta(List<LatLng> rutaCompleta, LatLng origen, LatLng destino) {
        if (rutaCompleta == null || rutaCompleta.size() < 2)
            return rutaCompleta;

        int startIndex = 0;
        int endIndex = rutaCompleta.size() - 1;
        float minDistOrigen = Float.MAX_VALUE;
        float minDistDestino = Float.MAX_VALUE;
        float[] result = new float[1];

        // 1. Buscar el punto más cercano al origen (en toda la ruta)
        for (int i = 0; i < rutaCompleta.size(); i++) {
            LatLng p = rutaCompleta.get(i);
            Location.distanceBetween(origen.latitude, origen.longitude,
                    p.latitude, p.longitude, result);
            if (result[0] < minDistOrigen) {
                minDistOrigen = result[0];
                startIndex = i;
            }
        }

        // 2. Buscar el punto más cercano al destino (solo desde startIndex en adelante)
        //    Asunción explícita: la ruta es unidireccional (origen → destino)
        for (int i = startIndex; i < rutaCompleta.size(); i++) {
            LatLng p = rutaCompleta.get(i);
            Location.distanceBetween(destino.latitude, destino.longitude,
                    p.latitude, p.longitude, result);
            if (result[0] < minDistDestino) {
                minDistDestino = result[0];
                endIndex = i;
            }
        }

        // 3. Validar que el recorte tiene sentido
        if (startIndex > endIndex) {
            Log.w("recortarRuta", "startIndex (" + startIndex + ") > endIndex ("
                    + endIndex + "), devolviendo ruta completa");
            return new ArrayList<>(rutaCompleta);
        }

        List<LatLng> recortada = new ArrayList<>(
                rutaCompleta.subList(startIndex, endIndex + 1));

        // 4. (Opcional pero recomendado) Insertar origen y destino exactos
        //    para que el trazado arranque y termine justo en los pins
        if (!recortada.isEmpty()) {
            recortada.set(0, origen);
            recortada.set(recortada.size() - 1, destino);
        }

        return recortada;
    }

    /**
     * Crea un marcador con un círculo gris de base y un icono personalizado escalado.
     * Mantiene la coherencia visual con el estilo de las estaciones de bicicleta.
     */
    private void agregarMarcadorConEstilo(LatLng pos, String titulo, int resourceId) {
        // 1. Dibujamos el círculo gris en el suelo (idéntico al de las bicis)
        mMap.addCircle(new com.google.android.gms.maps.model.CircleOptions()
                .center(pos)
                .radius(6) // 6 metros de radio para que se vea bien
                .fillColor(Color.argb(70, 128, 128, 128)) // Gris suave transparente
                .strokeColor(Color.GRAY)
                .strokeWidth(2));

        // 2. Cargamos y escalamos el icono (100x100 píxeles como las bicis)
        Bitmap b = BitmapFactory.decodeResource(getResources(), resourceId);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(b, 100, 100, false);

        // 3. Añadimos el marcador centrado exactamente sobre el círculo
        mMap.addMarker(new MarkerOptions()
                .position(pos)
                .title(titulo)
                .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                .anchor(0.5f, 0.5f)); // Centrado total
    }
}