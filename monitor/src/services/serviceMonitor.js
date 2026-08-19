/**
 * Service Monitor - Health checks for microservices
 * Periodically polls Spring Boot Actuator endpoints
 */

const http = require('http');

// Microservice definitions
const SERVICES = [
  { id: 'eureka-server', name: 'Eureka Server', port: 8761, healthPath: '/actuator/health', type: 'infrastructure' },
  { id: 'api-gateway', name: 'API Gateway', port: 8080, healthPath: '/actuator/health', type: 'infrastructure' },
  { id: 'auth-service', name: 'Auth Service', port: 8084, healthPath: '/actuator/health', type: 'service' },
  { id: 'stock-service', name: 'Stock Service', port: 8081, healthPath: '/actuator/health', type: 'service' },
  { id: 'venta-service', name: 'Venta Service', port: 8082, healthPath: '/actuator/health', type: 'service' },
  { id: 'despacho-service', name: 'Despacho Service', port: 8083, healthPath: '/actuator/health', type: 'service' }
];

class ServiceMonitor {
  constructor(options = {}) {
    this.services = options.services || SERVICES;
    this.host = options.host || 'localhost';
    this.interval = options.interval || 10000; // 10 seconds
    this.timeout = options.timeout || 5000; // 5 second timeout
    this.state = new Map();
    this.history = [];
    this.maxHistory = 1000;
    this.listeners = [];
    this._timer = null;

    // Initialize state for all services
    for (const service of this.services) {
      this.state.set(service.id, {
        ...service,
        status: 'unknown',
        lastCheck: null,
        lastUp: null,
        lastDown: null,
        responseTime: null,
        consecutiveFailures: 0,
        details: null,
        uptime: null
      });
    }
  }

  /**
   * Check health of a single service
   */
  checkHealth(service) {
    return new Promise((resolve) => {
      const startTime = Date.now();

      const req = http.get({
        hostname: this.host,
        port: service.port,
        path: service.healthPath,
        timeout: this.timeout
      }, (res) => {
        let data = '';
        res.on('data', chunk => data += chunk);
        res.on('end', () => {
          const responseTime = Date.now() - startTime;
          let details = null;
          try {
            details = JSON.parse(data);
          } catch (e) {
            details = { raw: data };
          }

          const status = res.statusCode === 200 ? 'UP' : 'DOWN';
          resolve({
            status,
            responseTime,
            statusCode: res.statusCode,
            details
          });
        });
      });

      req.on('error', (err) => {
        const responseTime = Date.now() - startTime;
        resolve({
          status: 'DOWN',
          responseTime,
          statusCode: null,
          error: err.message,
          details: { error: err.code || err.message }
        });
      });

      req.on('timeout', () => {
        req.destroy();
        const responseTime = Date.now() - startTime;
        resolve({
          status: 'DOWN',
          responseTime,
          statusCode: null,
          error: 'Timeout',
          details: { error: 'Connection timeout' }
        });
      });
    });
  }

  /**
   * Check all services
   */
  async checkAll() {
    const results = [];

    for (const service of this.services) {
      const result = await this.checkHealth(service);
      const state = this.state.get(service.id);
      const previousStatus = state.status;
      const now = Date.now();

      // Update state
      state.status = result.status;
      state.lastCheck = now;
      state.responseTime = result.responseTime;
      state.details = result.details;

      if (result.status === 'UP') {
        state.lastUp = now;
        state.consecutiveFailures = 0;
        if (!state.uptimeStart) {
          state.uptimeStart = now;
        }
        state.uptime = now - state.uptimeStart;
      } else {
        state.lastDown = now;
        state.consecutiveFailures++;
        state.uptimeStart = null;
        state.uptime = null;
      }

      // Detect status change
      const statusChanged = previousStatus !== 'unknown' && previousStatus !== result.status;

      const entry = {
        serviceId: service.id,
        serviceName: service.name,
        status: result.status,
        previousStatus,
        statusChanged,
        responseTime: result.responseTime,
        timestamp: now,
        details: result.details
      };

      results.push(entry);

      // Log status changes
      if (statusChanged) {
        this.history.unshift({
          ...entry,
          type: 'status_change'
        });
        if (this.history.length > this.maxHistory) {
          this.history.pop();
        }
        console.log(`[ServiceMonitor] ${service.name} ${previousStatus} → ${result.status} (${result.responseTime}ms)`);
      }
    }

    // Notify listeners
    this.notifyListeners(results);

    return results;
  }

  /**
   * Start periodic monitoring
   */
  start() {
    console.log(`[ServiceMonitor] Starting monitoring of ${this.services.length} services (interval: ${this.interval / 1000}s)`);
    this.checkAll(); // Initial check
    this._timer = setInterval(() => this.checkAll(), this.interval);
  }

  /**
   * Stop monitoring
   */
  stop() {
    if (this._timer) {
      clearInterval(this._timer);
      this._timer = null;
    }
  }

  /**
   * Register a listener for service updates
   */
  onUpdate(callback) {
    this.listeners.push(callback);
  }

  /**
   * Notify all listeners
   */
  notifyListeners(results) {
    for (const listener of this.listeners) {
      try {
        listener(results);
      } catch (e) {
        console.error('[ServiceMonitor] Listener error:', e.message);
      }
    }
  }

  /**
   * Get current state of all services
   */
  getStatus() {
    const services = [];
    for (const [id, state] of this.state) {
      services.push({ ...state });
    }
    return {
      services,
      summary: {
        total: services.length,
        up: services.filter(s => s.status === 'UP').length,
        down: services.filter(s => s.status === 'DOWN').length,
        unknown: services.filter(s => s.status === 'unknown').length
      },
      lastCheck: Math.max(...services.map(s => s.lastCheck || 0))
    };
  }

  /**
   * Get status change history
   */
  getHistory(limit = 50) {
    return this.history.slice(0, limit);
  }

  /**
   * Add a custom service to monitor
   */
  addService(service) {
    if (!service.id || !service.port) {
      throw new Error('Service must have id and port');
    }
    const full = {
      healthPath: '/actuator/health',
      type: 'service',
      name: service.id,
      ...service
    };
    this.services.push(full);
    this.state.set(full.id, {
      ...full,
      status: 'unknown',
      lastCheck: null,
      lastUp: null,
      lastDown: null,
      responseTime: null,
      consecutiveFailures: 0,
      details: null,
      uptime: null
    });
  }

  /**
   * Remove a service from monitoring
   */
  removeService(id) {
    this.services = this.services.filter(s => s.id !== id);
    this.state.delete(id);
  }
}

module.exports = { ServiceMonitor, SERVICES };
