/**
 * Service Map - Dependency topology visualization
 * 
 * Builds a graph of service dependencies based on:
 * - Trace data (which services call which)
 * - Health check data
 * - Configuration (known dependencies)
 * 
 * Provides:
 * - Node (service) status and metrics
 * - Edge (connection) latency and error rates
 * - Topology auto-discovery from traces
 */

class ServiceMap {
  constructor() {
    // Nodes: services in the system
    this.nodes = new Map();
    // Edges: connections between services
    this.edges = new Map(); // "source->target" -> stats
    // Known architecture
    this.knownDependencies = [];
  }

  /**
   * Initialize with known architecture
   */
  init(services = [], dependencies = []) {
    // Define known services
    const defaultServices = [
      { id: 'api-gateway', name: 'API Gateway', type: 'gateway', port: 8080 },
      { id: 'auth-service', name: 'Auth Service', type: 'service', port: 8084 },
      { id: 'stock-service', name: 'Stock Service', type: 'service', port: 8081 },
      { id: 'venta-service', name: 'Venta Service', type: 'service', port: 8082 },
      { id: 'despacho-service', name: 'Despacho Service', type: 'service', port: 8083 },
      { id: 'eureka-server', name: 'Eureka Server', type: 'infrastructure', port: 8761 },
      { id: 'postgresql', name: 'PostgreSQL', type: 'database', port: 5432 },
      { id: 'mongodb', name: 'MongoDB', type: 'database', port: 27017 },
      { id: 'kafka', name: 'Kafka', type: 'messaging', port: 9092 },
      { id: 'frontend', name: 'Frontend', type: 'frontend', port: 4200 }
    ];

    for (const svc of [...defaultServices, ...services]) {
      this.nodes.set(svc.id, {
        ...svc,
        status: 'unknown',
        metrics: { requests: 0, errors: 0, avgLatency: 0 },
        lastSeen: null
      });
    }

    // Define known dependencies
    const defaultDeps = [
      { source: 'frontend', target: 'api-gateway', protocol: 'HTTP' },
      { source: 'api-gateway', target: 'auth-service', protocol: 'HTTP' },
      { source: 'api-gateway', target: 'stock-service', protocol: 'HTTP' },
      { source: 'api-gateway', target: 'venta-service', protocol: 'HTTP' },
      { source: 'api-gateway', target: 'despacho-service', protocol: 'HTTP' },
      { source: 'venta-service', target: 'kafka', protocol: 'Kafka' },
      { source: 'despacho-service', target: 'kafka', protocol: 'Kafka' },
      { source: 'stock-service', target: 'kafka', protocol: 'Kafka' },
      { source: 'auth-service', target: 'postgresql', protocol: 'TCP' },
      { source: 'venta-service', target: 'mongodb', protocol: 'TCP' },
      { source: 'stock-service', target: 'postgresql', protocol: 'TCP' },
      { source: 'despacho-service', target: 'mongodb', protocol: 'TCP' },
      { source: 'api-gateway', target: 'eureka-server', protocol: 'HTTP' },
      { source: 'auth-service', target: 'eureka-server', protocol: 'HTTP' },
      { source: 'stock-service', target: 'eureka-server', protocol: 'HTTP' },
      { source: 'venta-service', target: 'eureka-server', protocol: 'HTTP' },
      { source: 'despacho-service', target: 'eureka-server', protocol: 'HTTP' }
    ];

    for (const dep of [...defaultDeps, ...dependencies]) {
      const key = `${dep.source}->${dep.target}`;
      this.edges.set(key, {
        ...dep,
        status: 'unknown',
        requests: 0,
        errors: 0,
        avgLatency: 0,
        lastSeen: null
      });
    }

    this.knownDependencies = [...defaultDeps, ...dependencies];
    console.log(`[ServiceMap] Initialized with ${this.nodes.size} nodes, ${this.edges.size} edges`);
  }

  /**
   * Update node status from health checks
   */
  updateNodeStatus(serviceId, status, responseTime) {
    const node = this.nodes.get(serviceId);
    if (node) {
      node.status = status;
      node.lastSeen = Date.now();
      node.metrics.avgLatency = responseTime || 0;
    }
  }

  /**
   * Record a service-to-service call (from traces)
   */
  recordCall(source, target, duration, isError = false) {
    const key = `${source}->${target}`;

    if (!this.edges.has(key)) {
      // Auto-discover new dependency
      this.edges.set(key, {
        source,
        target,
        protocol: 'discovered',
        status: 'unknown',
        requests: 0,
        errors: 0,
        avgLatency: 0,
        lastSeen: null
      });
    }

    const edge = this.edges.get(key);
    edge.requests++;
    if (isError) edge.errors++;
    edge.avgLatency = ((edge.avgLatency * (edge.requests - 1)) + duration) / edge.requests;
    edge.status = isError ? 'degraded' : 'healthy';
    edge.lastSeen = Date.now();

    // Also update source node metrics
    const sourceNode = this.nodes.get(source);
    if (sourceNode) {
      sourceNode.metrics.requests++;
      if (isError) sourceNode.metrics.errors++;
    }
  }

  /**
   * Get the full service map for visualization
   */
  getMap() {
    const nodes = [];
    for (const [id, node] of this.nodes) {
      nodes.push({
        id,
        label: node.name,
        type: node.type,
        status: node.status,
        port: node.port,
        metrics: node.metrics,
        lastSeen: node.lastSeen
      });
    }

    const edges = [];
    for (const [key, edge] of this.edges) {
      const errorRate = edge.requests > 0 ? Math.round((edge.errors / edge.requests) * 10000) / 100 : 0;
      edges.push({
        id: key,
        source: edge.source,
        target: edge.target,
        protocol: edge.protocol,
        status: edge.status,
        metrics: {
          requests: edge.requests,
          errors: edge.errors,
          errorRate,
          avgLatency: Math.round(edge.avgLatency)
        },
        lastSeen: edge.lastSeen
      });
    }

    return { nodes, edges };
  }

  /**
   * Get topology summary
   */
  getSummary() {
    const nodesByStatus = { up: 0, down: 0, unknown: 0, degraded: 0 };
    for (const [, node] of this.nodes) {
      const s = node.status === 'UP' ? 'up' : node.status === 'DOWN' ? 'down' : node.status === 'degraded' ? 'degraded' : 'unknown';
      nodesByStatus[s]++;
    }

    const edgesByStatus = { healthy: 0, degraded: 0, unknown: 0 };
    for (const [, edge] of this.edges) {
      const s = edge.status === 'healthy' ? 'healthy' : edge.status === 'degraded' ? 'degraded' : 'unknown';
      edgesByStatus[s]++;
    }

    return {
      totalNodes: this.nodes.size,
      totalEdges: this.edges.size,
      nodesByStatus,
      edgesByStatus
    };
  }

  /**
   * Get impacted services when a given service is down
   */
  getImpactAnalysis(serviceId) {
    const impacted = new Set();
    const queue = [serviceId];

    while (queue.length > 0) {
      const current = queue.shift();
      for (const [key, edge] of this.edges) {
        if (edge.target === current && !impacted.has(edge.source)) {
          impacted.add(edge.source);
          queue.push(edge.source);
        }
      }
    }

    return {
      service: serviceId,
      directlyImpacted: Array.from(this.edges.entries())
        .filter(([, e]) => e.target === serviceId)
        .map(([, e]) => e.source),
      totalImpacted: Array.from(impacted)
    };
  }
}

module.exports = { ServiceMap };
