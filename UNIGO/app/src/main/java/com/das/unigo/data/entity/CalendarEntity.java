package com.das.unigo.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Calendario base de servicio (GTFS calendar.txt / calendar.csv).
 * Define el patrón semanal de cada service_id.
 *
 * Bilbobus: usa este patrón directamente (ej: 21-60 = L-J).
 * Bizkaibus: todos los días a 0 — los servicios se activan via CalendarDateEntity.
 */
@Entity(tableName = "calendar")
public class CalendarEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "service_id")
    public String serviceId;           // "21-60", "OP40LJIN", etc.

    @ColumnInfo(name = "monday")
    public int monday;                 // 1 = servicio activo, 0 = inactivo

    @ColumnInfo(name = "tuesday")
    public int tuesday;

    @ColumnInfo(name = "wednesday")
    public int wednesday;

    @ColumnInfo(name = "thursday")
    public int thursday;

    @ColumnInfo(name = "friday")
    public int friday;

    @ColumnInfo(name = "saturday")
    public int saturday;

    @ColumnInfo(name = "sunday")
    public int sunday;

    @ColumnInfo(name = "start_date")
    public String startDate;           // "YYYYMMDD"

    @ColumnInfo(name = "end_date")
    public String endDate;             // "YYYYMMDD"
}
