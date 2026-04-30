package com.das.unigo.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;
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
import com.das.unigo.data.entity.StopTimeEntity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.CancellationTokenSource;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDest, spinnerLang;
    private LinearLayout layoutTransport;
    private RadioGroup rgTransport;
    private RadioButton rbWalk, rbBus, rbBike, rbTram;
    private Button btnConfirmar;
    private boolean isFirstStart = true;

    // Guardamos la lista de paradas universitarias para obtener sus coordenadas luego
    private List<StopEntity> campusStopsList;
    // Variable para controlar qué botón está pulsado y poder desmarcarlo
    private int radioSeleccionadoId = -1;

    // Cliente de ubicación
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private DirectionsApiClient apiClient;

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

                if (resId != 0) {
                    destinationNames.add(getString(resId));
                } else {
                    destinationNames.add(stop.stopName);
                }
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

        View.OnClickListener radioClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Si tocamos el que ya estaba seleccionado, lo desmarcamos
                if (radioSeleccionadoId == v.getId()) {
                    rgTransport.clearCheck();
                    radioSeleccionadoId = -1;
                } else {
                    // Si es uno nuevo, nos guardamos su ID
                    radioSeleccionadoId = v.getId();
                }
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

                    // Ponemos textos de carga
                    rbWalk.setText(getString(R.string.transport_walk) + " (" + getString(R.string.calculating) + ")");
                    rbBus.setText(getString(R.string.transport_bus) + " (" + getString(R.string.calculating) + ")");
                    rbBike.setText(getString(R.string.transport_bike)  + " (" + getString(R.string.calculating) + ")");

                    rbTram.setText(getString(R.string.transport_tram));


                    // Lanzar cálculo
                    int selectedPosition = position - 1;
                    StopEntity destinoSeleccionado = campusStopsList.get(selectedPosition);
                    calcularTiempos(destinoSeleccionado);
                } else {
                    layoutTransport.setAlpha(0.5f);
                    rbWalk.setEnabled(false);
                    rbBus.setEnabled(false);
                    rbBike.setEnabled(false);
                    rbTram.setEnabled(false);
                    btnConfirmar.setVisibility(View.INVISIBLE);

                    // Al bloquear, limpiamos todo_ y reseteamos nuestra memoria
                    rgTransport.clearCheck();
                    radioSeleccionadoId = -1;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerLang.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isFirstStart) {
                    isFirstStart = false;
                    return;
                }

                String langCode = "es";
                if (position == 1) langCode = "eu";
                else if (position == 2) langCode = "en";

                cambiarIdioma(langCode);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Acción al confirmar ruta
        // Acción al confirmar ruta
        btnConfirmar.setOnClickListener(v -> {
            if (radioSeleccionadoId == -1) {
                Toast.makeText(this, getString(R.string.error_selecciona_transporte), Toast.LENGTH_SHORT).show();
                return;
            }

            // position 0 es el prompt_destino, así que restamos 1 para el array
            int selectedPosition = spinnerDest.getSelectedItemPosition() - 1;
            StopEntity destinoSeleccionado = campusStopsList.get(selectedPosition);

            String modoTransporte = "walking";
            String tiempoAEnviar = "";

            if (radioSeleccionadoId == R.id.rb_walk) {
                modoTransporte = "walking";
                tiempoAEnviar = rbWalk.getText().toString();
            } else if (radioSeleccionadoId == R.id.rb_bus) {
                modoTransporte = "transit";
                tiempoAEnviar = rbBus.getText().toString();
            } else if (radioSeleccionadoId == R.id.rb_bike) {
                modoTransporte = "bicycling";
                tiempoAEnviar = rbBike.getText().toString();
            } else if (radioSeleccionadoId == R.id.rb_tram) {
                modoTransporte = "tram";
                tiempoAEnviar = rbTram.getText().toString();
            }

            // --- EXTRACCIÓN DEL TIEMPO ---
            // Buscamos el texto entre paréntesis. Si no hay o da error, enviamos un fallback seguro.
            int startIdx = tiempoAEnviar.lastIndexOf("(");
            int endIdx = tiempoAEnviar.lastIndexOf(")");

            if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
                tiempoAEnviar = tiempoAEnviar.substring(startIdx + 1, endIdx);
            } else {
                // Si por algún motivo el botón no tiene paréntesis (ej. el tranvía o no ha terminado de calcular)
                tiempoAEnviar = "-- mins";
            }
            // ------------------------------------

            // Lanzar la actividad del mapa pasando los datos
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            intent.putExtra("DESTINO_LAT", destinoSeleccionado.stopLat);
            intent.putExtra("DESTINO_LNG", destinoSeleccionado.stopLon);
            intent.putExtra("DESTINO_NOMBRE", destinoSeleccionado.stopName);
            intent.putExtra("MODO_TRANSPORTE", modoTransporte);
            intent.putExtra("TIEMPO_ESTIMADO", tiempoAEnviar);
            startActivity(intent);
        });
    }

    private void calcularTiempos(StopEntity destino) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                lanzarCalculoRuta(location.getLatitude(), location.getLongitude(), destino);
            } else {
                // Fallback: solicitar ubicación actual al GPS
                CancellationTokenSource cts = new CancellationTokenSource();
                fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY, cts.getToken()
                ).addOnSuccessListener(loc -> {
                    if (loc != null) {
                        lanzarCalculoRuta(loc.getLatitude(), loc.getLongitude(), destino);
                    } else {
                        Toast.makeText(this, getString(R.string.error_ubicacion), Toast.LENGTH_SHORT).show();
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
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");

            // --- CÁLCULO ANDANDO ---
            // Andando (Única llamada a la API en esta pantalla para este modo)
            apiClient.getRoute(latOrigen, lngOrigen, destino.stopLat, destino.stopLon, "walking", apiKey, new DirectionsApiClient.RouteCallback() {
                @Override
                public void onSuccess(List<LatLng> r, String d) {
                    rbWalk.setText(getString(R.string.transport_walk) + " (" + d + ")");
                }

                @Override
                public void onComplexSuccess(List<LatLng> walk1, String d1, List<LatLng> bike, String d2, List<LatLng> walk2, String d3, String totalDuration) {
                    // Se deja vacío porque aquí solo calculamos ruta simple
                }

                @Override
                public void onError(String errorMessage) {
                    rbWalk.setText(getString(R.string.transport_walk) + " (-)");
                }
            });

            // --- CÁLCULO TRANSPORTE PÚBLICO (Real GTFS) ---
            calcularTiempoBusReal(latOrigen, lngOrigen, destino);

            // --- CÁLCULO EN BICI (Multimodal: Andar -> Bici -> Andar) ---
            // Buscamos las estaciones de Bilbobizi más cercanas en la base de datos
            Executors.newSingleThreadExecutor().execute(() -> {
                TransitDatabase db = TransitDatabase.getInstance(MainActivity.this);
                List<StopEntity> estacionesBici = db.transitDao().getStopsByNodeType("BIKE");

                if (estacionesBici != null && estacionesBici.size() >= 2) {
                    // Encontrar estaciones óptimas de origen y destino
                    StopEntity biciOrigen = getClosestStop(latOrigen, lngOrigen, estacionesBici);
                    StopEntity biciDestino = getClosestStop(destino.stopLat, destino.stopLon, estacionesBici);

                    // Petición combinada al DirectionsApiClient
                    apiClient.getComplexBikeRoute(
                            latOrigen, lngOrigen,
                            biciOrigen.stopLat, biciOrigen.stopLon,
                            biciDestino.stopLat, biciDestino.stopLon,
                            destino.stopLat, destino.stopLon,
                            apiKey,
                            new DirectionsApiClient.RouteCallback() {
                                @Override
                                public void onSuccess(List<LatLng> routeDecoded, String duration) {}

                                @Override
                                public void onComplexSuccess(List<LatLng> walk1, String d1, List<LatLng> bike, String d2, List<LatLng> walk2, String d3, String totalDuration) {
                                    // Se muestra el icono de bicicleta y el tiempo total del trayecto completo
                                    rbBike.setText(getString(R.string.transport_bike) + " (" + totalDuration + ")" );
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    rbBike.setText(getString(R.string.transport_walk) + "(-)");
                                }
                            }
                    );
                }
            });

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
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

            // Radio de búsqueda de ~1km para paradas
            List<StopEntity> origenStops = db.transitDao().getNearbyStops(latOrigen, lngOrigen, 0.01, 0.01);
            List<StopEntity> destinoStops = db.transitDao().getNearbyStops(destino.stopLat, destino.stopLon, 0.01, 0.01);

            int bestTotalTimeSeconds = Integer.MAX_VALUE;

            for (StopEntity oStop : origenStops) {
                // Estimamos tiempo andando a la parada (5km/h = ~1.4 m/s)
                float[] res1 = new float[1];
                android.location.Location.distanceBetween(latOrigen, lngOrigen, oStop.stopLat, oStop.stopLon, res1);
                // Multiplicamos la distancia en línea recta por 1.5 para simular las curvas de las calles
                int walkSecondsToOrigen = (int) ((res1[0] * 1.5f) / 1.4f);

                // Calculamos a qué hora exacta pisamos la parada
                cal.setTimeInMillis(System.currentTimeMillis() + (walkSecondsToOrigen * 1000L));
                String arrivalAtStopStr = sdfTime.format(cal.getTime());

                // Buscamos los próximos buses desde que llegamos, no desde "ahora"
                List<StopTimeEntity> departures = db.transitDao().getNextDepartures(oStop.stopId, arrivalAtStopStr, activeServices, 15);

                for (StopTimeEntity dep : departures) {
                    List<StopTimeEntity> tripStops = db.transitDao().getStopTimesForTrip(dep.tripId);

                    for (StopEntity dStop : destinoStops) {
                        for (StopTimeEntity ts : tripStops) {
                            // Si la ruta pasa por nuestro destino DESPUÉS de recogernos
                            if (ts.stopId.equals(dStop.stopId) && ts.stopSequence > dep.stopSequence) {

                                float[] res2 = new float[1];
                                android.location.Location.distanceBetween(dStop.stopLat, dStop.stopLon, destino.stopLat, destino.stopLon, res2);
                                // Multiplicamos por 1.5 para simular las calles
                                int walkSecondsToCampus = (int) ((res2[0] * 1.5f) / 1.4f);

                                // El tiempo real total es simplemente (HoraLlegadaDestino - HoraActual) + CaminarCampus
                                int horaLlegadaDestinoBus = timeStringToSeconds(ts.arrivalTime);
                                int horaActual = timeStringToSeconds(ahoraTime);

                                int totalSeconds = (horaLlegadaDestinoBus - horaActual) + walkSecondsToCampus;

                                if (totalSeconds > 0 && totalSeconds < bestTotalTimeSeconds) {
                                    bestTotalTimeSeconds = totalSeconds;
                                }
                            }
                        }
                    }
                }
            }

            if (bestTotalTimeSeconds != Integer.MAX_VALUE) {
                int mins = bestTotalTimeSeconds / 60;
                runOnUiThread(() -> rbBus.setText(getString(R.string.transport_bus) + " (" + mins + " mins)"));
            } else {
                runOnUiThread(() -> rbBus.setText(getString(R.string.transport_bus) + " (- mins)"));
            }
        });
    }

    /**
     * Utilidad para convertir los String del GTFS (ej. "14:30:00") a segundos operables.
     */
    private int timeStringToSeconds(String time) {
        if (time == null) return 0;
        String[] parts = time.split(":");
        if (parts.length == 3) {
            return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
        }
        return 0;
    }

    /**
     * Algoritmo auxiliar para encontrar la parada o estación más cercana a una ubicación dada.
     */
    private StopEntity getClosestStop(double lat, double lon, List<StopEntity> stops) {
        StopEntity closest = null;
        float minDistance = Float.MAX_VALUE;
        float[] results = new float[1];

        for (StopEntity stop : stops) {
            // Cálculo de distancia real usando la utilidad de Android
            android.location.Location.distanceBetween(lat, lon, stop.stopLat, stop.stopLon, results);
            if (results[0] < minDistance) {
                minDistance = results[0];
                closest = stop;
            }
        }
        return closest;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Si el usuario acaba de dar permiso, recálculamos si ya hay algo seleccionado
            if (spinnerDest.getSelectedItemPosition() > 0) {
                StopEntity destinoSeleccionado = campusStopsList.get(spinnerDest.getSelectedItemPosition() - 1);
                calcularTiempos(destinoSeleccionado);
            }
        }
    }

    private void cambiarIdioma(String langCode) {
        Locale currentLocale = getResources().getConfiguration().locale;
        if (currentLocale.getLanguage().equals(langCode)) return;

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
        if (lang.equals("eu")) position = 1;
        else if (lang.equals("en")) position = 2;

        spinnerLang.setSelection(position);
    }
}