/**
 * MetricsStore - Time-series persistence layer
 * 
 * Primary: InfluxDB (for production, scalable time-series)
 * Fallback: SQLite (for local dev, zero-dependency)
 * 
 * Provides unified API regardless of backend.
 */

const { InfluxDB, Point } = require('@influxdata/influxdb-client');
const path = require('path');

class InfluxStore {
  constructor(config) {
    this.url = config.url || 'http://localhost:8086';
    this.token = config.token || 'kiro-monitor-token';
    this.org = config.org || 'kiro';
    this.bucket = config.bucket || 'metrics';

    this.client = new InfluxDB({ url: this.url, token: this.token });
    this.writeApi = this.client.getWriteApi(this.org, this.bucket, 's');
    this.queryApi = this.client.getQueryApi(this.org);
    this.connected = false;
  }

  async init() {
    try {
      // Test connection with a simple query
      const query = `buckets() |> limit(n: 1)`;
      await this.queryApi.collectRows(query);
      this.connected = true;
      console.log('[Store] InfluxDB connected');
      return true;
    } catch (err) {
      console.warn(`[Store] InfluxDB unavailable: ${err.message}`);
      return false;
    }
  }

  async writeMetrics(hostId, metrics) {
    const point = new Point('system_metrics')
      .tag('host', hostId)
      .floatField('cpu_usage', metrics.cpu?.usage || 0)
      .floatField('cpu_system', metrics.cpu?.system || 0)
      .floatField('cpu_user', metrics.cpu?.user || 0)
      .floatField('mem_percent', metrics.memory?.usedPercent || 0)
      .intField('mem_used', metrics.memory?.used || 0)
      .intField('mem_total', metrics.memory?.total || 0)
      .intField('processes_total', metrics.processes?.all || 0)
      .intField('processes_running', metrics.processes?.running || 0)
      .floatField('net_rx_sec', metrics.network?.[0]?.rxPerSec || 0)
      .floatField('net_tx_sec', metrics.network?.[0]?.txPerSec || 0)
      .floatField('disk_read_sec', metrics.disk?.readPerSec || 0)
      .floatField('disk_write_sec', metrics.disk?.writePerSec || 0)
      .timestamp(new Date(metrics.timestamp || Date.now()));

    this.writeApi.writePoint(point);
  }

  async writeServiceStatus(serviceId, status, responseTime) {
    const point = new Point('service_health')
      .tag('service', serviceId)
      .stringField('status', status)
      .intField('response_time', responseTime || 0)
      .booleanField('is_up', status === 'UP')
      .timestamp(new Date());

    this.writeApi.writePoint(point);
  }

  async writeTrace(trace) {
    const point = new Point('traces')
      .tag('service', trace.service)
      .tag('operation', trace.operation)
      .tag('trace_id', trace.traceId)
      .tag('status_code', String(trace.statusCode || 0))
      .intField('duration_ms', trace.duration)
      .booleanField('is_error', trace.isError || false)
      .timestamp(new Date(trace.timestamp || Date.now()));

    this.writeApi.writePoint(point);
  }

  async writeLog(log) {
    const point = new Point('logs')
      .tag('service', log.service)
      .tag('level', log.level)
      .stringField('message', log.message)
      .stringField('context', JSON.stringify(log.context || {}))
      .timestamp(new Date(log.timestamp || Date.now()));

    this.writeApi.writePoint(point);
  }

  async queryMetrics(hostId, range = '-1h', limit = 100) {
    const query = `
      from(bucket: "${this.bucket}")
        |> range(start: ${range})
        |> filter(fn: (r) => r._measurement == "system_metrics")
        |> filter(fn: (r) => r.host == "${hostId}")
        |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
        |> sort(columns: ["_time"], desc: true)
        |> limit(n: ${limit})
    `;
    return await this.queryApi.collectRows(query);
  }

  async flush() {
    try {
      await this.writeApi.flush();
    } catch (e) {
      // Ignore flush errors
    }
  }

  async close() {
    await this.writeApi.close();
  }
}

class SQLiteStore {
  constructor(config) {
    this.dbPath = config.dbPath || path.join(__dirname, '../../data/metrics.db');
    this.db = null;
    this.connected = false;
  }

