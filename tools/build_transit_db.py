#!/usr/bin/env python3
"""
build_transit_db.py — Genera la base de datos SQLite pre-poblada para UNIGO.

Lee los archivos GTFS (CSV) y JSON desde la carpeta de datos y genera
un archivo .db con el esquema exacto que Room espera.

NO incluye la tabla room_master_table: Room la generará automáticamente
con el identity hash correcto al primer arranque.

Uso:
    python tools/build_transit_db.py

Estructura esperada:
    data/
        bilbobus_gtfs/   → routes.csv, stops.csv, trips.csv, stop_times.csv,
                           shapes.csv, calendar.csv, calendar_dates.csv
        bizkaibus_gtfs/  → routes.csv, stops.csv, trips.csv, stop_times.csv,
                           shapes.csv, calendar.csv, calendar_dates.csv
        bici/            → EstacionesPrestamo.json
        centros/         → centros.json

Salida:
    UNIGO/app/src/main/assets/databases/unigo_transit.db
"""

import csv
import json
import math
import os
import sqlite3
import sys
import time

# ============================================================
# CONFIGURACIÓN
# ============================================================

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# Carpeta de datos fuente (los CSV/JSON originales)
DATA_DIR = os.path.join(PROJECT_ROOT, "data")

# Ruta de salida del .db
OUTPUT_DB = os.path.join(
    PROJECT_ROOT,
    "UNIGO", "app", "src", "main", "assets", "databases", "unigo_transit.db"
)

# Prefijos para evitar colisiones de IDs entre agencias
PREFIX_BILBOBUS = "BB_"
PREFIX_BIZKAIBUS = "BK_"
PREFIX_BIKE = "BICI_"
PREFIX_CAMPUS = "UNI_"

# Transfers
MAX_WALK_RADIUS_METERS = 500.0
LAT_DELTA = 0.0045
LON_DELTA = 0.0062
EARTH_RADIUS = 6_371_000
WALK_SPEED = 1.4  # m/s (~5 km/h)

BATCH_SIZE = 5000


# ============================================================
# ESQUEMA SQL (idéntico al generado por Room)
# ============================================================

