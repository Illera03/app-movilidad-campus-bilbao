package com.das.unigo;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerDest, spinnerLang;
    private LinearLayout layoutTransport;
    private RadioGroup rgTransport;
    private RadioButton rbWalk, rbBus, rbBike;
    private Button btnConfirmar;
    private boolean isFirstStart = true;

    // Variable para controlar qué botón está pulsado y poder desmarcarlo
    private int radioSeleccionadoId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        setContentView(R.layout.activity_main);

        spinnerDest = findViewById(R.id.spinner_destination);
        spinnerLang = findViewById(R.id.spinner_language);
        layoutTransport = findViewById(R.id.layout_transport_options);
        rgTransport = findViewById(R.id.rg_transport);
        rbWalk = findViewById(R.id.rb_walk);
        rbBus = findViewById(R.id.rb_bus);
        rbBike = findViewById(R.id.rb_bike);
        btnConfirmar = findViewById(R.id.btn_confirmar_ruta);

        ArrayAdapter<CharSequence> adapterDest = ArrayAdapter.createFromResource(this,
                R.array.campus_array, android.R.layout.simple_spinner_item);
        adapterDest.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spinnerDest.setAdapter(adapterDest);

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

                    String tiempo = getString(R.string.tiempo_estimado);
                    rbWalk.setText(getString(R.string.transport_walk) + " " + tiempo);
                    rbBus.setText(getString(R.string.transport_bus) + " " + tiempo);
                    rbBike.setText(getString(R.string.transport_bike) + " " + tiempo);
                } else {
                    layoutTransport.setAlpha(0.5f);
                    rbWalk.setEnabled(false);
                    rbBus.setEnabled(false);
                    rbBike.setEnabled(false);
                    btnConfirmar.setVisibility(View.INVISIBLE);

                    // Al bloquear, limpiamos todo y reseteamos nuestra memoria
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