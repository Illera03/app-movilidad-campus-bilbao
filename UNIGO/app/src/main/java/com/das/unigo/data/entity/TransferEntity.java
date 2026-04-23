package com.das.unigo.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Transbordo o conexión entre dos nodos del grafo.
 * Se pre-calcula al importar datos, buscando nodos cercanos (<500m)
 * de distintos modos/líneas.
 *
 * Tipos de transferencia (basado en GTFS):
 *   0 = Punto de transferencia recomendado entre rutas
 *   1 = Transferencia cronometrada (el bus espera)
 *   2 = Requiere tiempo mínimo de transferencia
 *   3 = Transferencia no posible
 *
 * Para el routing multimodal, los pesos se asignan así:
 *   - A pie entre paradas:  distanceMeters / 1.4 (velocidad peatón ~5 km/h)
 *   - En bici entre estaciones: distanceMeters / 4.2 (velocidad bici ~15 km/h)
 */
@Entity(tableName = "transfers",
        foreignKeys = {
                @ForeignKey(
                        entity = StopEntity.class,
                        parentColumns = "stop_id",
                        childColumns = "from_stop_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = StopEntity.class,
                        parentColumns = "stop_id",
                        childColumns = "to_stop_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("from_stop_id"),
                @Index("to_stop_id"),
                @Index(value = {"from_stop_id", "to_stop_id"}, unique = true)
        }
)
public class TransferEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "from_stop_id")
    public String fromStopId;          // FK → stops

    @ColumnInfo(name = "to_stop_id")
    public String toStopId;            // FK → stops

    @ColumnInfo(name = "transfer_type")
    public int transferType;           // 0, 1, 2, o 3

    @ColumnInfo(name = "min_transfer_time")
    public int minTransferTime;        // Segundos estimados

    @ColumnInfo(name = "distance_meters")
    public double distanceMeters;      // Distancia Haversine entre nodos
}
