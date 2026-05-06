package com.das.unigo.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Registro de uso: cada fila representa un trayecto confirmado por el usuario.
 * Los registros se almacenan localmente y se sincronizan periódicamente
 * con el servidor remoto mediante WorkManager.
 */
@Entity(tableName = "usage_logs")
public class UsageLogEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Marca temporal ISO 8601 del momento en que se confirmó el trayecto. */
    @ColumnInfo(name = "timestamp")
    public String timestamp;

    /** Latitud del origen del usuario (ubicación actual al confirmar). */
    @ColumnInfo(name = "origin_lat")
    public double originLat;

    /** Longitud del origen del usuario (ubicación actual al confirmar). */
    @ColumnInfo(name = "origin_lng")
    public double originLng;

    /** Código del campus de destino (ej: "345", "310"). */
    @ColumnInfo(name = "destination")
    public String destination;

    /** Modo de transporte elegido: WALK, BUS, BIKE o TRAM. */
    @ColumnInfo(name = "transport_mode")
    public String transportMode;

    /** Tiempo estimado del trayecto (ej: "12 min"). */
    @ColumnInfo(name = "estimated_time")
    public String estimatedTime;

    /** Código de idioma activo en la app (es, eu, en). */
    @ColumnInfo(name = "language")
    public String language;

    /** Indica si el registro ya ha sido enviado al servidor. */
    @ColumnInfo(name = "synced", defaultValue = "0")
    public boolean synced;
}
