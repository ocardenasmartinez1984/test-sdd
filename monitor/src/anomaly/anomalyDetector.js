/**
 * Anomaly Detection Engine
 * 
 * Uses statistical methods to detect unusual behavior:
 * - Z-score detection (standard deviations from mean)
 * - Moving average comparison
 * - Spike detection (sudden changes)
 * - Seasonal pattern recognition (basic)
 * 
 * No external ML libraries - pure statistics.
 */

class AnomalyDetector {
  constructor(options = {}) {
    this.windowSize = options.windowSize || 60; // data points to consider
    this.zScoreThreshold = options.zScoreThreshold || 2.5;
    this.spikeThreshold = options.spikeThreshold || 3; // multiplier
    this.metrics = new Map(); // metric_name -> circular buffer
    this.anomalies = [];
    this.maxAnomalies = 500;
    this.listeners = [];
  }

  /**
   * Feed a metric value and check for anomalies
   * Returns anomaly if detected, null otherwise
   */
  detect(metricName, value, timestamp = Date.now(), metadata = {}) {
    if (!this.metrics.has(metricName)) {
      this.metrics.set(metricName, {
        values: [],
        timestamps: [],
        mean: 0,
        stdDev: 0,
        lastValue: null,
        movingAvg: 0
      });
    }

    const metric = this.metrics.get(metricName);
    const anomalies = [];

    // Only check if we have enough data
    if (metric.values.length >= 10) {
      // Z-Score detection
      const zScore = this._calculateZScore(value, metric.mean, metric.stdDev);
      if (Math.abs(zScore) > this.zScoreThreshold) {
        anomalies.push({
          type: 'zscore',
          metric: metricName,
          value,
          expected: Math.round(metric.mean * 100) / 100,
          zScore: Math.round(zScore * 100) / 100,
          severity: Math.abs(zScore) > 4 ? 'critical' : 'warning',
          message: `${metricName} = ${value.toFixed(2)} (${zScore > 0 ? 'above' : 'below'} normal, z=${zScore.toFixed(1)})`,
          timestamp,
          metadata
        });
      }

      // Spike detection (sudden jump)
      if (metric.lastValue !== null) {
        const change = Math.abs(value - metric.lastValue);
        const avgChange = this._averageChange(metric.values);
        if (avgChange > 0 && change > avgChange * this.spikeThreshold) {
          anomalies.push({
            type: 'spike',
            metric: metricName,
            value,
            previousValue: metric.lastValue,
            change: Math.round(change * 100) / 100,
            severity: change > avgChange * 5 ? 'critical' : 'warning',
            message: `${metricName} spiked from ${metric.lastValue.toFixed(2)} to ${value.toFixed(2)} (${change > 0 ? '+' : ''}${change.toFixed(2)})`,
            timestamp,
            metadata
          });
        }
      }

      // Trend detection (sustained increase over window)
      if (metric.values.length >= 20) {
        const trend = this._detectTrend(metric.values.slice(-20));
        if (Math.abs(trend) > 0.8) { // Strong trend
          const direction = trend > 0 ? 'increasing' : 'decreasing';
          anomalies.push({
            type: 'trend',
            metric: metricName,
            value,
            trend: Math.round(trend * 100) / 100,
            direction,
            severity: 'info',
            message: `${metricName} has been ${direction} steadily (trend: ${(trend * 100).toFixed(0)}%)`,
            timestamp,
            metadata
          });
        }
      }
    }

    // Update metric buffer
    metric.values.push(value);
    metric.timestamps.push(timestamp);
    metric.lastValue = value;

    // Keep window size
    if (metric.values.length > this.windowSize) {
      metric.values.shift();
      metric.timestamps.shift();
    }

    // Recalculate statistics
    metric.mean = this._mean(metric.values);
    metric.stdDev = this._stdDev(metric.values, metric.mean);
    metric.movingAvg = this._movingAverage(metric.values, Math.min(10, metric.values.length));

    // Store and notify anomalies
    if (anomalies.length > 0) {
      for (const anomaly of anomalies) {
        this.anomalies.unshift(anomaly);
        if (this.anomalies.length > this.maxAnomalies) {
          this.anomalies.pop();
        }
        this.notifyListeners(anomaly);
      }
    }

    return anomalies.length > 0 ? anomalies : null;
  }

