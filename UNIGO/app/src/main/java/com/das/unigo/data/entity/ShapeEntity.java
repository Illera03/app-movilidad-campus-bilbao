package com.das.unigo.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Punto geométrico de la forma de una ruta.
 * Se usa para dibujar el recorrido real del bus en el mapa.
 * No participa directamente en el algoritmo de routing.
 */
@Entity(tableName = "shapes",
        indices = {
                @Index("shape_id"),
                @Index(value = {"shape_id", "shape_pt_sequence"}, unique = true)
        }
)
public class ShapeEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "shape_id")
    public String shapeId;             // Prefijado: "BB_66", "BK_100"

    @ColumnInfo(name = "shape_pt_lat")
    public double shapePtLat;

    @ColumnInfo(name = "shape_pt_lon")
    public double shapePtLon;

    @ColumnInfo(name = "shape_pt_sequence")
    public int shapePtSequence;

    @ColumnInfo(name = "shape_dist_traveled")
    public double shapeDistTraveled;
}
