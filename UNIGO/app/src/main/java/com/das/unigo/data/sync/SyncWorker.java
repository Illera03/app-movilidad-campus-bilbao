package com.das.unigo.data.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.das.unigo.BuildConfig;
import com.das.unigo.data.UsageDatabase;
import com.das.unigo.data.entity.UsageLogEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Worker que sincroniza los registros de uso pendientes con el servidor remoto.
 * Se ejecuta periódicamente mediante WorkManager (cada 24 horas).
 */
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            UsageDatabase db = UsageDatabase.getInstance(getApplicationContext());
            List<UsageLogEntity> unsyncedLogs = db.usageLogDao().getUnsyncedLogs();

            if (unsyncedLogs.isEmpty()) {
                Log.d(TAG, "No hay logs pendientes de sincronizar.");
                return Result.success();
            }

            // Serializar a JSON
            JSONArray jsonArray = new JSONArray();
            List<Integer> ids = new ArrayList<>();

            for (UsageLogEntity log : unsyncedLogs) {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", log.timestamp);
                obj.put("origin_lat", log.originLat);
                obj.put("origin_lng", log.originLng);
                obj.put("destination", log.destination);
                obj.put("transport_mode", log.transportMode);
                obj.put("estimated_time", log.estimatedTime);
                obj.put("language", log.language);
                jsonArray.put(obj);
                ids.add(log.id);
            }

            // Enviar al servidor
            String serverUrl = BuildConfig.SERVER_URL + "/api/logs";
            URL url = new URL(serverUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                db.usageLogDao().markAsSynced(ids);
                Log.d(TAG, "Sincronizados " + ids.size() + " registros.");
                return Result.success();
            } else {
                Log.e(TAG, "Error del servidor: HTTP " + responseCode);
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error al sincronizar logs de uso", e);
            return Result.retry();
        }
    }
}
