package com.das.unigo.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.das.unigo.data.dao.UsageLogDao;
import com.das.unigo.data.entity.UsageLogEntity;

/**
 * Base de datos Room separada para los registros de uso de la aplicación.
 *
 * Se mantiene independiente de TransitDatabase (que es de solo lectura
 * y se carga desde assets) para evitar conflictos de migración y
 * permitir escritura libre de los logs.
 */
@Database(entities = {UsageLogEntity.class}, version = 1, exportSchema = false)
public abstract class UsageDatabase extends RoomDatabase {

    private static volatile UsageDatabase INSTANCE;

    public abstract UsageLogDao usageLogDao();

    /**
     * Singleton thread-safe para obtener la instancia de la base de datos de uso.
     */
    public static UsageDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (UsageDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            UsageDatabase.class,
                            "unigo_usage.db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
