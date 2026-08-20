/**
 * Log Aggregator - Centralized log collection and search
 * 
 * Features:
 * - Ingest logs from multiple services
 * - Full-text search
 * - Filter by service, level, time range
 * - Log pattern detection
 * - Real-time log streaming via WebSocket
 */

class LogAggregator {
  constructor(store) {
    this.store = store;
    this.recentLogs = [];
    this.maxRecent = 5000;
    this.patterns = new Map(); // Pattern detection
    this.listeners = [];
    this.stats = {
      total: 0,
      byLevel: { debug: 0, info: 0, warn: 0, error: 0, fatal: 0 },
      byService: {}
    };
  }

  /**
   * Ingest a log entry
   */
  async ingest(log) {
    const normalized = {
      service: log.service || log.source || 'unknown',
      level: (log.level || 'info').toLowerCase(),
      message: log.message || log.msg || '',
      timestamp: log.timestamp || Date.now(),
      context: log.context || log.meta || {},
      traceId: log.traceId || log.trace_id || null,
      spanId: log.spanId || log.span_id || null
    };

    // Persist
    if (this.store) {
      await this.store.writeLog(normalized);
    }

    // Update in-memory
    this.recentLogs.unshift(normalized);
    if (this.recentLogs.length > this.maxRecent) {
      this.recentLogs.pop();
    }

    // Update stats
    this.stats.total++;
    this.stats.byLevel[normalized.level] = (this.stats.byLevel[normalized.level] || 0) + 1;
    if (!this.stats.byService[normalized.service]) {
      this.stats.byService[normalized.service] = { total: 0, errors: 0 };
    }
    this.stats.byService[normalized.service].total++;
    if (normalized.level === 'error' || normalized.level === 'fatal') {
      this.stats.byService[normalized.service].errors++;
    }

    // Pattern detection
    this._detectPattern(normalized);

    // Notify listeners (for WebSocket streaming)
    this.notifyListeners({ type: 'log', log: normalized });

    return normalized;
  }

  /**
   * Ingest batch of logs
   */
  async ingestBatch(logs) {
    for (const log of logs) {
      await this.ingest(log);
    }
  }

  /**
   * Search logs with filters
   */
  async search(options = {}) {
    // Use store if available
    if (this.store && this.store.queryLogs) {
      const since = options.range ? Date.now() - this._parseRange(options.range) : undefined;
      return await this.store.queryLogs({
        service: options.service,
        level: options.level,
        search: options.query,
        since,
        limit: options.limit || 100
      });
    }

    // Fallback to in-memory
    let results = [...this.recentLogs];

    if (options.service) {
      results = results.filter(l => l.service === options.service);
    }
    if (options.level) {
      results = results.filter(l => l.level === options.level);
    }
    if (options.query) {
      const q = options.query.toLowerCase();
      results = results.filter(l => l.message.toLowerCase().includes(q));
    }
    if (options.traceId) {
      results = results.filter(l => l.traceId === options.traceId);
    }
    if (options.since) {
      results = results.filter(l => l.timestamp >= options.since);
    }

    return results.slice(0, options.limit || 100);
  }

  /**
   * Get log statistics
   */
  getStats() {
    return {
      ...this.stats,
      recentCount: this.recentLogs.length,
      errorRate: this.stats.total > 0
        ? Math.round(((this.stats.byLevel.error + this.stats.byLevel.fatal) / this.stats.total) * 10000) / 100
        : 0,
      topPatterns: this._getTopPatterns(10)
    };
  }

  /**
   * Get recent logs
   */
  getRecent(limit = 50, level = null) {
    let logs = this.recentLogs;
    if (level) {
      logs = logs.filter(l => l.level === level);
    }
    return logs.slice(0, limit);
  }

  /**
   * Get error logs summary
   */
  getErrors(limit = 30) {
    return this.recentLogs
      .filter(l => l.level === 'error' || l.level === 'fatal')
      .slice(0, limit);
  }

  /**
   * Detect repeating patterns (like same error repeated)
   */
  _detectPattern(log) {
    if (log.level !== 'error' && log.level !== 'warn') return;

    // Simple pattern: first 50 chars of message
    const key = `${log.service}:${log.message.slice(0, 50)}`;
    if (!this.patterns.has(key)) {
      this.patterns.set(key, { count: 0, firstSeen: log.timestamp, lastSeen: 0, sample: log });
    }
    const pattern = this.patterns.get(key);
    pattern.count++;
    pattern.lastSeen = log.timestamp;

    // Emit alert if pattern frequency is high
    if (pattern.count === 10 || pattern.count === 50 || pattern.count === 100) {
      this.notifyListeners({
        type: 'pattern_alert',
        pattern: {
          service: log.service,
          message: log.message.slice(0, 100),
          count: pattern.count,
          firstSeen: pattern.firstSeen,
          lastSeen: pattern.lastSeen
        }
      });
    }
  }

  _getTopPatterns(limit) {
    return Array.from(this.patterns.entries())
      .map(([key, data]) => ({ key, ...data, sample: undefined }))
      .sort((a, b) => b.count - a.count)
      .slice(0, limit);
  }

  _parseRange(range) {
    const match = range.match(/^-?(\d+)([smhd])$/);
    if (!match) return 3600000;
    const val = parseInt(match[1]);
    const unit = match[2];
    const multipliers = { s: 1000, m: 60000, h: 3600000, d: 86400000 };
    return val * (multipliers[unit] || 3600000);
  }

  onUpdate(callback) {
    this.listeners.push(callback);
  }

  notifyListeners(data) {
    for (const listener of this.listeners) {
      try { listener(data); } catch (e) { /* ignore */ }
    }
  }
}

module.exports = { LogAggregator };
