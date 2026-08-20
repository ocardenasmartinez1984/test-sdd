/**
 * Distributed Tracing Module
 * 
 * Provides OpenTelemetry-compatible trace collection and visualization.
 * Traces follow requests across microservices showing:
 * - Full request lifecycle
 * - Service-to-service calls
 * - Latency breakdown
 * - Error propagation
 */

const { v4: uuidv4 } = require('uuid');

class TraceCollector {
  constructor(store) {
    this.store = store;
    this.activeTraces = new Map();
    this.recentTraces = [];
    this.maxRecent = 500;
    this.listeners = [];
  }

  /**
   * Ingest a span from a microservice
   * Spans represent individual operations within a trace
   */
  async ingestSpan(span) {
    const normalized = {
      traceId: span.traceId || span.trace_id,
      spanId: span.spanId || span.span_id || uuidv4().slice(0, 16),
      parentSpanId: span.parentSpanId || span.parent_span_id || null,
      service: span.service || span.serviceName || 'unknown',
      operation: span.operation || span.operationName || span.name || 'unknown',
      duration: span.duration || span.duration_ms || 0,
      startTime: span.startTime || span.start_time || span.timestamp || Date.now(),
      endTime: span.endTime || span.end_time || null,
      statusCode: span.statusCode || span.status_code || span.http?.status_code || 200,
      isError: span.isError || span.error || (span.statusCode >= 400) || false,
      tags: span.tags || span.attributes || {},
      metadata: span.metadata || {}
    };

    if (normalized.endTime && !normalized.duration) {
      normalized.duration = normalized.endTime - normalized.startTime;
    }

    // Store in persistence layer
    if (this.store) {
      await this.store.writeTrace(normalized);
    }

    // Track active traces
    if (!this.activeTraces.has(normalized.traceId)) {
      this.activeTraces.set(normalized.traceId, {
        traceId: normalized.traceId,
        spans: [],
        services: new Set(),
        startTime: normalized.startTime,
        duration: 0,
        isError: false
      });
    }

    const trace = this.activeTraces.get(normalized.traceId);
    trace.spans.push(normalized);
    trace.services.add(normalized.service);
    trace.isError = trace.isError || normalized.isError;
    trace.duration = Math.max(trace.duration, normalized.duration);

    // Add to recent traces
    this.recentTraces.unshift(normalized);
    if (this.recentTraces.length > this.maxRecent) {
      this.recentTraces.pop();
    }

    // Clean up old active traces (>60s old)
    this._cleanupActiveTraces();

    // Notify listeners
    this.notifyListeners({ type: 'span', span: normalized });

    return normalized;
  }

  /**
   * Ingest batch of spans
   */
  async ingestBatch(spans) {
    const results = [];
    for (const span of spans) {
      results.push(await this.ingestSpan(span));
    }
    return results;
  }

  /**
   * Get a complete trace by ID
   */
  async getTrace(traceId) {
    // Check active traces first
    if (this.activeTraces.has(traceId)) {
      const trace = this.activeTraces.get(traceId);
      return {
        ...trace,
        services: Array.from(trace.services)
      };
    }

    // Query from store
    if (this.store && this.store.queryTraces) {
      const spans = await this.store.queryTraces({ traceId });
      if (spans.length > 0) {
        const services = new Set(spans.map(s => s.service));
        return {
          traceId,
          spans,
          services: Array.from(services),
          startTime: Math.min(...spans.map(s => s.timestamp)),
          duration: Math.max(...spans.map(s => s.duration_ms || s.duration || 0)),
          isError: spans.some(s => s.is_error)
        };
      }
    }

    return null;
  }

  /**
   * Search traces with filters
   */
  async searchTraces(options = {}) {
    if (this.store && this.store.queryTraces) {
      const since = options.range ? Date.now() - this._parseRange(options.range) : Date.now() - 3600000;
      return await this.store.queryTraces({
        service: options.service,
        isError: options.errorsOnly,
        since,
        limit: options.limit || 50
      });
    }

    // Fallback to in-memory
    let results = [...this.recentTraces];
    if (options.service) {
      results = results.filter(s => s.service === options.service);
    }
    if (options.errorsOnly) {
      results = results.filter(s => s.isError);
    }
    if (options.minDuration) {
      results = results.filter(s => s.duration >= options.minDuration);
    }
    return results.slice(0, options.limit || 50);
  }

  /**
   * Get trace statistics per service
   */
  getStats() {
    const stats = {};
    for (const span of this.recentTraces) {
      if (!stats[span.service]) {
        stats[span.service] = {
          service: span.service,
          totalSpans: 0,
          avgDuration: 0,
          maxDuration: 0,
          errorCount: 0,
          operations: {}
        };
      }
      const s = stats[span.service];
      s.totalSpans++;
      s.avgDuration = ((s.avgDuration * (s.totalSpans - 1)) + span.duration) / s.totalSpans;
      s.maxDuration = Math.max(s.maxDuration, span.duration);
      if (span.isError) s.errorCount++;

      if (!s.operations[span.operation]) {
        s.operations[span.operation] = { count: 0, avgDuration: 0, errors: 0 };
      }
      const op = s.operations[span.operation];
      op.count++;
      op.avgDuration = ((op.avgDuration * (op.count - 1)) + span.duration) / op.count;
      if (span.isError) op.errors++;
    }

    return Object.values(stats);
  }

  /**
   * Get recent traces summary
   */
  getRecent(limit = 20) {
    // Group by traceId
    const traces = new Map();
    for (const span of this.recentTraces) {
      if (!traces.has(span.traceId)) {
        traces.set(span.traceId, {
          traceId: span.traceId,
          rootService: span.service,
          rootOperation: span.operation,
          spanCount: 0,
          services: new Set(),
          duration: 0,
          isError: false,
          startTime: span.startTime
        });
      }
      const t = traces.get(span.traceId);
      t.spanCount++;
      t.services.add(span.service);
      t.duration = Math.max(t.duration, span.duration);
      t.isError = t.isError || span.isError;
    }

    return Array.from(traces.values())
      .map(t => ({ ...t, services: Array.from(t.services) }))
      .slice(0, limit);
  }

  _cleanupActiveTraces() {
    const cutoff = Date.now() - 60000;
    for (const [id, trace] of this.activeTraces) {
      if (trace.startTime < cutoff) {
        this.activeTraces.delete(id);
      }
    }
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

module.exports = { TraceCollector };
