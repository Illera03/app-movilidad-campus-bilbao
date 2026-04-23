package com.das.unigo.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Representa un viaje individual dentro de una ruta.
 * Cada trip tiene una secuencia de stop_times (paradas con horario).
 */
@Entity(tableName = "trips",
        foreignKeys = {
                @ForeignKey(
                        entity = RouteEntity.class,
                        parentColumns = "route_id",
                        childColumns = "route_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("route_id"),
                @Index("service_id"),
                @Index("shape_id")
        }
)
public class TripEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "trip_id")
    public String tripId;              // Prefijado: "BB_41413_1", "BK_12345_1"

    @ColumnInfo(name = "route_id")
    public String routeId;             // FK → routes

    @ColumnInfo(name = "service_id")
    public String serviceId;           // "21-60", "OP40LJIN", etc.

    @ColumnInfo(name = "trip_headsign")
    public String tripHeadsign;        // Destino mostrado al pasajero

    @ColumnInfo(name = "direction_id")
    public int directionId;            // 0 = ida, 1 = vuelta

    @ColumnInfo(name = "shape_id")
    public String shapeId;             // FK lógica → shapes (para visualización)

    @ColumnInfo(name = "wheelchair_accessible")
    public int wheelchairAccessible;   // 0=sin info, 1=sí, 2=no
}