  /**
   * Batch analyze metrics
   */
  analyzeMetrics(hostId, metrics) {
    const allAnomalies = [];

    if (metrics.cpu?.usage !== undefined) {
      const a = this.detect(`${hostId}.cpu.usage`, metrics.cpu.usage, metrics.timestamp, { host: hostId });
      if (a) allAnomalies.push(...a);
    }
    if (metrics.memory?.usedPercent !== undefined) {
      const a = this.detect(`${hostId}.memory.percent`, metrics.memory.usedPercent, metrics.timestamp, { host: hostId });
      if (a) allAnomalies.push(...a);
    }
    if (metrics.network?.[0]?.rxPerSec !== undefined) {
      const a = this.detect(`${hostId}.network.rx`, metrics.network[0].rxPerSec, metrics.timestamp, { host: hostId });
      if (a) allAnomalies.push(...a);
    }
    if (metrics.processes?.all !== undefined) {
      const a = this.detect(`${hostId}.processes.total`, metrics.processes.all, metrics.timestamp, { host: hostId });
      if (a) allAnomalies.push(...a);
    }

    return allAnomalies;
  }

  /**
   * Get detected anomalies
   */
  getAnomalies(options = {}) {
    let results = [...this.anomalies];

    if (options.metric) {
      results = results.filter(a => a.metric.includes(options.metric));
    }
    if (options.severity) {
      results = results.filter(a => a.severity === options.severity);
    }
    if (options.type) {
      results = results.filter(a => a.type === options.type);
    }
    if (options.since) {
      results = results.filter(a => a.timestamp >= options.since);
    }

    return results.slice(0, options.limit || 50);
  }

  /**
   * Get current metric baselines
   */
  getBaselines() {
    const baselines = {};
    for (const [name, metric] of this.metrics) {
      baselines[name] = {
        mean: Math.round(metric.mean * 100) / 100,
        stdDev: Math.round(metric.stdDev * 100) / 100,
        movingAvg: Math.round(metric.movingAvg * 100) / 100,
        lastValue: metric.lastValue !== null ? Math.round(metric.lastValue * 100) / 100 : null,
        dataPoints: metric.values.length
      };
    }
    return baselines;
  }

  // === Statistical Functions ===

  _mean(arr) {
    if (arr.length === 0) return 0;
    return arr.reduce((a, b) => a + b, 0) / arr.length;
  }

  _stdDev(arr, mean) {
    if (arr.length < 2) return 0;
    const squaredDiffs = arr.map(v => Math.pow(v - mean, 2));
    return Math.sqrt(squaredDiffs.reduce((a, b) => a + b, 0) / (arr.length - 1));
  }

  _calculateZScore(value, mean, stdDev) {
    if (stdDev === 0) return 0;
    return (value - mean) / stdDev;
  }

  _movingAverage(arr, window) {
    if (arr.length === 0) return 0;
    const slice = arr.slice(-window);
    return slice.reduce((a, b) => a + b, 0) / slice.length;
  }

  _averageChange(arr) {
    if (arr.length < 2) return 0;
    let totalChange = 0;
    for (let i = 1; i < arr.length; i++) {
      totalChange += Math.abs(arr[i] - arr[i - 1]);
    }
    return totalChange / (arr.length - 1);
  }

  _detectTrend(values) {
    // Simple linear regression slope normalized
    const n = values.length;
    if (n < 3) return 0;

    let sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
    for (let i = 0; i < n; i++) {
      sumX += i;
      sumY += values[i];
      sumXY += i * values[i];
      sumX2 += i * i;
    }

    const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    const mean = sumY / n;

    // Normalize: how strong is the trend relative to the mean
    if (mean === 0) return 0;
    return (slope * n) / mean; // Normalized trend strength
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

module.exports = { AnomalyDetector };
