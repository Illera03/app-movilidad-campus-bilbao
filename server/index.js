const express = require('express');
const cors = require('cors');
const path = require('path');
const { insertLogs, getRecentLogs, getStats } = require('./db');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// ─── POST /api/logs ──────────────────────────────────────────────────────────
// Recibe un array JSON de registros de uso desde la app Android.
app.post('/api/logs', (req, res) => {
    try {
        const logs = req.body;

        if (!Array.isArray(logs) || logs.length === 0) {
            return res.status(400).json({ error: 'Se esperaba un array de logs no vacío.' });
        }

        // Validación básica de campos
        const requiredFields = ['timestamp', 'origin_lat', 'origin_lng',
                                'destination', 'transport_mode', 'estimated_time', 'language'];

        for (const log of logs) {
            for (const field of requiredFields) {
                if (log[field] === undefined || log[field] === null) {
                    return res.status(400).json({ error: `Falta el campo '${field}' en uno de los logs.` });
                }
            }
        }

        const count = insertLogs(logs);
        console.log(`[POST /api/logs] Insertados ${count} registros.`);
        res.json({ ok: true, inserted: count });

    } catch (err) {
        console.error('[POST /api/logs] Error:', err.message);
        res.status(500).json({ error: 'Error interno del servidor.' });
    }
});

// ─── GET /api/logs ───────────────────────────────────────────────────────────
// Devuelve los últimos 100 registros.
app.get('/api/logs', (req, res) => {
    try {
        const limit = parseInt(req.query.limit) || 100;
        const logs = getRecentLogs(limit);
        res.json(logs);
    } catch (err) {
        console.error('[GET /api/logs] Error:', err.message);
        res.status(500).json({ error: 'Error interno del servidor.' });
    }
});

// ─── GET /api/stats ──────────────────────────────────────────────────────────
// Devuelve estadísticas agregadas para el dashboard.
app.get('/api/stats', (req, res) => {
    try {
        const stats = getStats();
        res.json(stats);
    } catch (err) {
        console.error('[GET /api/stats] Error:', err.message);
        res.status(500).json({ error: 'Error interno del servidor.' });
    }
});

// ─── Ruta raíz → Dashboard ──────────────────────────────────────────────────
app.get('/', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// ─── DEMO: Dashboard con datos sintéticos ───────────────────────────────────
app.get('/demo', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'demo.html'));
});

// Generador de datos sintéticos realistas para Bilbao
function generateDemoData() {
    const DESTINATIONS = ['345', '350', '310', '320', '323', '324', '321', '354', '327', '363'];
    const DEST_WEIGHTS = [25, 12, 15, 8, 10, 7, 9, 6, 5, 3]; // Ing. Bilbao es el más popular
    const MODES = ['WALK', 'BUS', 'BIKE', 'TRAM'];
    const MODE_WEIGHTS = [30, 35, 20, 15];
    const LANGS = ['es', 'eu', 'en'];
    const LANG_WEIGHTS = [60, 30, 10];
    const TIMES = ['5 min', '8 min', '10 min', '12 min', '15 min', '18 min', '22 min', '25 min', '30 min'];

    // Zonas de origen realistas alrededor de Bilbao
    const ORIGIN_ZONES = [
        { lat: 43.2630, lng: -2.9350, spread: 0.003 },  // Casco Viejo
        { lat: 43.2710, lng: -2.9390, spread: 0.004 },  // Indautxu
        { lat: 43.2580, lng: -2.9510, spread: 0.003 },  // San Mamés
        { lat: 43.2660, lng: -2.9240, spread: 0.003 },  // Bilbao La Vieja
        { lat: 43.2750, lng: -2.9470, spread: 0.005 },  // Deusto
        { lat: 43.2620, lng: -2.9620, spread: 0.004 },  // Basurto
        { lat: 43.2695, lng: -2.9320, spread: 0.003 },  // Abando
        { lat: 43.2560, lng: -2.9270, spread: 0.003 },  // Atxuri
    ];

    // Distribución horaria realista (picos mañana y tarde)
    const HOUR_WEIGHTS = [
        0,0,0,0,0,1,2,8,25,20,10,5, // 0-11
        8,15,12,10,8,18,22,15,8,3,1,0  // 12-23
    ];

    function weightedRandom(items, weights) {
        const total = weights.reduce((a, b) => a + b, 0);
        let r = Math.random() * total;
        for (let i = 0; i < items.length; i++) {
            r -= weights[i];
            if (r <= 0) return items[i];
        }
        return items[items.length - 1];
    }

    function weightedHour() {
        return weightedRandom(
            Array.from({ length: 24 }, (_, i) => i),
            HOUR_WEIGHTS
        );
    }

    const NUM_LOGS = 500;
    const logs = [];
    const now = new Date();

    for (let i = 0; i < NUM_LOGS; i++) {
        const daysAgo = Math.floor(Math.random() * 30);
        const hour = weightedHour();
        const minute = Math.floor(Math.random() * 60);

        const date = new Date(now);
        date.setDate(date.getDate() - daysAgo);
        date.setHours(hour, minute, 0, 0);
        const timestamp = date.toISOString().substring(0, 19);

        const zone = ORIGIN_ZONES[Math.floor(Math.random() * ORIGIN_ZONES.length)];
        const origin_lat = zone.lat + (Math.random() - 0.5) * zone.spread * 2;
        const origin_lng = zone.lng + (Math.random() - 0.5) * zone.spread * 2;

        logs.push({
            id: i + 1,
            timestamp,
            origin_lat: parseFloat(origin_lat.toFixed(6)),
            origin_lng: parseFloat(origin_lng.toFixed(6)),
            destination: weightedRandom(DESTINATIONS, DEST_WEIGHTS),
            transport_mode: weightedRandom(MODES, MODE_WEIGHTS),
            estimated_time: TIMES[Math.floor(Math.random() * TIMES.length)],
            language: weightedRandom(LANGS, LANG_WEIGHTS),
        });
    }

    // Ordenar por timestamp descendente
    logs.sort((a, b) => b.timestamp.localeCompare(a.timestamp));
    return logs;
}

