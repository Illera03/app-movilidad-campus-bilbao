package com.das.unigo.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Nodo unificado del grafo de transporte.
 * Almacena paradas de bus (Bilbobus/Bizkaibus), estaciones de bici (Bilbonbizi)
 * y centros universitarios.
 *
 * El campo nodeType permite al algoritmo de routing distinguir el tipo de nodo:
 *   - "BUS_BILBOBUS"  → parada de Bilbobus
 *   - "BUS_BIZKAIBUS" → parada de Bizkaibus
 *   - "BIKE"          → estación de préstamo de bicicletas
 *   - "CAMPUS"        → centro universitario (destino final)
 */
@Entity(tableName = "stops",
        indices = {
                @Index("stop_lat"),
                @Index("stop_lon"),
                @Index("node_type")
        }
)
public class StopEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "stop_id")
    public String stopId;              // "BB_775", "BK_3001", "BICI_284", "UNI_345"

    @ColumnInfo(name = "stop_code")
    public String stopCode;

    @ColumnInfo(name = "stop_name")
    public String stopName;

    @ColumnInfo(name = "stop_lat")
    public double stopLat;

    @ColumnInfo(name = "stop_lon")
    public double stopLon;

    @ColumnInfo(name = "location_type")
    public int locationType;           // 0=parada, 1=estación padre, 2=entrada/salida

    @ColumnInfo(name = "parent_station")
    public String parentStation;

    @ColumnInfo(name = "node_type")
    public String nodeType;            // "BUS_BILBOBUS", "BUS_BIZKAIBUS", "BIKE", "CAMPUS"
}
