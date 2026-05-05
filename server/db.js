const Database = require('better-sqlite3');
const path = require('path');

const DB_PATH = path.join(__dirname, 'data', 'usage.db');

// Asegurar que el directorio data/ exista
const fs = require('fs');
const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
    fs.mkdirSync(dataDir, { recursive: true });
}

const db = new Database(DB_PATH);

// Activar WAL para mejor rendimiento en escrituras concurrentes
db.pragma('journal_mode = WAL');

// Crear tabla si no existe
db.exec(`
    CREATE TABLE IF NOT EXISTS usage_logs (
        id          INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp   TEXT NOT NULL,
        origin_lat  REAL NOT NULL,
        origin_lng  REAL NOT NULL,
        destination TEXT NOT NULL,
        transport_mode TEXT NOT NULL,
        estimated_time TEXT NOT NULL,
        language    TEXT NOT NULL,
        received_at TEXT DEFAULT (datetime('now'))
    )
`);

/**
 * Inserta un array de registros de uso.
 * @param {Array} logs - Array de objetos con los campos de la tabla.
 * @returns {number} Número de registros insertados.
 */
function insertLogs(logs) {
    const insert = db.prepare(`
        INSERT INTO usage_logs (timestamp, origin_lat, origin_lng, destination,
                                transport_mode, estimated_time, language)
        VALUES (@timestamp, @origin_lat, @origin_lng, @destination,
                @transport_mode, @estimated_time, @language)
    `);

    const insertMany = db.transaction((items) => {
        for (const item of items) {
            insert.run(item);
        }
        return items.length;
    });

    return insertMany(logs);
}

/**
 * Devuelve los últimos N registros.
 */
function getRecentLogs(limit = 100) {
    return db.prepare('SELECT * FROM usage_logs ORDER BY id DESC LIMIT ?').all(limit);
}

/**
 * Devuelve estadísticas agregadas para el dashboard.
 */
function getStats() {
    const totalLogs = db.prepare('SELECT COUNT(*) as count FROM usage_logs').get();

    const byTransport = db.prepare(`
        SELECT transport_mode, COUNT(*) as count
        FROM usage_logs GROUP BY transport_mode ORDER BY count DESC
    `).all();

    const byDestination = db.prepare(`
        SELECT destination, COUNT(*) as count
        FROM usage_logs GROUP BY destination ORDER BY count DESC LIMIT 10
    `).all();

    const byLanguage = db.prepare(`
        SELECT language, COUNT(*) as count
        FROM usage_logs GROUP BY language ORDER BY count DESC
    `).all();

    const byHour = db.prepare(`
        SELECT CAST(substr(timestamp, 12, 2) AS INTEGER) as hour, COUNT(*) as count
        FROM usage_logs GROUP BY hour ORDER BY hour
    `).all();

    const byDay = db.prepare(`
        SELECT substr(timestamp, 1, 10) as day, COUNT(*) as count
        FROM usage_logs GROUP BY day ORDER BY day DESC LIMIT 30
    `).all();

    return {
        total: totalLogs.count,
        byTransport,
        byDestination,
        byLanguage,
        byHour,
        byDay: byDay.reverse()
    };
}

module.exports = { insertLogs, getRecentLogs, getStats };