  async init() {
    try {
      const Database = require('better-sqlite3');
      const fs = require('fs');
      const dir = path.dirname(this.dbPath);
      if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

      this.db = new Database(this.dbPath);
      this.db.pragma('journal_mode = WAL');
      this.db.pragma('synchronous = NORMAL');

      // Create tables
      this.db.exec(`
        CREATE TABLE IF NOT EXISTS system_metrics (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          host TEXT NOT NULL,
          timestamp INTEGER NOT NULL,
          cpu_usage REAL,
          cpu_system REAL,
          cpu_user REAL,
          mem_percent REAL,
          mem_used INTEGER,
          mem_total INTEGER,
          processes_total INTEGER,
          processes_running INTEGER,
          net_rx_sec REAL,
          net_tx_sec REAL,
          disk_read_sec REAL,
          disk_write_sec REAL,
          raw_json TEXT
        );

        CREATE TABLE IF NOT EXISTS service_health (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          service TEXT NOT NULL,
          status TEXT NOT NULL,
          response_time INTEGER,
          timestamp INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS traces (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          trace_id TEXT NOT NULL,
          span_id TEXT,
          parent_span_id TEXT,
          service TEXT NOT NULL,
          operation TEXT NOT NULL,
          duration_ms INTEGER NOT NULL,
          status_code INTEGER,
          is_error INTEGER DEFAULT 0,
          metadata TEXT,
          timestamp INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS logs (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          service TEXT NOT NULL,
          level TEXT NOT NULL,
          message TEXT NOT NULL,
          context TEXT,
          timestamp INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_metrics_host_ts ON system_metrics(host, timestamp);
        CREATE INDEX IF NOT EXISTS idx_service_health_ts ON service_health(service, timestamp);
        CREATE INDEX IF NOT EXISTS idx_traces_service ON traces(service, timestamp);
        CREATE INDEX IF NOT EXISTS idx_traces_trace_id ON traces(trace_id);
        CREATE INDEX IF NOT EXISTS idx_logs_service ON logs(service, timestamp);
        CREATE INDEX IF NOT EXISTS idx_logs_level ON logs(level, timestamp);
      `);

      this.connected = true;
      console.log('[Store] SQLite initialized at', this.dbPath);

      // Prepare statements
      this._insertMetric = this.db.prepare(`
        INSERT INTO system_metrics (host, timestamp, cpu_usage, cpu_system, cpu_user, mem_percent, 
          mem_used, mem_total, processes_total, processes_running, net_rx_sec, net_tx_sec, 
          disk_read_sec, disk_write_sec, raw_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);

      this._insertServiceHealth = this.db.prepare(`
        INSERT INTO service_health (service, status, response_time, timestamp)
        VALUES (?, ?, ?, ?)
      `);

      this._insertTrace = this.db.prepare(`
        INSERT INTO traces (trace_id, span_id, parent_span_id, service, operation, duration_ms, status_code, is_error, metadata, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `);

      this._insertLog = this.db.prepare(`
        INSERT INTO logs (service, level, message, context, timestamp)
        VALUES (?, ?, ?, ?, ?)
      `);

      // Auto-cleanup old data (keep 7 days)
      this._cleanup();
      setInterval(() => this._cleanup(), 3600000); // Every hour

      return true;
    } catch (err) {
      console.error(`[Store] SQLite init failed: ${err.message}`);
      return false;
    }
  }

  _cleanup() {
    const cutoff = Date.now() - (7 * 24 * 60 * 60 * 1000);
    try {
      this.db.prepare('DELETE FROM system_metrics WHERE timestamp < ?').run(cutoff);
      this.db.prepare('DELETE FROM service_health WHERE timestamp < ?').run(cutoff);
      this.db.prepare('DELETE FROM traces WHERE timestamp < ?').run(cutoff);
      this.db.prepare('DELETE FROM logs WHERE timestamp < ?').run(cutoff);
    } catch (e) { /* ignore */ }
  }

  async writeMetrics(hostId, metrics) {
    this._insertMetric.run(
      hostId,
      metrics.timestamp || Date.now(),
      metrics.cpu?.usage || 0,
      metrics.cpu?.system || 0,
      metrics.cpu?.user || 0,
      metrics.memory?.usedPercent || 0,
      metrics.memory?.used || 0,
      metrics.memory?.total || 0,
      metrics.processes?.all || 0,
      metrics.processes?.running || 0,
      metrics.network?.[0]?.rxPerSec || 0,
      metrics.network?.[0]?.txPerSec || 0,
      metrics.disk?.readPerSec || 0,
      metrics.disk?.writePerSec || 0,
      JSON.stringify(metrics)
    );
  }

  async writeServiceStatus(serviceId, status, responseTime) {
    this._insertServiceHealth.run(serviceId, status, responseTime || 0, Date.now());
  }

  async writeTrace(trace) {
    this._insertTrace.run(
      trace.traceId,
      trace.spanId || null,
      trace.parentSpanId || null,
      trace.service,
      trace.operation,
      trace.duration,
      trace.statusCode || null,
      trace.isError ? 1 : 0,
      JSON.stringify(trace.metadata || {}),
      trace.timestamp || Date.now()
    );
  }

  async writeLog(log) {
    this._insertLog.run(
      log.service,
      log.level,
      log.message,
      JSON.stringify(log.context || {}),
      log.timestamp || Date.now()
    );
  }

  async queryMetrics(hostId, range = '-1h', limit = 100) {
    const ms = this._parseRange(range);
    const since = Date.now() - ms;
    return this.db.prepare(
      'SELECT * FROM system_metrics WHERE host = ? AND timestamp > ? ORDER BY timestamp DESC LIMIT ?'
    ).all(hostId, since, limit);
  }

  async queryServiceHealth(serviceId, range = '-1h', limit = 100) {
    const ms = this._parseRange(range);
    const since = Date.now() - ms;
    return this.db.prepare(
      'SELECT * FROM service_health WHERE service = ? AND timestamp > ? ORDER BY timestamp DESC LIMIT ?'
    ).all(serviceId, since, limit);
  }

  async queryTraces(options = {}) {
    let sql = 'SELECT * FROM traces WHERE 1=1';
    const params = [];

    if (options.service) { sql += ' AND service = ?'; params.push(options.service); }
    if (options.traceId) { sql += ' AND trace_id = ?'; params.push(options.traceId); }
    if (options.isError) { sql += ' AND is_error = 1'; }
    if (options.since) { sql += ' AND timestamp > ?'; params.push(options.since); }

    sql += ' ORDER BY timestamp DESC LIMIT ?';
    params.push(options.limit || 100);

    return this.db.prepare(sql).all(...params);
  }

  async queryLogs(options = {}) {
    let sql = 'SELECT * FROM logs WHERE 1=1';
    const params = [];

    if (options.service) { sql += ' AND service = ?'; params.push(options.service); }
    if (options.level) { sql += ' AND level = ?'; params.push(options.level); }
    if (options.search) { sql += ' AND message LIKE ?'; params.push(`%${options.search}%`); }
    if (options.since) { sql += ' AND timestamp > ?'; params.push(options.since); }

    sql += ' ORDER BY timestamp DESC LIMIT ?';
    params.push(options.limit || 100);

    return this.db.prepare(sql).all(...params);
  }

  async getApmStats(service, range = '-1h') {
    const ms = this._parseRange(range);
    const since = Date.now() - ms;
    const rows = this.db.prepare(`
      SELECT 
        operation,
        COUNT(*) as total_requests,
        AVG(duration_ms) as avg_duration,
        MAX(duration_ms) as max_duration,
        MIN(duration_ms) as min_duration,
        SUM(CASE WHEN is_error = 1 THEN 1 ELSE 0 END) as error_count,
        ROUND(SUM(CASE WHEN is_error = 1 THEN 1.0 ELSE 0.0 END) / COUNT(*) * 100, 2) as error_rate
      FROM traces 
      WHERE service = ? AND timestamp > ?
      GROUP BY operation
      ORDER BY total_requests DESC
    `).all(service, since);
    return rows;
  }

  _parseRange(range) {
    const match = range.match(/^-?(\d+)([smhd])$/);
    if (!match) return 3600000; // default 1h
    const val = parseInt(match[1]);
    const unit = match[2];
    const multipliers = { s: 1000, m: 60000, h: 3600000, d: 86400000 };
    return val * (multipliers[unit] || 3600000);
  }

  async flush() { /* no-op for SQLite */ }

  async close() {
    if (this.db) this.db.close();
  }
}

/**
 * Factory: tries InfluxDB first, falls back to SQLite
 */
async function createStore(config = {}) {
  if (config.type === 'influx' || process.env.INFLUXDB_URL) {
    const store = new InfluxStore({
      url: process.env.INFLUXDB_URL || config.influxUrl || 'http://localhost:8086',
      token: process.env.INFLUXDB_TOKEN || config.influxToken || 'kiro-monitor-token',
      org: process.env.INFLUXDB_ORG || config.influxOrg || 'kiro',
      bucket: process.env.INFLUXDB_BUCKET || config.influxBucket || 'metrics'
    });
    if (await store.init()) return store;
  }

  // Fallback to SQLite
  const store = new SQLiteStore({
    dbPath: config.dbPath || process.env.SQLITE_PATH
  });
  await store.init();
  return store;
}

module.exports = { createStore, InfluxStore, SQLiteStore };
