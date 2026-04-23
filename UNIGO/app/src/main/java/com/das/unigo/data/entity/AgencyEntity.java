package com.das.unigo.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Representa una agencia o proveedor de transporte.
 * Permite distinguir entre Bilbobus, Bizkaibus, Bilbonbizi y centros universitarios.
 */
@Entity(tableName = "agencies")
public class AgencyEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "agency_id")
    public String agencyId;        // "BILBOBUS", "BIZKAIBUS", "BILBONBIZI", "UNIVERSIDAD"

    @ColumnInfo(name = "agency_name")
    public String agencyName;      // Nombre legible

    @ColumnInfo(name = "agency_type")
    public String agencyType;      // "BUS", "BIKE", "CAMPUS"

    public AgencyEntity(@NonNull String agencyId, String agencyName, String agencyType) {
        this.agencyId = agencyId;
        this.agencyName = agencyName;
        this.agencyType = agencyType;
    }
}
