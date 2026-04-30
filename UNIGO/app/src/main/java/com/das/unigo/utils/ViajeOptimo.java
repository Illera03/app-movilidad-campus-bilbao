package com.das.unigo.utils;

public class ViajeOptimo {
    public String routeId;
    public String shapeId;
    public String tripId;
    public String origenId;
    public String destinoId;
    public String horaSalida; // st_orig.departure_time
    public String horaLlegada; // st_dest.arrival_time
    public double origenLat;
    public double origenLon;
    public double destLat;
    public double destLon;

    public String nombreParadaOrigen;
    public String nombreParadaDestino;


    @Override
    public String toString() {
        return "ViajeOptimo{" +
                "routeId='" + routeId + '\'' +
                ", shapeId='" + shapeId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", origenId='" + origenId + '\'' +
                ", destinoId='" + destinoId + '\'' +
                ", horaSalida='" + horaSalida + '\'' +
                ", horaLlegada='" + horaLlegada + '\'' +
                '}';
    }
}