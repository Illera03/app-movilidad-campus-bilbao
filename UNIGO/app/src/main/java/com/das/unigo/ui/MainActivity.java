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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDest, spinnerLang;
    private LinearLayout layoutTransport;
    private RadioGroup rgTransport;
    private RadioButton rbWalk, rbBus, rbBike;
    private Button btnConfirmar;
    private boolean isFirstStart = true;

    // Guardamos la lista de paradas universitarias para obtener sus coordenadas luego
    private List<StopEntity> campusStopsList;
    // Variable para controlar qué botón está pulsado y poder desmarcarlo
    private int radioSeleccionadoId = -1;

    // Cliente de ubicación
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        spinnerDest = findViewById(R.id.spinner_destination);
        spinnerLang = findViewById(R.id.spinner_language);
        layoutTransport = findViewById(R.id.layout_transport_options);
        rgTransport = findViewById(R.id.rg_transport);
        rbWalk = findViewById(R.id.rb_walk);
        rbBus = findViewById(R.id.rb_bus);
        rbBike = findViewById(R.id.rb_bike);
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
                    // Fallback to database value if resource not found
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

        spinnerDest.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    layoutTransport.setAlpha(1.0f);
                    rbWalk.setEnabled(true);
                    rbBus.setEnabled(true);
                    rbBike.setEnabled(true);
                    btnConfirmar.setVisibility(View.VISIBLE);

                    // Ponemos textos de carga solo para andando
                    rbWalk.setText(getString(R.string.transport_walk) + " (" + getString(R.string.calculating) + ")");
                    // Bus y bici se quedan con su texto normal de momento
                    rbBus.setText(getString(R.string.transport_bus));
                    rbBike.setText(getString(R.string.transport_bike));

                    // Lanzar cálculo
                    int selectedPosition = position - 1;
                    StopEntity destinoSeleccionado = campusStopsList.get(selectedPosition);
                    calcularTiempos(destinoSeleccionado);
                } else {
                    layoutTransport.setAlpha(0.5f);
                    rbWalk.setEnabled(false);
                    rbBus.setEnabled(false);
                    rbBike.setEnabled(false);
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
        btnConfirmar.setOnClickListener(v -> {
            if (radioSeleccionadoId == -1) {
                Toast.makeText(this, "Selecciona un medio de transporte", Toast.LENGTH_SHORT).show();
                return;
            }

            // position 0 es el prompt_destino, así que restamos 1 para el array
            int selectedPosition = spinnerDest.getSelectedItemPosition() - 1;
            StopEntity destinoSeleccionado = campusStopsList.get(selectedPosition);

            String modoTransporte = "walking";
            if (radioSeleccionadoId == R.id.rb_bus) modoTransporte = "transit";
            if (radioSeleccionadoId == R.id.rb_bike) modoTransporte = "bicycling";

            // Lanzar la actividad del mapa pasando los datos
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            intent.putExtra("DESTINO_LAT", destinoSeleccionado.stopLat);
            intent.putExtra("DESTINO_LNG", destinoSeleccionado.stopLon);
            intent.putExtra("DESTINO_NOMBRE", destinoSeleccionado.stopName);
            intent.putExtra("MODO_TRANSPORTE", modoTransporte);
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
                double latOrigen = location.getLatitude();
                double lngOrigen = location.getLongitude();

                try {
                    ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
                    String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");
                    DirectionsApiClient apiClient = new DirectionsApiClient();

                    // Andando (Única llamada a la API en esta pantalla)
                    apiClient.getRoute(latOrigen, lngOrigen, destino.stopLat, destino.stopLon, "walking", apiKey, new DirectionsApiClient.RouteCallback() {
                        @Override
                        public void onSuccess(List<LatLng> routeDecoded, String duration) {
                            rbWalk.setText(getString(R.string.transport_walk) + " (" + duration + ")");
                        }
                        @Override
                        public void onError(String errorMessage) {
                            rbWalk.setText(getString(R.string.transport_walk) + " (-)");
                        }
                    });

                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(this, "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show();
            }
        });
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