SCHEMA_SQL = """
-- Agencias
CREATE TABLE IF NOT EXISTS `agencies` (
    `agency_id`   TEXT NOT NULL,
    `agency_name` TEXT,
    `agency_type` TEXT,
    PRIMARY KEY(`agency_id`)
);

-- Rutas
CREATE TABLE IF NOT EXISTS `routes` (
    `route_id`         TEXT NOT NULL,
    `agency_id`        TEXT,
    `route_short_name` TEXT,
    `route_long_name`  TEXT,
    `route_type`       INTEGER NOT NULL DEFAULT 0,
    `route_color`      TEXT,
    `route_text_color` TEXT,
    PRIMARY KEY(`route_id`),
    FOREIGN KEY(`agency_id`) REFERENCES `agencies`(`agency_id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_routes_agency_id` ON `routes` (`agency_id`);

-- Paradas (nodos del grafo)
CREATE TABLE IF NOT EXISTS `stops` (
    `stop_id`        TEXT NOT NULL,
    `stop_code`      TEXT,
    `stop_name`      TEXT,
    `stop_lat`       REAL NOT NULL DEFAULT 0,
    `stop_lon`       REAL NOT NULL DEFAULT 0,
    `location_type`  INTEGER NOT NULL DEFAULT 0,
    `parent_station` TEXT,
    `node_type`      TEXT,
    PRIMARY KEY(`stop_id`)
);
CREATE INDEX IF NOT EXISTS `index_stops_stop_lat`  ON `stops` (`stop_lat`);
CREATE INDEX IF NOT EXISTS `index_stops_stop_lon`  ON `stops` (`stop_lon`);
CREATE INDEX IF NOT EXISTS `index_stops_node_type` ON `stops` (`node_type`);

-- Viajes
CREATE TABLE IF NOT EXISTS `trips` (
    `trip_id`                TEXT NOT NULL,
    `route_id`               TEXT,
    `service_id`             TEXT,
    `trip_headsign`          TEXT,
    `direction_id`           INTEGER NOT NULL DEFAULT 0,
    `shape_id`               TEXT,
    `wheelchair_accessible`  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(`trip_id`),
    FOREIGN KEY(`route_id`) REFERENCES `routes`(`route_id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_trips_route_id`   ON `trips` (`route_id`);
CREATE INDEX IF NOT EXISTS `index_trips_service_id` ON `trips` (`service_id`);
CREATE INDEX IF NOT EXISTS `index_trips_shape_id`   ON `trips` (`shape_id`);

-- Horarios de parada
CREATE TABLE IF NOT EXISTS `stop_times` (
    `id`             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `trip_id`        TEXT,
    `arrival_time`   TEXT,
    `departure_time` TEXT,
    `stop_id`        TEXT,
    `stop_sequence`  INTEGER NOT NULL DEFAULT 0,
    `pickup_type`    INTEGER NOT NULL DEFAULT 0,
    `drop_off_type`  INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(`trip_id`) REFERENCES `trips`(`trip_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(`stop_id`) REFERENCES `stops`(`stop_id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_stop_times_trip_id` ON `stop_times` (`trip_id`);
CREATE INDEX IF NOT EXISTS `index_stop_times_stop_id` ON `stop_times` (`stop_id`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_stop_times_trip_id_stop_sequence`
    ON `stop_times` (`trip_id`, `stop_sequence`);

-- Shapes (geometría de ruta para visualización)
CREATE TABLE IF NOT EXISTS `shapes` (
    `id`                  INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `shape_id`            TEXT,
    `shape_pt_lat`        REAL NOT NULL DEFAULT 0,
    `shape_pt_lon`        REAL NOT NULL DEFAULT 0,
    `shape_pt_sequence`   INTEGER NOT NULL DEFAULT 0,
    `shape_dist_traveled` REAL NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS `index_shapes_shape_id` ON `shapes` (`shape_id`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_shapes_shape_id_shape_pt_sequence`
    ON `shapes` (`shape_id`, `shape_pt_sequence`);

-- Calendario base
CREATE TABLE IF NOT EXISTS `calendar` (
    `service_id` TEXT NOT NULL,
    `monday`     INTEGER NOT NULL DEFAULT 0,
    `tuesday`    INTEGER NOT NULL DEFAULT 0,
    `wednesday`  INTEGER NOT NULL DEFAULT 0,
    `thursday`   INTEGER NOT NULL DEFAULT 0,
    `friday`     INTEGER NOT NULL DEFAULT 0,
    `saturday`   INTEGER NOT NULL DEFAULT 0,
    `sunday`     INTEGER NOT NULL DEFAULT 0,
    `start_date` TEXT,
    `end_date`   TEXT,
    PRIMARY KEY(`service_id`)
);

-- Excepciones de calendario
CREATE TABLE IF NOT EXISTS `calendar_dates` (
    `id`             INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `service_id`     TEXT,
    `date`           TEXT,
    `exception_type` INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(`service_id`) REFERENCES `calendar`(`service_id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_calendar_dates_service_id` ON `calendar_dates` (`service_id`);
CREATE INDEX IF NOT EXISTS `index_calendar_dates_date`       ON `calendar_dates` (`date`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_calendar_dates_service_id_date`
    ON `calendar_dates` (`service_id`, `date`);

-- Transfers (transbordos entre nodos)
CREATE TABLE IF NOT EXISTS `transfers` (
    `id`                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `from_stop_id`      TEXT,
    `to_stop_id`        TEXT,
    `transfer_type`     INTEGER NOT NULL DEFAULT 0,
    `min_transfer_time` INTEGER NOT NULL DEFAULT 0,
    `distance_meters`   REAL NOT NULL DEFAULT 0,
    FOREIGN KEY(`from_stop_id`) REFERENCES `stops`(`stop_id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(`to_stop_id`)   REFERENCES `stops`(`stop_id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_transfers_from_stop_id` ON `transfers` (`from_stop_id`);
CREATE INDEX IF NOT EXISTS `index_transfers_to_stop_id`   ON `transfers` (`to_stop_id`);
CREATE UNIQUE INDEX IF NOT EXISTS `index_transfers_from_stop_id_to_stop_id`
    ON `transfers` (`from_stop_id`, `to_stop_id`);
"""