// Cachear datos demo para que sean consistentes durante la sesión
let demoCache = null;
function getDemoData() {
    if (!demoCache) demoCache = generateDemoData();
    return demoCache;
}

app.get('/api/demo/logs', (req, res) => {
    const limit = parseInt(req.query.limit) || 100;
    const logs = getDemoData().slice(0, limit);
    res.json(logs);
});

app.get('/api/demo/stats', (req, res) => {
    const logs = getDemoData();

    // byTransport
    const transportMap = {};
    logs.forEach(l => { transportMap[l.transport_mode] = (transportMap[l.transport_mode] || 0) + 1; });
    const byTransport = Object.entries(transportMap)
        .map(([transport_mode, count]) => ({ transport_mode, count }))
        .sort((a, b) => b.count - a.count);

    // byDestination
    const destMap = {};
    logs.forEach(l => { destMap[l.destination] = (destMap[l.destination] || 0) + 1; });
    const byDestination = Object.entries(destMap)
        .map(([destination, count]) => ({ destination, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 10);

    // byLanguage
    const langMap = {};
    logs.forEach(l => { langMap[l.language] = (langMap[l.language] || 0) + 1; });
    const byLanguage = Object.entries(langMap)
        .map(([language, count]) => ({ language, count }))
        .sort((a, b) => b.count - a.count);

    // byHour
    const hourMap = {};
    logs.forEach(l => {
        const hour = parseInt(l.timestamp.substring(11, 13));
        hourMap[hour] = (hourMap[hour] || 0) + 1;
    });
    const byHour = Object.entries(hourMap)
        .map(([hour, count]) => ({ hour: parseInt(hour), count }))
        .sort((a, b) => a.hour - b.hour);

    // byDay
    const dayMap = {};
    logs.forEach(l => {
        const day = l.timestamp.substring(0, 10);
        dayMap[day] = (dayMap[day] || 0) + 1;
    });
    const byDay = Object.entries(dayMap)
        .map(([day, count]) => ({ day, count }))
        .sort((a, b) => a.day.localeCompare(b.day));

    res.json({
        total: logs.length,
        byTransport,
        byDestination,
        byLanguage,
        byHour,
        byDay
    });
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`\n  🚀 UNIGO Analytics Server`);
    console.log(`  ─────────────────────────`);
    console.log(`  Dashboard:  http://localhost:${PORT}`);
    console.log(`  Demo:       http://localhost:${PORT}/demo`);
    console.log(`  API Logs:   http://localhost:${PORT}/api/logs`);
    console.log(`  API Stats:  http://localhost:${PORT}/api/stats\n`);
});
