package com.das.unigo.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.das.unigo.data.entity.UsageLogEntity;

import java.util.List;

/**
 * DAO para operaciones sobre la tabla de registros de uso.
 */
@Dao
public interface UsageLogDao {

    /** Inserta un nuevo registro de uso. */
    @Insert
    void insert(UsageLogEntity log);

    /** Devuelve todos los registros que aún no se han sincronizado con el servidor. */
    @Query("SELECT * FROM usage_logs WHERE synced = 0 ORDER BY id ASC")
    List<UsageLogEntity> getUnsyncedLogs();

    /** Marca como sincronizados todos los registros cuyos IDs estén en la lista. */
    @Query("UPDATE usage_logs SET synced = 1 WHERE id IN (:ids)")
    void markAsSynced(List<Integer> ids);
}
