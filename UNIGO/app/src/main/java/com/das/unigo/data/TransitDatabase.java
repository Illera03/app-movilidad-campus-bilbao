package com.das.unigo.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.das.unigo.data.dao.TransitDao;
import com.das.unigo.data.entity.*;

/**
 * Base de datos Room para el sistema de transporte multimodal UNIGO.
 *
 * Contiene 9 entidades que modelan un grafo de transporte:
 * - Nodos: StopEntity (paradas bus, estaciones bici, centros universitarios)
 * - Aristas transit: StopTimeEntity (horarios consecutivos de un mismo trip)
 * - Aristas transfer: TransferEntity (transbordos a pie / bici pre-calculados)
 * - Metadatos: AgencyEntity, RouteEntity, TripEntity, ShapeEntity,
 *              CalendarEntity, CalendarDateEntity
 */
@Database(
        entities = {
                AgencyEntity.class,
                RouteEntity.class,
                StopEntity.class,
                TripEntity.class,
                StopTimeEntity.class,
                ShapeEntity.class,
                CalendarEntity.class,
                CalendarDateEntity.class,
                TransferEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class TransitDatabase extends RoomDatabase {

    private static volatile TransitDatabase INSTANCE;

    public abstract TransitDao transitDao();

    /**
     * Singleton thread-safe para obtener la instancia de la base de datos.
     */
    public static TransitDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TransitDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            TransitDatabase.class,
                            "unigo_transit.db"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
