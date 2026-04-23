package com.das.unigo.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Representa una ruta/línea de transporte (ej: Bilbobus línea 51, Bizkaibus A3514).
 * Prefijada con "BB_" o "BK_" para evitar colisiones entre agencias.
 */
@Entity(tableName = "routes",
        foreignKeys = @ForeignKey(
                entity = AgencyEntity.class,
                parentColumns = "agency_id",
                childColumns = "agency_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("agency_id")}
)
public class RouteEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "route_id")
    public String routeId;             // Prefijado: "BB_51", "BK_A3514"

    @ColumnInfo(name = "agency_id")
    public String agencyId;            // FK → agencies

    @ColumnInfo(name = "route_short_name")
    public String routeShortName;      // "51", "A3514"

    @ColumnInfo(name = "route_long_name")
    public String routeLongName;       // Descripción larga

    @ColumnInfo(name = "route_type")
    public int routeType;              // 3 = bus (GTFS estándar)

    @ColumnInfo(name = "route_color")
    public String routeColor;          // Color hexadecimal sin #

    @ColumnInfo(name = "route_text_color")
    public String routeTextColor;      // Color del texto
}
