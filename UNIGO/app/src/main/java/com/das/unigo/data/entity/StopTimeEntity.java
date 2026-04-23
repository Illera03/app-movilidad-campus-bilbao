package com.das.unigo.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Horario de parada para un viaje concreto.
 * Esta es la tabla principal del grafo de transporte:
 * dos StopTimeEntity consecutivos del mismo trip forman una arista "transit".
 *
 * NOTA: arrival_time/departure_time se almacenan como String "HH:MM:SS"
 * y pueden exceder 24:00:00 (ej: "25:30:00" = 01:30 del día siguiente).
 */
@Entity(tableName = "stop_times",
        foreignKeys = {
                @ForeignKey(
                        entity = TripEntity.class,
                        parentColumns = "trip_id",
                        childColumns = "trip_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = StopEntity.class,
                        parentColumns = "stop_id",
                        childColumns = "stop_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("trip_id"),
                @Index("stop_id"),
                @Index(value = {"trip_id", "stop_sequence"}, unique = true)
        }
)
public class StopTimeEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "trip_id")
    public String tripId;              // FK → trips

    @ColumnInfo(name = "arrival_time")
    public String arrivalTime;         // "HH:MM:SS"

    @ColumnInfo(name = "departure_time")
    public String departureTime;       // "HH:MM:SS"

    @ColumnInfo(name = "stop_id")
    public String stopId;              // FK → stops

    @ColumnInfo(name = "stop_sequence")
    public int stopSequence;           // Orden de la parada dentro del viaje

    @ColumnInfo(name = "pickup_type")
    public int pickupType;             // 0=regular, 1=no disponible

    @ColumnInfo(name = "drop_off_type")
    public int dropOffType;            // 0=regular, 1=no disponible
}