# ============================================================
# UTILIDADES
# ============================================================

def safe_int(value, default=0):
    """Convierte a int, devolviendo default si falla."""
    if not value or not value.strip():
        return default
    try:
        return int(value.strip())
    except ValueError:
        return default


def safe_float(value, default=0.0):
    """Convierte a float, devolviendo default si falla."""
    if not value or not value.strip():
        return default
    try:
        return float(value.strip())
    except ValueError:
        return default


def haversine(lat1, lon1, lat2, lon2):
    """Distancia en metros entre dos puntos (Haversine)."""
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (math.sin(d_lat / 2) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(d_lon / 2) ** 2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return EARTH_RADIUS * c


def read_csv(filepath):
    """
    Lee un archivo CSV con cabeceras, devolviendo cada fila como dict.
    Soporta BOM y retornos de carro extra.
    """
    with open(filepath, "r", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f)
        for row in reader:
            # Limpiar claves y valores
            cleaned = {}
            for k, v in row.items():
                key = k.strip() if k else k
                val = v.strip() if v else None
                if val == "":
                    val = None
                cleaned[key] = val
            yield cleaned


def find_csv_file(folder, base_name):
    """Busca un archivo con extensión .csv o .txt."""
    for ext in [".csv", ".txt"]:
        path = os.path.join(folder, base_name + ext)
        if os.path.exists(path):
            return path
    return None


# ============================================================
# IMPORTACIÓN DE DATOS
# ============================================================

def insert_agencies(cursor):
    """Inserta las 4 agencias de transporte."""
    agencies = [
        ("BILBOBUS", "Bilbobus", "BUS"),
        ("BIZKAIBUS", "Bizkaibus", "BUS"),
        ("BILBONBIZI", "Bilbonbizi", "BIKE"),
        ("UNIVERSIDAD", "Centros Universitarios", "CAMPUS"),
    ]
    cursor.executemany(
        "INSERT OR REPLACE INTO agencies (agency_id, agency_name, agency_type) VALUES (?, ?, ?)",
        agencies
    )
    print(f"  ✓ Agencias insertadas: {len(agencies)}")


def import_routes(cursor, folder, prefix, agency_id):
    """Importa rutas desde routes.csv."""
    filepath = os.path.join(folder, "routes.csv")
    if not os.path.exists(filepath):
        print(f"  ⚠ No se encontró {filepath}")
        return

    batch = []
    for row in read_csv(filepath):
        batch.append((
            prefix + row.get("route_id", ""),
            agency_id,
            row.get("route_short_name"),
            row.get("route_long_name"),
            safe_int(row.get("route_type"), 3),
            row.get("route_color"),
            row.get("route_text_color"),
        ))
        if len(batch) >= BATCH_SIZE:
            cursor.executemany(
                "INSERT OR REPLACE INTO routes "
                "(route_id, agency_id, route_short_name, route_long_name, route_type, route_color, route_text_color) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)", batch
            )
            batch.clear()

    if batch:
        cursor.executemany(
            "INSERT OR REPLACE INTO routes "
            "(route_id, agency_id, route_short_name, route_long_name, route_type, route_color, route_text_color) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ {prefix}routes importadas")


def import_stops(cursor, folder, prefix, node_type):
    """Importa paradas desde stops.csv."""
    filepath = os.path.join(folder, "stops.csv")
    if not os.path.exists(filepath):
        print(f"  ⚠ No se encontró {filepath}")
        return

    batch = []
    for row in read_csv(filepath):
        parent = row.get("parent_station")
        parent_station = (prefix + parent) if parent else None

        batch.append((
            prefix + row.get("stop_id", ""),
            row.get("stop_code"),
            row.get("stop_name"),
            safe_float(row.get("stop_lat")),
            safe_float(row.get("stop_lon")),
            safe_int(row.get("location_type")),
            parent_station,
            node_type,
        ))
        if len(batch) >= BATCH_SIZE:
            cursor.executemany(
                "INSERT OR REPLACE INTO stops "
                "(stop_id, stop_code, stop_name, stop_lat, stop_lon, location_type, parent_station, node_type) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch
            )
            batch.clear()

    if batch:
        cursor.executemany(
            "INSERT OR REPLACE INTO stops "
            "(stop_id, stop_code, stop_name, stop_lat, stop_lon, location_type, parent_station, node_type) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ {prefix}stops importadas")


def import_trips(cursor, folder, prefix):
    """Importa viajes desde trips.csv."""
    filepath = os.path.join(folder, "trips.csv")
    if not os.path.exists(filepath):
        print(f"  ⚠ No se encontró {filepath}")
        return

    batch = []
    count = 0
    for row in read_csv(filepath):
        shape_id = row.get("shape_id")
        batch.append((
            prefix + row.get("trip_id", ""),
            prefix + row.get("route_id", ""),
            prefix + row.get("service_id", ""),
            row.get("trip_headsign"),
            safe_int(row.get("direction_id")),
            (prefix + shape_id) if shape_id else None,
            safe_int(row.get("wheelchair_accessible")),
        ))
        count += 1
        if len(batch) >= BATCH_SIZE:
            cursor.executemany(
                "INSERT OR REPLACE INTO trips "
                "(trip_id, route_id, service_id, trip_headsign, direction_id, shape_id, wheelchair_accessible) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)", batch
            )
            batch.clear()

    if batch:
        cursor.executemany(
            "INSERT OR REPLACE INTO trips "
            "(trip_id, route_id, service_id, trip_headsign, direction_id, shape_id, wheelchair_accessible) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ {prefix}trips importados: {count}")


def import_stop_times(cursor, folder, prefix):
    """Importa horarios de parada desde stop_times.csv."""
    filepath = os.path.join(folder, "stop_times.csv")
    if not os.path.exists(filepath):
        print(f"  ⚠ No se encontró {filepath}")
        return

    batch = []
    count = 0
    for row in read_csv(filepath):
        batch.append((
            prefix + row.get("trip_id", ""),
            row.get("arrival_time"),
            row.get("departure_time"),
            prefix + row.get("stop_id", ""),
            safe_int(row.get("stop_sequence")),
            safe_int(row.get("pickup_type")),
            safe_int(row.get("drop_off_type")),
        ))
        count += 1
        if len(batch) >= BATCH_SIZE:
            cursor.executemany(
                "INSERT INTO stop_times "
                "(trip_id, arrival_time, departure_time, stop_id, stop_sequence, pickup_type, drop_off_type) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)", batch
            )
            batch.clear()
            if count % 100000 == 0:
                print(f"    {prefix}stop_times: {count:,} filas...")

    if batch:
        cursor.executemany(
            "INSERT INTO stop_times "
            "(trip_id, arrival_time, departure_time, stop_id, stop_sequence, pickup_type, drop_off_type) "
            "VALUES (?, ?, ?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ {prefix}stop_times importados: {count:,}")


def import_shapes(cursor, folder, prefix):
    """Importa puntos de geometría de rutas desde shapes.csv."""
    filepath = os.path.join(folder, "shapes.csv")
    if not os.path.exists(filepath):
        print(f"  ⚠ No se encontró {filepath}")
        return

    batch = []
    count = 0
    for row in read_csv(filepath):
        batch.append((
            prefix + row.get("shape_id", ""),
            safe_float(row.get("shape_pt_lat")),
            safe_float(row.get("shape_pt_lon")),
            safe_int(row.get("shape_pt_sequence")),
            safe_float(row.get("shape_dist_traveled")),
        ))
        count += 1
        if len(batch) >= BATCH_SIZE:
            cursor.executemany(
                "INSERT INTO shapes "
                "(shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence, shape_dist_traveled) "
                "VALUES (?, ?, ?, ?, ?)", batch
            )
            batch.clear()
            if count % 200000 == 0:
                print(f"    {prefix}shapes: {count:,} puntos...")

    if batch:
        cursor.executemany(
            "INSERT INTO shapes "
            "(shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence, shape_dist_traveled) "
            "VALUES (?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ {prefix}shapes importados: {count:,}")


def import_calendar(cursor, folder, prefix):
    """Importa calendario base desde calendar.csv o calendar.txt."""
    filepath = find_csv_file(folder, "calendar")
    if not filepath:
        print(f"  ⚠ No se encontró calendar en {folder}")
        return

    batch = []
    for row in read_csv(filepath):
        batch.append((
            prefix + row.get("service_id", ""),
            safe_int(row.get("monday")),
            safe_int(row.get("tuesday")),
            safe_int(row.get("wednesday")),
            safe_int(row.get("thursday")),
            safe_int(row.get("friday")),
            safe_int(row.get("saturday")),
            safe_int(row.get("sunday")),
            row.get("start_date"),
            row.get("end_date"),
        ))

    if batch:
        cursor.executemany(
            "INSERT OR REPLACE INTO calendar "
            "(service_id, monday, tuesday, wednesday, thursday, friday, saturday, sunday, start_date, end_date) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ {prefix}calendar importado: {len(batch)} servicios")


def import_calendar_dates(cursor, folder, prefix):
    """Importa excepciones de calendario desde calendar_dates.csv o calendar_dates.txt."""
    filepath = find_csv_file(folder, "calendar_dates")
    if not filepath:
        print(f"  ⚠ No se encontró calendar_dates en {folder}")
        return

    batch = []
    count = 0
    for row in read_csv(filepath):
        service_id = row.get("service_id")
        date = row.get("date")
        if not service_id or not date:
            continue

        batch.append((
            prefix + service_id,
            date,
            safe_int(row.get("exception_type"), 1),
        ))
        count += 1
        if len(batch) >= BATCH_SIZE:
            cursor.executemany(
                "INSERT INTO calendar_dates (service_id, date, exception_type) "
                "VALUES (?, ?, ?)", batch
            )
            batch.clear()

    if batch:
        cursor.executemany(
            "INSERT INTO calendar_dates (service_id, date, exception_type) "
            "VALUES (?, ?, ?)", batch
        )
    print(f"  ✓ {prefix}calendar_dates importado: {count} excepciones")


def import_gtfs(cursor, folder_name, prefix, agency_id, node_type):
    """Importa un conjunto GTFS completo."""
    folder = os.path.join(DATA_DIR, folder_name)
    if not os.path.isdir(folder):
        print(f"  ⚠ No se encontró la carpeta {folder}")
        return

    import_routes(cursor, folder, prefix, agency_id)
    import_stops(cursor, folder, prefix, node_type)
    import_calendar(cursor, folder, prefix)
    import_calendar_dates(cursor, folder, prefix)
    import_trips(cursor, folder, prefix)
    import_stop_times(cursor, folder, prefix)
    import_shapes(cursor, folder, prefix)


# ============================================================
# ESTACIONES DE BICICLETA
# ============================================================

def calculate_centroid(geometry):
    """Calcula centroide de una geometría GeoJSON (Polygon/MultiPolygon)."""
    geo_type = geometry.get("type", "")
    coordinates = geometry.get("coordinates", [])

    sum_lat = 0.0
    sum_lon = 0.0
    count = 0

    if geo_type == "Polygon":
        ring = coordinates[0] if coordinates else []
        for point in ring:
            sum_lon += point[0]
            sum_lat += point[1]
            count += 1
    elif geo_type == "MultiPolygon":
        for polygon in coordinates:
            ring = polygon[0] if polygon else []
            for point in ring:
                sum_lon += point[0]
                sum_lat += point[1]
                count += 1

    if count == 0:
        return (0.0, 0.0)
    return (sum_lat / count, sum_lon / count)


def import_bike_stations(cursor):
    """Importa estaciones de Bilbonbizi."""
    filepath = os.path.join(DATA_DIR, "bici", "EstacionesPrestamo.json")
    if not os.path.exists(filepath):
        print(f"  ⚠ No se encontró {filepath}")
        return

    with open(filepath, "r", encoding="utf-8") as f:
        data = json.load(f)

    features = data.get("features", [])
    batch = []

    for feature in features:
        props = feature.get("properties", {})
        geometry = feature.get("geometry", {})

        station_id = props.get("Id", 0)
        name = props.get("NombreEstacion", "")
        lat, lon = calculate_centroid(geometry)

        batch.append((
            PREFIX_BIKE + str(station_id),
            str(station_id),
            "Bilbonbizi - " + name,
            lat,
            lon,
            0,
            None,
            "BIKE",
        ))

    if batch:
        cursor.executemany(
            "INSERT OR REPLACE INTO stops "
            "(stop_id, stop_code, stop_name, stop_lat, stop_lon, location_type, parent_station, node_type) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ Estaciones de bici importadas: {len(batch)}")


# ============================================================
# CENTROS UNIVERSITARIOS
# ============================================================

def import_campus_stops(cursor):
    """Importa centros universitarios."""
    filepath = os.path.join(DATA_DIR, "centros", "centros.json")
    if not os.path.exists(filepath):
        print(f"  ⚠ No se encontró {filepath}")
        return

    with open(filepath, "r", encoding="utf-8") as f:
        data = json.load(f)

    destinos = data.get("destinos_universitarios", {})
    batch = []
    auto_id = 0

    for uni_name, centros in destinos.items():
        for centro in centros:
            auto_id += 1

            codigo = centro.get("codigo")
            if not codigo:
                codigo = uni_name.upper() + "_" + str(auto_id)

            batch.append((
                PREFIX_CAMPUS + str(codigo),
                str(codigo),
                uni_name + " - " + centro.get("nombre", ""),
                centro.get("latitud", 0.0),
                centro.get("longitud", 0.0),
                0,
                None,
                "CAMPUS",
            ))

    if batch:
        cursor.executemany(
            "INSERT OR REPLACE INTO stops "
            "(stop_id, stop_code, stop_name, stop_lat, stop_lon, location_type, parent_station, node_type) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", batch
        )
    print(f"  ✓ Centros universitarios importados: {len(batch)}")


# ============================================================
# GENERACIÓN DE TRANSFERS
# ============================================================

def generate_transfers(cursor):
    """
    Genera transfers bidireccionales entre nodos de diferente modo.
    Misma lógica que TransferGenerator.java.
    """
    print("\n── Generando transfers ──")

    # Obtener nodos por tipo
    node_types = {
        "BUS_BILBOBUS": [],
        "BUS_BIZKAIBUS": [],
        "BIKE": [],
        "CAMPUS": [],
    }

    cursor.execute("SELECT stop_id, stop_lat, stop_lon, node_type FROM stops")
    for row in cursor.fetchall():
        stop_id, lat, lon, nt = row
        if nt in node_types:
            node_types[nt].append((stop_id, lat, lon))

    for nt, stops in node_types.items():
        print(f"  Nodos {nt}: {len(stops)}")

    transfers = []

    # Generar entre pares de modos distintos
    pairs = [
        ("BUS_BILBOBUS", "BIKE"),
        ("BUS_BIZKAIBUS", "BIKE"),
        ("BUS_BILBOBUS", "CAMPUS"),
        ("BUS_BIZKAIBUS", "CAMPUS"),
        ("BIKE", "CAMPUS"),
    ]

    for type_a, type_b in pairs:
        list_a = node_types[type_a]
        list_b = node_types[type_b]
        pair_count = 0

        for stop_a, lat_a, lon_a in list_a:
            for stop_b, lat_b, lon_b in list_b:
                # Filtro rápido por bounding box
                if abs(lat_a - lat_b) > LAT_DELTA:
                    continue
                if abs(lon_a - lon_b) > LON_DELTA:
                    continue

                distance = haversine(lat_a, lon_a, lat_b, lon_b)
                if distance <= MAX_WALK_RADIUS_METERS:
                    walk_time = math.ceil(distance / WALK_SPEED)
                    dist_rounded = round(distance * 10) / 10

                    # A → B
                    transfers.append((stop_a, stop_b, 2, walk_time, dist_rounded))
                    # B → A
                    transfers.append((stop_b, stop_a, 2, walk_time, dist_rounded))
                    pair_count += 2

        print(f"  ✓ {type_a} ↔ {type_b}: {pair_count} transfers")

    # Insertar en lotes
    for i in range(0, len(transfers), BATCH_SIZE):
        batch = transfers[i:i + BATCH_SIZE]
        cursor.executemany(
            "INSERT OR REPLACE INTO transfers "
            "(from_stop_id, to_stop_id, transfer_type, min_transfer_time, distance_meters) "
            "VALUES (?, ?, ?, ?, ?)", batch
        )

    print(f"  ✓ Total transfers generados: {len(transfers)}")


# ============================================================
# MAIN
# ============================================================

def main():
    print("=" * 60)
    print("  UNIGO — Generador de base de datos de transporte")
    print("=" * 60)
    print()

    # Verificar que existe la carpeta de datos
    if not os.path.isdir(DATA_DIR):
        print(f"ERROR: No se encontró la carpeta de datos: {DATA_DIR}")
        print("Asegúrate de que los archivos CSV/JSON están en data/")
        sys.exit(1)

    # Crear directorio de salida si no existe
    os.makedirs(os.path.dirname(OUTPUT_DB), exist_ok=True)

    # Eliminar .db anterior si existe
    if os.path.exists(OUTPUT_DB):
        os.remove(OUTPUT_DB)
        print(f"Eliminado .db anterior: {OUTPUT_DB}")

    start_time = time.time()

    # Crear conexión
    conn = sqlite3.connect(OUTPUT_DB)
    cursor = conn.cursor()

    # Optimizaciones para inserción masiva
    cursor.execute("PRAGMA journal_mode = OFF")
    cursor.execute("PRAGMA synchronous = OFF")
    cursor.execute("PRAGMA cache_size = -64000")  # 64MB cache
    cursor.execute("PRAGMA temp_store = MEMORY")

    # Crear esquema
    print("\n── Creando esquema ──")
    cursor.executescript(SCHEMA_SQL)
    print("  ✓ Esquema creado")

    # Insertar agencias
    print("\n── Insertando agencias ──")
    insert_agencies(cursor)
    conn.commit()

    # Importar Bilbobus
    print("\n── Importando Bilbobus GTFS ──")
    import_gtfs(cursor, "bilbobus_gtfs", PREFIX_BILBOBUS, "BILBOBUS", "BUS_BILBOBUS")
    conn.commit()

    # Importar Bizkaibus
    print("\n── Importando Bizkaibus GTFS ──")
    import_gtfs(cursor, "bizkaibus_gtfs", PREFIX_BIZKAIBUS, "BIZKAIBUS", "BUS_BIZKAIBUS")
    conn.commit()

    # Importar estaciones de bici
    print("\n── Importando estaciones de bici ──")
    import_bike_stations(cursor)
    conn.commit()

    # Importar centros universitarios
    print("\n── Importando centros universitarios ──")
    import_campus_stops(cursor)
    conn.commit()

    # Generar transfers
    generate_transfers(cursor)
    conn.commit()

    # Estadísticas finales
    print("\n── Estadísticas ──")
    tables = ["agencies", "routes", "stops", "trips", "stop_times", "shapes",
              "calendar", "calendar_dates", "transfers"]
    for table in tables:
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        print(f"  {table:20s}: {count:>10,}")

    conn.close()

    elapsed = time.time() - start_time
    db_size = os.path.getsize(OUTPUT_DB)
    print(f"\n✅ Base de datos generada en {elapsed:.1f}s")
    print(f"   Archivo: {OUTPUT_DB}")
    print(f"   Tamaño:  {db_size / (1024 * 1024):.1f} MB")


if __name__ == "__main__":
    main()
