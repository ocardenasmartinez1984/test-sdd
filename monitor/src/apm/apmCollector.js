/**
 * APM Module - Application Performance Monitoring
 * 
 * Tracks per-endpoint performance:
 * - Response times (avg, p50, p95, p99)
 * - Throughput (requests/sec)
 * - Error rates
 * - Slow queries detection
 * - Apdex score
 */

class ApmCollector {
  constructor(store) {
    this.store = store;
    this.endpoints = new Map(); // service -> { endpoint -> stats }
    this.recentRequests = [];
    this.maxRecent = 2000;
    this.apdexThreshold = 500; // ms - satisfying threshold
    this.listeners = [];
  }

  /**
   * Record a request/transaction
   */
  async record(transaction) {
    const tx = {
      service: transaction.service,
      endpoint: transaction.endpoint || transaction.operation || transaction.url,
      method: transaction.method || 'GET',
      statusCode: transaction.statusCode || transaction.status_code || 200,
      duration: transaction.duration || transaction.response_time || 0,
      isError: transaction.isError || transaction.statusCode >= 400 || false,
      timestamp: transaction.timestamp || Date.now(),
      metadata: transaction.metadata || {}
    };

    // Store in persistence
    if (this.store) {
      await this.store.writeTrace({
        traceId: transaction.traceId || `apm-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        service: tx.service,
        operation: `${tx.method} ${tx.endpoint}`,
        duration: tx.duration,
        statusCode: tx.statusCode,
        isError: tx.isError,
        timestamp: tx.timestamp
      });
    }

    // Update in-memory stats
    this._updateStats(tx);

    // Add to recent
    this.recentRequests.unshift(tx);
    if (this.recentRequests.length > this.maxRecent) {
      this.recentRequests.pop();
    }

    // Notify listeners
    this.notifyListeners({ type: 'transaction', tx });

    return tx;
  }

  /**
   * Record batch of transactions
   */
  async recordBatch(transactions) {
    for (const tx of transactions) {
      await this.record(tx);
    }
  }

  _updateStats(tx) {
    if (!this.endpoints.has(tx.service)) {
      this.endpoints.set(tx.service, new Map());
    }
    const serviceEndpoints = this.endpoints.get(tx.service);
    const key = `${tx.method} ${tx.endpoint}`;

    if (!serviceEndpoints.has(key)) {
      serviceEndpoints.set(key, {
        endpoint: tx.endpoint,
        method: tx.method,
        totalRequests: 0,
        totalErrors: 0,
        totalDuration: 0,
        maxDuration: 0,
        minDuration: Infinity,
        durations: [], // Keep last 100 for percentile calculation
        lastSeen: 0,
        statusCodes: {}
      });
    }

    const stats = serviceEndpoints.get(key);
    stats.totalRequests++;
    stats.totalDuration += tx.duration;
    stats.maxDuration = Math.max(stats.maxDuration, tx.duration);
    stats.minDuration = Math.min(stats.minDuration, tx.duration);
    stats.lastSeen = tx.timestamp;

    if (tx.isError) stats.totalErrors++;

    // Track status codes
    const code = String(tx.statusCode);
    stats.statusCodes[code] = (stats.statusCodes[code] || 0) + 1;

    // Keep recent durations for percentiles
    stats.durations.push(tx.duration);
    if (stats.durations.length > 200) {
      stats.durations = stats.durations.slice(-200);
    }
  }

  /**
   * Get APM overview for all services
   */
  getOverview() {
    const services = [];

    for (const [serviceName, endpoints] of this.endpoints) {
      let totalReqs = 0, totalErrors = 0, totalDuration = 0, endpointCount = 0;

      for (const [, stats] of endpoints) {
        totalReqs += stats.totalRequests;
        totalErrors += stats.totalErrors;
        totalDuration += stats.totalDuration;
        endpointCount++;
      }

      const avgDuration = totalReqs > 0 ? Math.round(totalDuration / totalReqs) : 0;
      const errorRate = totalReqs > 0 ? Math.round((totalErrors / totalReqs) * 10000) / 100 : 0;

      services.push({
        service: serviceName,
        totalRequests: totalReqs,
        totalErrors,
        errorRate,
        avgResponseTime: avgDuration,
        endpointCount,
        apdex: this._calculateServiceApdex(serviceName)
      });
    }

    return services;
  }

  /**
   * Get detailed stats for a specific service
   */
  getServiceDetail(serviceName) {
    const serviceEndpoints = this.endpoints.get(serviceName);
    if (!serviceEndpoints) return null;

    const endpoints = [];
    for (const [key, stats] of serviceEndpoints) {
      const sorted = [...stats.durations].sort((a, b) => a - b);
      endpoints.push({
        key,
        endpoint: stats.endpoint,
        method: stats.method,
        totalRequests: stats.totalRequests,
        totalErrors: stats.totalErrors,
        errorRate: stats.totalRequests > 0 ? Math.round((stats.totalErrors / stats.totalRequests) * 10000) / 100 : 0,
        avgDuration: Math.round(stats.totalDuration / stats.totalRequests),
        minDuration: stats.minDuration === Infinity ? 0 : stats.minDuration,
        maxDuration: stats.maxDuration,
        p50: this._percentile(sorted, 50),
        p95: this._percentile(sorted, 95),
        p99: this._percentile(sorted, 99),
        throughput: this._calculateThroughput(stats),
        apdex: this._calculateApdex(stats.durations),
        statusCodes: stats.statusCodes,
        lastSeen: stats.lastSeen
      });
    }

    return {
      service: serviceName,
      endpoints: endpoints.sort((a, b) => b.totalRequests - a.totalRequests)
    };
  }

  /**
   * Get slow requests (>1s by default)
   */
  getSlowRequests(threshold = 1000, limit = 20) {
    return this.recentRequests
      .filter(r => r.duration > threshold)
      .slice(0, limit);
  }

  /**
   * Get error requests
   */
  getErrors(limit = 20) {
    return this.recentRequests
      .filter(r => r.isError)
      .slice(0, limit);
  }

  _percentile(sorted, p) {
    if (sorted.length === 0) return 0;
    const idx = Math.ceil((p / 100) * sorted.length) - 1;
    return Math.round(sorted[Math.max(0, idx)]);
  }

  _calculateApdex(durations) {
    if (durations.length === 0) return 1;
    const satisfied = durations.filter(d => d <= this.apdexThreshold).length;
    const tolerating = durations.filter(d => d > this.apdexThreshold && d <= this.apdexThreshold * 4).length;
    return Math.round(((satisfied + tolerating / 2) / durations.length) * 100) / 100;
  }

  _calculateServiceApdex(serviceName) {
    const serviceEndpoints = this.endpoints.get(serviceName);
    if (!serviceEndpoints) return 1;
    const allDurations = [];
    for (const [, stats] of serviceEndpoints) {
      allDurations.push(...stats.durations);
    }
    return this._calculateApdex(allDurations);
  }

  _calculateThroughput(stats) {
    // Requests per minute based on recent data
    if (stats.durations.length < 2) return 0;
    const timeWindow = stats.lastSeen - (stats.lastSeen - 60000);
    const recentCount = this.recentRequests.filter(r =>
      r.endpoint === stats.endpoint && r.timestamp > Date.now() - 60000
    ).length;
    return recentCount;
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

module.exports = { ApmCollector };
