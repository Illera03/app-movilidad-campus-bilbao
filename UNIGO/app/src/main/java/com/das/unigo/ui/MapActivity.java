package com.das.unigo.ui;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.das.unigo.R;
import com.das.unigo.data.api.DirectionsApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // Variables que recibimos del MainActivity
    private double destLat;
    private double destLng;
    private String destNombre;
    private String modoTransporte;

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

        findViewById(R.id.fab_back).setOnClickListener(v -> finish());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
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
                LatLng origen = new LatLng(location.getLatitude(), location.getLongitude());
                LatLng destino = new LatLng(destLat, destLng);

                // Añadir marcador en el destino
                mMap.addMarker(new MarkerOptions()
                        .position(destino)
                        .title(destNombre)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                // Llamada a tu API Client
                llamarAPIDirections(origen, destino);

            } else {
                Toast.makeText(this, "Activando GPS, reintentando...", Toast.LENGTH_SHORT).show();
                // Opcional: Podrías usar un LocationRequest si getLastLocation devuelve null
            }
        });
    }

    private void llamarAPIDirections(LatLng origen, LatLng destino) {
        try {
            // Leemos la clave del AndroidManifest (igual que en tu proyecto anterior)
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            String apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY");

            DirectionsApiClient apiClient = new DirectionsApiClient();
            apiClient.getRoute(
                    origen.latitude, origen.longitude,
                    destino.latitude, destino.longitude,
                    modoTransporte,
                    apiKey,
                    new DirectionsApiClient.RouteCallback() {
                        @Override
                        public void onSuccess(List<LatLng> routeDecoded, String duration) {
                            // Definimos el color de la línea según el transporte
                            int colorLinea = Color.BLUE; // default walking
                            if ("transit".equals(modoTransporte)) colorLinea = Color.RED;
                            if ("bicycling".equals(modoTransporte)) colorLinea = Color.GREEN;

                            // Trazamos la línea
                            PolylineOptions options = new PolylineOptions()
                                    .addAll(routeDecoded)
                                    .width(12f)
                                    .color(colorLinea)
                                    .geodesic(true);
                            mMap.addPolyline(options);

                            // Ajustamos la cámara para que se vean origen y destino
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            builder.include(origen);
                            builder.include(destino);
                            // También incluimos todos los puntos de la ruta para curvas extremas
                            for (LatLng point : routeDecoded) {
                                builder.include(point);
                            }

                            LatLngBounds bounds = builder.build();
                            // 100px de padding en los bordes de la pantalla
                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(MapActivity.this, "Error de ruta: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
            );

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            obtenerUbicacionYTrazarRuta();
        }
    }
}