package com.das.unigo.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import com.das.unigo.R;
import com.das.unigo.data.TransitDatabase;
import com.das.unigo.data.api.DirectionsApiClient;
import com.das.unigo.data.entity.StopEntity;
import com.das.unigo.utils.ViajeOptimo;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.maps.android.PolyUtil;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDest, spinnerLang;
    private LinearLayout layoutTransport;
    private RadioGroup rgTransport;
    private RadioButton rbWalk, rbBus, rbBike, rbTram;
    private Button btnConfirmar;
    private boolean isFirstStart = true;

    private List<StopEntity> campusStopsList;
    private int radioSeleccionadoId = -1;

    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private DirectionsApiClient apiClient;

    // Rutas codificadas para Walk
    private String encodedWalkPath;

    // Rutas codificadas para Bike + coordenadas de las estaciones
    private String encodedBikePath, encodedBikeWalk1, encodedBikeWalk2;
    private double bikeOriLat, bikeOriLon; // FIX: guardamos estación origen bici
    private double bikeDestLat, bikeDestLon; // FIX: guardamos estación destino bici
    private String bikeOrigenName, bikeDestinoName; // FIX: nombres de las estaciones

    // Mejor viaje Bus encontrado por GTFS
    private ViajeOptimo mejorViajeBus;
    private ViajeOptimo mejorViajeTram;

    // FIX: guardamos la ubicación del usuario en cuanto se calcula la ruta,
    // para poder enviarla a MapActivity en el Intent.
    private double currentUserLat, currentUserLon;
    private boolean userLocationCached = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        apiClient = new DirectionsApiClient();

        spinnerDest = findViewById(R.id.spinner_destination);
        spinnerLang = findViewById(R.id.spinner_language);
        layoutTransport = findViewById(R.id.layout_transport_options);
        rgTransport = findViewById(R.id.rg_transport);
        rbWalk = findViewById(R.id.rb_walk);
        rbBus = findViewById(R.id.rb_bus);
        rbBike = findViewById(R.id.rb_bike);
        rbTram = findViewById(R.id.rb_tram);
        btnConfirmar = findViewById(R.id.btn_confirmar_ruta);

        Executors.newSingleThreadExecutor().execute(() -> {
            TransitDatabase db = TransitDatabase.getInstance(MainActivity.this);
            campusStopsList = db.transitDao().getCampusStops();

            List<String> destinationNames = new ArrayList<>();
            destinationNames.add(getString(R.string.prompt_destino));

            for (StopEntity stop : campusStopsList) {
                String resourceName = "campus_" + stop.stopCode;
                int resId = getResources().getIdentifier(resourceName, "string", getPackageName());
                destinationNames.add(resId != 0 ? getString(resId) : stop.stopName);
            }

            runOnUiThread(() -> {
                ArrayAdapter<String> adapterDest = new ArrayAdapter<>(MainActivity.this,
                        android.R.layout.simple_spinner_item, destinationNames);
                adapterDest.setDropDownViewResource(R.layout.item_spinner_dropdown);
                spinnerDest.setAdapter(adapterDest);
            });
        });

        ArrayAdapter<CharSequence> adapterLang = ArrayAdapter.createFromResource(this,
                R.array.languages_array, android.R.layout.simple_spinner_item);
        adapterLang.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerLang.setAdapter(adapterLang);

        setLocaleInSpinner();

        View.OnClickListener radioClickListener = v -> {
            if (radioSeleccionadoId == v.getId()) {
                rgTransport.clearCheck();
                radioSeleccionadoId = -1;
            } else {
                radioSeleccionadoId = v.getId();
            }
        };
        rbWalk.setOnClickListener(radioClickListener);
        rbBus.setOnClickListener(radioClickListener);
        rbBike.setOnClickListener(radioClickListener);
        rbTram.setOnClickListener(radioClickListener);

        spinnerDest.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    layoutTransport.setAlpha(1.0f);
                    rbWalk.setEnabled(true);
                    rbBus.setEnabled(true);
                    rbBike.setEnabled(true);
                    rbTram.setEnabled(true);
                    btnConfirmar.setVisibility(View.VISIBLE);

                    rbWalk.setText(getString(R.string.transport_walk) + " (" + getString(R.string.calculating) + ")");
                    rbBus.setText(getString(R.string.transport_bus) + " (" + getString(R.string.calculating) + ")");
                    rbBike.setText(getString(R.string.transport_bike) + " (" + getString(R.string.calculating) + ")");
                    rbTram.setText(getString(R.string.transport_tram) + " (" + getString(R.string.calculating) + ")");

                    // FIX: reseteamos las rutas guardadas al cambiar de destino
                    limpiarRutasGuardadas();

                    StopEntity destinoSeleccionado = campusStopsList.get(position - 1);
                    calcularTiempos(destinoSeleccionado);
                } else {
                    layoutTransport.setAlpha(0.5f);
                    rbWalk.setEnabled(false);
                    rbBus.setEnabled(false);
                    rbBike.setEnabled(false);
                    rbTram.setEnabled(false);
                    btnConfirmar.setVisibility(View.INVISIBLE);
                    rgTransport.clearCheck();
                    radioSeleccionadoId = -1;
                    limpiarRutasGuardadas();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isFirstStart) {
                    isFirstStart = false;
                    return;
                }
                String langCode = "es";
                if (position == 1)
                    langCode = "eu";
                else if (position == 2)
                    langCode = "en";
                cambiarIdioma(langCode);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // ── Confirmar ruta ──────────────────────────────────────────────────
        btnConfirmar.setOnClickListener(v -> {
            if (radioSeleccionadoId == -1) {
                Toast.makeText(this, getString(R.string.error_selecciona_transporte), Toast.LENGTH_SHORT).show();
                return;
            }

            int selectedPosition = spinnerDest.getSelectedItemPosition() - 1;
            StopEntity destinoSeleccionado = campusStopsList.get(selectedPosition);

            Intent intent = new Intent(MainActivity.this, MapActivity.class);

            // ── Datos comunes ──
            intent.putExtra("DESTINO_LAT", destinoSeleccionado.stopLat);
            intent.putExtra("DESTINO_LNG", destinoSeleccionado.stopLon);
            intent.putExtra("DESTINO_NOMBRE", destinoSeleccionado.stopName);

            String modoTransporte = "";
            String tiempoAEnviar = "";

            if (radioSeleccionadoId == R.id.rb_walk) {
                modoTransporte = "WALK";
                tiempoAEnviar = rbWalk.getText().toString();
                intent.putExtra("ENCODED_PATH", encodedWalkPath);

            } else if (radioSeleccionadoId == R.id.rb_bike) {
                modoTransporte = "BIKE";
                tiempoAEnviar = rbBike.getText().toString();
                intent.putExtra("PATH_W1", encodedBikeWalk1);
                intent.putExtra("PATH_B", encodedBikePath);
                intent.putExtra("PATH_W2", encodedBikeWalk2);
                // FIX: enviamos coordenadas y nombres de las estaciones de bici
                intent.putExtra("BIKE_ORI_LAT", bikeOriLat);
                intent.putExtra("BIKE_ORI_LON", bikeOriLon);
                intent.putExtra("BIKE_DEST_LAT", bikeDestLat);
                intent.putExtra("BIKE_DEST_LON", bikeDestLon);
                intent.putExtra("BIKE_ORIGEN_NAME", bikeOrigenName);
                intent.putExtra("BIKE_DESTINO_NAME", bikeDestinoName);

            } else if (radioSeleccionadoId == R.id.rb_bus) {
                modoTransporte = "BUS";
                tiempoAEnviar = rbBus.getText().toString();
                if (mejorViajeBus != null) {
                    intent.putExtra("SHAPE_ID", mejorViajeBus.shapeId);
                    intent.putExtra("ROUTE_ID", mejorViajeBus.routeId);
                    // FIX: enviamos coordenadas de la parada de bus
                    intent.putExtra("BUS_ORI_LAT", mejorViajeBus.origenLat);
                    intent.putExtra("BUS_ORI_LON", mejorViajeBus.origenLon);
                    intent.putExtra("BUS_DEST_LAT", mejorViajeBus.destLat);
                    intent.putExtra("BUS_DEST_LON", mejorViajeBus.destLon);
                    intent.putExtra("STOP_ORIGEN_NAME", mejorViajeBus.nombreParadaOrigen);
                    intent.putExtra("STOP_DESTINO_NAME", mejorViajeBus.nombreParadaDestino);
                }

            } else if (radioSeleccionadoId == R.id.rb_tram) {
                modoTransporte = "TRAM";
                tiempoAEnviar = rbTram.getText().toString();
                if (mejorViajeTram != null) {
                    intent.putExtra("SHAPE_ID", mejorViajeTram.shapeId);
                    intent.putExtra("ROUTE_ID", mejorViajeTram.routeId);
                    // FIX: enviamos coordenadas de la parada de tranvía
                    intent.putExtra("TRAM_ORI_LAT", mejorViajeTram.origenLat);
                    intent.putExtra("TRAM_ORI_LON", mejorViajeTram.origenLon);
                    intent.putExtra("TRAM_DEST_LAT", mejorViajeTram.destLat);
                    intent.putExtra("TRAM_DEST_LON", mejorViajeTram.destLon);
                    intent.putExtra("STOP_ORIGEN_NAME", mejorViajeTram.nombreParadaOrigen);
                    intent.putExtra("STOP_DESTINO_NAME", mejorViajeTram.nombreParadaDestino);
                }
            }

            // ── Extracción del tiempo entre paréntesis ──
            int startIdx = tiempoAEnviar.lastIndexOf("(");
            int endIdx = tiempoAEnviar.lastIndexOf(")");
            if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
                tiempoAEnviar = tiempoAEnviar.substring(startIdx + 1, endIdx);
            } else {
                tiempoAEnviar = "-- mins";
            }

            intent.putExtra("MODO_TRANSPORTE", modoTransporte);
            intent.putExtra("TIEMPO_ESTIMADO", tiempoAEnviar);

            startActivity(intent);
        });
    }

    /** Limpia todas las rutas calculadas al cambiar de destino. */
    private void limpiarRutasGuardadas() {
        encodedWalkPath = null;
        encodedBikePath = null;
        encodedBikeWalk1 = null;
        encodedBikeWalk2 = null;
        bikeOriLat = bikeOriLon = bikeDestLat = bikeDestLon = 0;
        bikeOrigenName = bikeDestinoName = null;
        mejorViajeBus = null;
        mejorViajeTram = null;
    }

    private void calcularTiempos(StopEntity destino) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                // FIX: cacheamos la ubicación del usuario para el Intent
                currentUserLat = location.getLatitude();
                currentUserLon = location.getLongitude();
                userLocationCached = true;
                lanzarCalculoRuta(currentUserLat, currentUserLon, destino);
            } else {
                CancellationTokenSource cts = new CancellationTokenSource();
                fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                        .addOnSuccessListener(loc -> {
                            if (loc != null) {
                                currentUserLat = loc.getLatitude();
                                currentUserLon = loc.getLongitude();
                                userLocationCached = true;
                                lanzarCalculoRuta(currentUserLat, currentUserLon, destino);
                            } else {
                                Toast.makeText(this, getString(R.string.error_ubicacion),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }

    /**
     * Ejecuta el cálculo de tiempos para los diferentes modos de transporte.
     */
    private void lanzarCalculoRuta(double latOrigen, double lngOrigen, StopEntity destino) {
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
                    getPackageName(), PackageManager.GET_META_DATA);
            String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");

            // ── ANDANDO ──────────────────────────────────────────────────────
            apiClient.getRoute(latOrigen, lngOrigen, destino.stopLat, destino.stopLon,
                    "walking", apiKey,
                    new DirectionsApiClient.RouteCallback() {
                        @Override
                        public void onSuccess(List<LatLng> r, String d) {
                            rbWalk.setText(getString(R.string.transport_walk) + " (" + d + ")");
                            encodedWalkPath = PolyUtil.encode(r);
                        }

                        @Override
                        public void onComplexSuccess(List<LatLng> walk1, String d1,
                                List<LatLng> bike, String d2,
                                List<LatLng> walk2, String d3, String totalDuration) {
                            // No aplica para ruta simple
                        }

                        @Override
                        public void onError(String errorMessage) {
                            rbWalk.setText(getString(R.string.transport_walk) + " (-)");
                        }
                    });

            // ── BUS (GTFS) ───────────────────────────────────────────────────
            calcularTiempoBusReal(latOrigen, lngOrigen, destino);

            // ── TRANVÍA (GTFS) ───────────────────────────────────────────────
            calcularTiempoTramReal(latOrigen, lngOrigen, destino);

            // ── BICI (multimodal: Andar → Bici → Andar) ──────────────────────
            Executors.newSingleThreadExecutor().execute(() -> {
                TransitDatabase db = TransitDatabase.getInstance(MainActivity.this);
                List<StopEntity> estacionesBici = db.transitDao().getStopsByNodeType("BIKE");

                if (estacionesBici != null && estacionesBici.size() >= 2) {
                    StopEntity biciOrigen = getClosestStop(latOrigen, lngOrigen, estacionesBici);
                    StopEntity biciDestino = getClosestStop(destino.stopLat, destino.stopLon, estacionesBici);

                    // FIX: guardamos coordenadas y nombres de las estaciones
                    bikeOriLat = biciOrigen.stopLat;
                    bikeOriLon = biciOrigen.stopLon;
                    bikeDestLat = biciDestino.stopLat;
                    bikeDestLon = biciDestino.stopLon;
                    bikeOrigenName = biciOrigen.stopName;
                    bikeDestinoName = biciDestino.stopName;

                    apiClient.getComplexBikeRoute(
                            latOrigen, lngOrigen,
                            biciOrigen.stopLat, biciOrigen.stopLon,
                            biciDestino.stopLat, biciDestino.stopLon,
                            destino.stopLat, destino.stopLon,
                            apiKey,
                            new DirectionsApiClient.RouteCallback() {
                                @Override
                                public void onSuccess(List<LatLng> routeDecoded, String duration) {
                                }

                                @Override
                                public void onComplexSuccess(List<LatLng> walk1, String d1,
                                        List<LatLng> bike, String d2,
                                        List<LatLng> walk2, String d3, String totalDuration) {
                                    rbBike.setText(getString(R.string.transport_bike)
                                            + " (" + totalDuration + ")");
                                    encodedBikeWalk1 = PolyUtil.encode(walk1);
                                    encodedBikePath = PolyUtil.encode(bike);
                                    encodedBikeWalk2 = PolyUtil.encode(walk2);
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    rbBike.setText(getString(R.string.transport_bike) + " (-)");
                                }
                            });
                }
            });

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void calcularTiempoTramReal(double latOrigen, double lngOrigen, StopEntity destino) {
        Executors.newSingleThreadExecutor().execute(() -> {
            TransitDatabase db = TransitDatabase.getInstance(MainActivity.this);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US);
            java.text.SimpleDateFormat sdfTime = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US);

            String hoyDate = sdfDate.format(cal.getTime());
            String ahoraTime = sdfTime.format(cal.getTime());

            int day = cal.get(java.util.Calendar.DAY_OF_WEEK);
            int gtfsDay = (day == java.util.Calendar.SUNDAY) ? 7 : day - 1;

            List<String> activeServices = db.transitDao().getActiveServiceIds(gtfsDay, hoyDate);
            if (activeServices == null || activeServices.isEmpty()) {
                runOnUiThread(() -> rbTram.setText(getString(R.string.transport_tram) + " (- mins)"));
                return;
            }

            List<StopEntity> origenStops = db.transitDao().getNearbyTramStops(latOrigen, lngOrigen, 0.01, 0.01);
            List<StopEntity> destinoStops = db.transitDao().getNearbyTramStops(
                    destino.stopLat, destino.stopLon, 0.01, 0.01);

            List<String> destinoIds = new ArrayList<>();
            for (StopEntity dStop : destinoStops)
                destinoIds.add(dStop.stopId);

            int bestTotalTimeSeconds = Integer.MAX_VALUE;

            for (StopEntity oStop : origenStops) {
                float[] res1 = new float[1];
                android.location.Location.distanceBetween(
                        latOrigen, lngOrigen, oStop.stopLat, oStop.stopLon, res1);
                int walkSecondsToOrigen = (int) ((res1[0] * 1.5f) / 1.4f);

                cal.setTimeInMillis(System.currentTimeMillis() + (walkSecondsToOrigen * 1000L));
                String arrivalAtStopStr = sdfTime.format(cal.getTime());

                ViajeOptimo viaje = db.transitDao().getMejorConexion(
                        oStop.stopId, destinoIds, activeServices, arrivalAtStopStr);

                if (viaje == null)
                    continue;

                float[] res2 = new float[1];
                android.location.Location.distanceBetween(
                        viaje.destLat, viaje.destLon, destino.stopLat, destino.stopLon, res2);
                int walkSecondsToCampus = (int) ((res2[0] * 1.5f) / 1.4f);

                int horaLlegadaDestinoTram = timeStringToSeconds(viaje.horaLlegada);
                int horaActual = timeStringToSeconds(ahoraTime);
                int totalSeconds = (horaLlegadaDestinoTram - horaActual) + walkSecondsToCampus;

                if (totalSeconds > 0 && totalSeconds < bestTotalTimeSeconds) {
                    bestTotalTimeSeconds = totalSeconds;
                    this.mejorViajeTram = viaje;
                    // FIX: guardamos también la parada de origen (subida) para enviarla a
                    // MapActivity
                    this.mejorViajeTram.origenLat = oStop.stopLat;
                    this.mejorViajeTram.origenLon = oStop.stopLon;
                }
            }

            if (this.mejorViajeTram != null) {
                Log.d("TramDebug", "origenLat=" + mejorViajeTram.origenLat + " origenLon=" + mejorViajeTram.origenLon);
                Log.d("TramDebug", "destLat=" + mejorViajeTram.destLat + " destLon=" + mejorViajeTram.destLon);
                Log.d("TramDebug", "shapeId=" + mejorViajeTram.shapeId);
                Log.d("TramDebug", "origenId=" + mejorViajeTram.origenId + " destinoId=" + mejorViajeTram.destinoId);
                int mins = bestTotalTimeSeconds / 60;
                runOnUiThread(() -> rbTram.setText(
                        getString(R.string.transport_tram) + " (" + mins + " mins)"));
                mejorViajeTram.nombreParadaOrigen = db.transitDao().getStopById(mejorViajeTram.origenId).stopName;
                mejorViajeTram.nombreParadaDestino = db.transitDao().getStopById(mejorViajeTram.destinoId).stopName;
                Log.d("MainActivity", "Mejor viaje: " + mejorViajeTram);
                Log.d("MainActivity", "Tiempo: " + mins + " mins");
            } else {
                runOnUiThread(() -> rbTram.setText(getString(R.string.transport_tram) + " (- mins)"));
            }
        });
    }

    /**
     * Calcula el tiempo total real cruzando horarios GTFS:
     * T_Caminar_Parada + T_Espera + T_Bus + T_Caminar_Campus
     */
    private void calcularTiempoBusReal(double latOrigen, double lngOrigen, StopEntity destino) {
        Executors.newSingleThreadExecutor().execute(() -> {
            TransitDatabase db = TransitDatabase.getInstance(MainActivity.this);

            java.util.Calendar cal = java.util.Calendar.getInstance();
            java.text.SimpleDateFormat sdfDate = new java.text.SimpleDateFormat("yyyyMMdd", Locale.US);
            java.text.SimpleDateFormat sdfTime = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US);

            String hoyDate = sdfDate.format(cal.getTime());
            String ahoraTime = sdfTime.format(cal.getTime());

            int day = cal.get(java.util.Calendar.DAY_OF_WEEK);
            int gtfsDay = (day == java.util.Calendar.SUNDAY) ? 7 : day - 1;

            List<String> activeServices = db.transitDao().getActiveServiceIds(gtfsDay, hoyDate);
            if (activeServices == null || activeServices.isEmpty()) {
                runOnUiThread(() -> rbBus.setText(getString(R.string.transport_bus) + " (- mins)"));
                return;
            }

            List<StopEntity> origenStops = db.transitDao().getNearbyBusStops(latOrigen, lngOrigen, 0.01, 0.01);
            List<StopEntity> destinoStops = db.transitDao().getNearbyBusStops(
                    destino.stopLat, destino.stopLon, 0.01, 0.01);

            List<String> destinoIds = new ArrayList<>();
            for (StopEntity dStop : destinoStops)
                destinoIds.add(dStop.stopId);

            int bestTotalTimeSeconds = Integer.MAX_VALUE;

            for (StopEntity oStop : origenStops) {
                float[] res1 = new float[1];
                android.location.Location.distanceBetween(
                        latOrigen, lngOrigen, oStop.stopLat, oStop.stopLon, res1);
                int walkSecondsToOrigen = (int) ((res1[0] * 1.5f) / 1.4f);

                cal.setTimeInMillis(System.currentTimeMillis() + (walkSecondsToOrigen * 1000L));
                String arrivalAtStopStr = sdfTime.format(cal.getTime());

                ViajeOptimo viaje = db.transitDao().getMejorConexion(
                        oStop.stopId, destinoIds, activeServices, arrivalAtStopStr);

                if (viaje == null)
                    continue;

                float[] res2 = new float[1];
                android.location.Location.distanceBetween(
                        viaje.destLat, viaje.destLon, destino.stopLat, destino.stopLon, res2);
                int walkSecondsToCampus = (int) ((res2[0] * 1.5f) / 1.4f);

                int horaLlegadaDestinoBus = timeStringToSeconds(viaje.horaLlegada);
                int horaActual = timeStringToSeconds(ahoraTime);
                int totalSeconds = (horaLlegadaDestinoBus - horaActual) + walkSecondsToCampus;

                if (totalSeconds > 0 && totalSeconds < bestTotalTimeSeconds) {
                    bestTotalTimeSeconds = totalSeconds;
                    this.mejorViajeBus = viaje;
                    // FIX: guardamos también la parada de origen (subida) para enviarla a
                    // MapActivity
                    this.mejorViajeBus.origenLat = oStop.stopLat;
                    this.mejorViajeBus.origenLon = oStop.stopLon;
                }
            }

            if (this.mejorViajeBus != null) {
                Log.d("BusDebug", "origenLat=" + mejorViajeBus.origenLat + " origenLon=" + mejorViajeBus.origenLon);
                Log.d("BusDebug", "destLat=" + mejorViajeBus.destLat + " destLon=" + mejorViajeBus.destLon);
                Log.d("BusDebug", "shapeId=" + mejorViajeBus.shapeId);
                Log.d("BusDebug", "origenId=" + mejorViajeBus.origenId + " destinoId=" + mejorViajeBus.destinoId);
                int mins = bestTotalTimeSeconds / 60;
                runOnUiThread(() -> rbBus.setText(
                        getString(R.string.transport_bus) + " (" + mins + " mins)"));
                mejorViajeBus.nombreParadaOrigen = db.transitDao().getStopById(mejorViajeBus.origenId).stopName;
                mejorViajeBus.nombreParadaDestino = db.transitDao().getStopById(mejorViajeBus.destinoId).stopName;
                Log.d("MainActivity", "Mejor viaje: " + mejorViajeBus);
                Log.d("MainActivity", "Tiempo: " + mins + " mins");
            } else {
                runOnUiThread(() -> rbBus.setText(getString(R.string.transport_bus) + " (- mins)"));
            }
        });
    }

    private int timeStringToSeconds(String time) {
        if (time == null)
            return 0;
        String[] parts = time.split(":");
        if (parts.length == 3) {
            return Integer.parseInt(parts[0]) * 3600
                    + Integer.parseInt(parts[1]) * 60
                    + Integer.parseInt(parts[2]);
        }
        return 0;
    }

    private StopEntity getClosestStop(double lat, double lon, List<StopEntity> stops) {
        StopEntity closest = null;
        float minDistance = Float.MAX_VALUE;
        float[] results = new float[1];
        for (StopEntity stop : stops) {
            android.location.Location.distanceBetween(lat, lon, stop.stopLat, stop.stopLon, results);
            if (results[0] < minDistance) {
                minDistance = results[0];
                closest = stop;
            }
        }
        return closest;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (spinnerDest.getSelectedItemPosition() > 0) {
                StopEntity destinoSeleccionado = campusStopsList.get(spinnerDest.getSelectedItemPosition() - 1);
                calcularTiempos(destinoSeleccionado);
            }
        }
    }

    private void cambiarIdioma(String langCode) {
        Locale currentLocale = getResources().getConfiguration().locale;
        if (currentLocale.getLanguage().equals(langCode))
            return;

        Locale myLocale = new Locale(langCode);
        Resources res = getResources();
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration conf = res.getConfiguration();
        conf.setLocale(myLocale);
        res.updateConfiguration(conf, dm);
        recreate();
    }

    private void setLocaleInSpinner() {
        String lang = getResources().getConfiguration().locale.getLanguage();
        int position = 0;
        if (lang.equals("eu"))
            position = 1;
        else if (lang.equals("en"))
            position = 2;
        spinnerLang.setSelection(position);
    }
}