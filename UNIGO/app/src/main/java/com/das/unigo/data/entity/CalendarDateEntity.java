package com.das.unigo.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Excepciones al calendario base (GTFS calendar_dates).
 * Permite añadir o eliminar servicio en fechas concretas.
 *
 * Bizkaibus usa EXCLUSIVAMENTE esta tabla para definir qué días circula:
 * todos sus service_id tienen los 7 días a 0 en CalendarEntity,
 * y se activan fecha a fecha con exception_type = 1.
 *
 * exception_type:
 *   1 = servicio AÑADIDO en esta fecha
 *   2 = servicio ELIMINADO en esta fecha
 */
@Entity(tableName = "calendar_dates",
        foreignKeys = @ForeignKey(
                entity = CalendarEntity.class,
                parentColumns = "service_id",
                childColumns = "service_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("service_id"),
                @Index("date"),
                @Index(value = {"service_id", "date"}, unique = true)
        }
)
public class CalendarDateEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "service_id")
    public String serviceId;           // FK → calendar

    @ColumnInfo(name = "date")
    public String date;                // "YYYYMMDD"

    @ColumnInfo(name = "exception_type")
    public int exceptionType;          // 1 = añadido, 2 = eliminado
}
