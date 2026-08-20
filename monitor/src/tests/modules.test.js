/**
 * Complete test suite for Kiro Monitor
 * Uses Node.js built-in test runner (node --test)
 */

const { describe, it, beforeEach } = require('node:test');
const assert = require('node:assert/strict');

// === Alert Engine Tests ===
describe('AlertEngine', () => {
  const { AlertEngine } = require('../alerts/alertEngine');
  let engine;

  beforeEach(() => {
    engine = new AlertEngine();
  });

  it('should load default rules', () => {
    assert.equal(engine.getRules().length, 4);
  });

  it('should add a new rule', () => {
    engine.addRule({ name: 'Test', metric: 'cpu.usage', operator: '>', threshold: 50 });
    assert.equal(engine.getRules().length, 5);
  });

  it('should update existing rule by name', () => {
    engine.addRule({ name: 'High CPU Usage', metric: 'cpu.usage', operator: '>', threshold: 99 });
    const rule = engine.getRules().find(r => r.name === 'High CPU Usage');
    assert.equal(rule.threshold, 99);
    assert.equal(engine.getRules().length, 4); // No new rule added
  });

  it('should remove a rule', () => {
    engine.removeRule('High CPU Usage');
    assert.equal(engine.getRules().length, 3);
  });

  it('should not trigger alert below threshold', () => {
    const alerts = engine.evaluate('host1', { cpu: { usage: 50 }, memory: { usedPercent: 40 } });
    assert.equal(alerts.length, 0);
  });

  it('should trigger alert when threshold exceeded with duration=1', () => {
    const alerts = engine.evaluate('host1', {
      cpu: { usage: 95 },
      memory: { usedPercent: 96 },
      filesystem: [{ usedPercent: 95 }]
    });
    assert(alerts.length >= 1);
    assert(alerts.some(a => a.severity === 'critical'));
  });

  it('should respect cooldown period', () => {
    engine.evaluate('host1', { memory: { usedPercent: 96 }, filesystem: [{ usedPercent: 95 }] });
    const alerts = engine.evaluate('host1', { memory: { usedPercent: 96 }, filesystem: [{ usedPercent: 95 }] });
    assert.equal(alerts.length, 0);
  });

  it('should record alert history', () => {
    engine.evaluate('host1', { memory: { usedPercent: 96 } });
    assert(engine.getHistory().length > 0);
  });

  it('should extract nested values correctly', () => {
    assert.equal(engine.getNestedValue({ a: { b: 42 } }, 'a.b'), 42);
    assert.equal(engine.getNestedValue({ arr: [{ x: 10 }] }, 'arr.0.x'), 10);
    assert.equal(engine.getNestedValue({}, 'a.b.c'), undefined);
  });

  it('should compare with all operators', () => {
    assert.equal(engine.compare(10, '>', 5), true);
    assert.equal(engine.compare(10, '<', 5), false);
    assert.equal(engine.compare(10, '>=', 10), true);
    assert.equal(engine.compare(10, '<=', 10), true);
    assert.equal(engine.compare(10, '==', 10), true);
    assert.equal(engine.compare(10, '!=', 5), true);
  });

  it('should respect duration requirement', () => {
    // High CPU requires 3 consecutive violations
    const alerts1 = engine.evaluate('host2', { cpu: { usage: 95 } });
    assert.equal(alerts1.length, 0); // 1st violation
    const alerts2 = engine.evaluate('host2', { cpu: { usage: 95 } });
    assert.equal(alerts2.length, 0); // 2nd violation
    const alerts3 = engine.evaluate('host2', { cpu: { usage: 95 } });
    assert(alerts3.some(a => a.rule === 'High CPU Usage')); // 3rd - triggers
  });

  it('should reset violation counter when condition clears', () => {
    engine.evaluate('host3', { cpu: { usage: 95 } }); // violation 1
    engine.evaluate('host3', { cpu: { usage: 95 } }); // violation 2
    engine.evaluate('host3', { cpu: { usage: 50 } }); // clears
    engine.evaluate('host3', { cpu: { usage: 95 } }); // violation 1 again
    engine.evaluate('host3', { cpu: { usage: 95 } }); // violation 2
    const alerts = engine.evaluate('host3', { cpu: { usage: 50 } }); // clears
    assert.equal(alerts.length, 0);
  });
});

// === Anomaly Detector Tests ===
describe('AnomalyDetector', () => {
  const { AnomalyDetector } = require('../anomaly/anomalyDetector');
  let detector;

  beforeEach(() => {
    detector = new AnomalyDetector({ windowSize: 30, zScoreThreshold: 2.5 });
  });

  it('should not detect anomaly with insufficient data', () => {
    const result = detector.detect('cpu', 50);
    assert.equal(result, null);
  });

  it('should learn baseline from normal data', () => {
    for (let i = 0; i < 20; i++) {
      detector.detect('cpu', 50 + Math.random() * 5);
    }
    const baselines = detector.getBaselines();
    assert(baselines.cpu);
    assert(baselines.cpu.mean > 48 && baselines.cpu.mean < 56);
  });

  it('should detect z-score anomaly', () => {
    // Feed stable data
    for (let i = 0; i < 20; i++) {
      detector.detect('cpu', 50);
    }
    // Inject anomaly
    const result = detector.detect('cpu', 99);
    assert(result !== null);
    assert(result.some(a => a.type === 'zscore'));
  });

  it('should detect spike anomaly', () => {
    // Feed gradually changing data
    for (let i = 0; i < 20; i++) {
      detector.detect('metric1', 50 + (i % 3));
    }
    // Sudden spike
    const result = detector.detect('metric1', 200);
    assert(result !== null);
    assert(result.some(a => a.type === 'spike'));
  });

  it('should classify severity correctly', () => {
    for (let i = 0; i < 20; i++) {
      detector.detect('test', 50);
    }
    const result = detector.detect('test', 200); // Extreme outlier
    assert(result !== null);
    const zsAnomaly = result.find(a => a.type === 'zscore');
    assert(zsAnomaly.severity === 'critical' || zsAnomaly.severity === 'warning');
  });

  it('should analyze full metrics object', () => {
    // Build baseline
    for (let i = 0; i < 15; i++) {
      detector.analyzeMetrics('host1', {
        timestamp: Date.now(),
        cpu: { usage: 30 },
        memory: { usedPercent: 60 },
        network: [{ rxPerSec: 1000 }],
        processes: { all: 200 }
      });
    }
    // Should not trigger with normal values
    const anomalies = detector.analyzeMetrics('host1', {
      timestamp: Date.now(),
      cpu: { usage: 32 },
      memory: { usedPercent: 61 },
      network: [{ rxPerSec: 1100 }],
      processes: { all: 205 }
    });
    assert.equal(anomalies.length, 0);
  });

  it('should store anomalies history', () => {
    for (let i = 0; i < 20; i++) {
      detector.detect('test2', 50);
    }
    detector.detect('test2', 200);
    const anomalies = detector.getAnomalies();
    assert(anomalies.length > 0);
  });

  it('should filter anomalies by type', () => {
    for (let i = 0; i < 20; i++) {
      detector.detect('test3', 50);
    }
    detector.detect('test3', 200);
    const spikes = detector.getAnomalies({ type: 'spike' });
    assert(spikes.every(a => a.type === 'spike'));
  });
});

// === APM Collector Tests ===
describe('ApmCollector', () => {
  const { ApmCollector } = require('../apm/apmCollector');
  let apm;

  beforeEach(() => {
    apm = new ApmCollector(null);
  });

  it('should record a transaction', async () => {
    await apm.record({
      service: 'api-gateway',
      endpoint: '/api/users',
      method: 'GET',
      duration: 120,
      statusCode: 200
    });
    const overview = apm.getOverview();
    assert.equal(overview.length, 1);
    assert.equal(overview[0].service, 'api-gateway');
    assert.equal(overview[0].totalRequests, 1);
  });

  it('should calculate percentiles', async () => {
    const durations = [10, 20, 30, 40, 50, 100, 200, 500, 1000, 2000];
    for (const d of durations) {
      await apm.record({ service: 'svc', endpoint: '/test', method: 'GET', duration: d, statusCode: 200 });
    }
    const detail = apm.getServiceDetail('svc');
    const ep = detail.endpoints[0];
    assert(ep.p50 > 0);
    assert(ep.p95 > ep.p50);
    assert(ep.p99 >= ep.p95);
  });

  it('should calculate error rates', async () => {
    for (let i = 0; i < 8; i++) {
      await apm.record({ service: 'svc', endpoint: '/api', method: 'GET', duration: 50, statusCode: 200 });
    }
    for (let i = 0; i < 2; i++) {
      await apm.record({ service: 'svc', endpoint: '/api', method: 'GET', duration: 50, statusCode: 500, isError: true });
    }
    const detail = apm.getServiceDetail('svc');
    assert.equal(detail.endpoints[0].errorRate, 20); // 2/10 = 20%
  });

  it('should calculate apdex score', async () => {
    // All fast requests -> apdex close to 1
    for (let i = 0; i < 10; i++) {
      await apm.record({ service: 'fast', endpoint: '/api', method: 'GET', duration: 100, statusCode: 200 });
    }
    const overview = apm.getOverview();
    const fast = overview.find(s => s.service === 'fast');
    assert(fast.apdex >= 0.9);
  });

  it('should detect slow requests', async () => {
    await apm.record({ service: 'svc', endpoint: '/slow', method: 'GET', duration: 5000, statusCode: 200 });
    const slow = apm.getSlowRequests(1000);
    assert.equal(slow.length, 1);
    assert.equal(slow[0].duration, 5000);
  });

  it('should track multiple services independently', async () => {
    await apm.record({ service: 'svc-a', endpoint: '/a', method: 'GET', duration: 50, statusCode: 200 });
    await apm.record({ service: 'svc-b', endpoint: '/b', method: 'POST', duration: 100, statusCode: 201 });
    const overview = apm.getOverview();
    assert.equal(overview.length, 2);
  });
});

// === Log Aggregator Tests ===
describe('LogAggregator', () => {
  const { LogAggregator } = require('../logs/logAggregator');
  let logs;

  beforeEach(() => {
    logs = new LogAggregator(null);
  });

  it('should ingest a log entry', async () => {
    await logs.ingest({ service: 'auth', level: 'info', message: 'User logged in' });
    assert.equal(logs.getRecent(10).length, 1);
  });

  it('should normalize log levels', async () => {
    await logs.ingest({ service: 'svc', level: 'ERROR', message: 'fail' });
    const recent = logs.getRecent(1);
    assert.equal(recent[0].level, 'error');
  });

  it('should search logs by message', async () => {
    await logs.ingest({ service: 'svc', level: 'info', message: 'Processing order #123' });
    await logs.ingest({ service: 'svc', level: 'info', message: 'User login successful' });
    const results = await logs.search({ query: 'order' });
    assert.equal(results.length, 1);
    assert(results[0].message.includes('order'));
  });

  it('should filter by service', async () => {
    await logs.ingest({ service: 'auth', level: 'info', message: 'login' });
    await logs.ingest({ service: 'stock', level: 'info', message: 'updated' });
    const results = await logs.search({ service: 'auth' });
    assert.equal(results.length, 1);
    assert.equal(results[0].service, 'auth');
  });

  it('should filter by level', async () => {
    await logs.ingest({ service: 'svc', level: 'info', message: 'ok' });
    await logs.ingest({ service: 'svc', level: 'error', message: 'fail' });
    const errors = await logs.search({ level: 'error' });
    assert.equal(errors.length, 1);
  });

  it('should track statistics', async () => {
    await logs.ingest({ service: 'svc', level: 'info', message: 'ok' });
    await logs.ingest({ service: 'svc', level: 'error', message: 'fail' });
    await logs.ingest({ service: 'svc', level: 'error', message: 'fail2' });
    const stats = logs.getStats();
    assert.equal(stats.total, 3);
    assert.equal(stats.byLevel.error, 2);
    assert.equal(stats.byLevel.info, 1);
  });

  it('should detect error patterns', async () => {
    let patternAlert = null;
    logs.onUpdate(data => {
      if (data.type === 'pattern_alert') patternAlert = data.pattern;
    });

    for (let i = 0; i < 10; i++) {
      await logs.ingest({ service: 'svc', level: 'error', message: 'Connection refused to DB' });
    }
    assert(patternAlert !== null);
    assert.equal(patternAlert.count, 10);
  });

  it('should get error logs only', async () => {
    await logs.ingest({ service: 'svc', level: 'info', message: 'ok' });
    await logs.ingest({ service: 'svc', level: 'error', message: 'bad' });
    await logs.ingest({ service: 'svc', level: 'fatal', message: 'crash' });
    const errors = logs.getErrors();
    assert.equal(errors.length, 2);
  });
});

// === Trace Collector Tests ===
describe('TraceCollector', () => {
  const { TraceCollector } = require('../tracing/traceCollector');
  let traces;

  beforeEach(() => {
    traces = new TraceCollector(null);
  });

  it('should ingest a span', async () => {
    const span = await traces.ingestSpan({
      traceId: 'trace-1',
      service: 'api-gateway',
      operation: 'GET /api/users',
      duration: 150
    });
    assert.equal(span.traceId, 'trace-1');
    assert.equal(span.service, 'api-gateway');
  });

  it('should group spans by trace ID', async () => {
    await traces.ingestSpan({ traceId: 'trace-1', service: 'gateway', operation: 'GET /api', duration: 100 });
    await traces.ingestSpan({ traceId: 'trace-1', service: 'auth', operation: 'validate', duration: 30 });
    const trace = await traces.getTrace('trace-1');
    assert.equal(trace.spans.length, 2);
    assert.deepEqual(trace.services.sort(), ['auth', 'gateway']);
  });

  it('should search traces by service', async () => {
    await traces.ingestSpan({ traceId: 't1', service: 'gateway', operation: 'op1', duration: 50 });
    await traces.ingestSpan({ traceId: 't2', service: 'auth', operation: 'op2', duration: 60 });
    const results = await traces.searchTraces({ service: 'auth' });
    assert(results.every(r => r.service === 'auth'));
  });

  it('should filter error traces', async () => {
    await traces.ingestSpan({ traceId: 't1', service: 'svc', operation: 'op', duration: 50, isError: false });
    await traces.ingestSpan({ traceId: 't2', service: 'svc', operation: 'op', duration: 50, isError: true });
    const results = await traces.searchTraces({ errorsOnly: true });
    assert(results.every(r => r.isError));
  });

  it('should calculate stats per service', async () => {
    for (let i = 0; i < 5; i++) {
      await traces.ingestSpan({ traceId: `t${i}`, service: 'svc', operation: 'op', duration: 50 + i * 10 });
    }
    const stats = traces.getStats();
    const svcStats = stats.find(s => s.service === 'svc');
    assert.equal(svcStats.totalSpans, 5);
    assert(svcStats.avgDuration > 0);
  });

  it('should get recent traces summary', async () => {
    await traces.ingestSpan({ traceId: 'trace-1', service: 'gw', operation: 'GET /api', duration: 100 });
    await traces.ingestSpan({ traceId: 'trace-1', service: 'auth', operation: 'validate', duration: 30 });
    const recent = traces.getRecent(10);
    assert(recent.length > 0);
    assert.equal(recent[0].traceId, 'trace-1');
    assert.equal(recent[0].spanCount, 2);
  });
});

// === Service Map Tests ===
describe('ServiceMap', () => {
  const { ServiceMap } = require('../topology/serviceMap');
  let map;

  beforeEach(() => {
    map = new ServiceMap();
    map.init();
  });

  it('should initialize with default nodes', () => {
    const topology = map.getMap();
    assert(topology.nodes.length >= 6);
  });

  it('should initialize with default edges', () => {
    const topology = map.getMap();
    assert(topology.edges.length > 0);
  });

  it('should update node status', () => {
    map.updateNodeStatus('api-gateway', 'UP', 15);
    const topology = map.getMap();
    const gw = topology.nodes.find(n => n.id === 'api-gateway');
    assert.equal(gw.status, 'UP');
  });

  it('should record service calls', () => {
    map.recordCall('api-gateway', 'auth-service', 25, false);
    const topology = map.getMap();
    const edge = topology.edges.find(e => e.source === 'api-gateway' && e.target === 'auth-service');
    assert.equal(edge.metrics.requests, 1);
    assert.equal(edge.metrics.avgLatency, 25);
  });

  it('should calculate error rates on edges', () => {
    for (let i = 0; i < 8; i++) map.recordCall('gw', 'svc', 20, false);
    for (let i = 0; i < 2; i++) map.recordCall('gw', 'svc', 20, true);
    const topology = map.getMap();
    const edge = topology.edges.find(e => e.source === 'gw' && e.target === 'svc');
    assert.equal(edge.metrics.errorRate, 20);
  });

  it('should auto-discover new edges from calls', () => {
    const before = map.getMap().edges.length;
    map.recordCall('new-service', 'another-service', 50, false);
    const after = map.getMap().edges.length;
    assert(after > before);
  });

  it('should perform impact analysis', () => {
    const impact = map.getImpactAnalysis('auth-service');
    assert(impact.directlyImpacted.includes('api-gateway'));
  });

  it('should provide summary statistics', () => {
    map.updateNodeStatus('api-gateway', 'UP', 10);
    map.updateNodeStatus('auth-service', 'DOWN', 0);
    const summary = map.getSummary();
    assert.equal(summary.totalNodes, 10); // default nodes
    assert(summary.nodesByStatus.up >= 1);
    assert(summary.nodesByStatus.down >= 1);
  });
});

// === Auth Manager Tests ===
describe('AuthManager', () => {
  const { AuthManager } = require('../auth/authManager');
  let auth;

  beforeEach(async () => {
    auth = new AuthManager();
    await auth.init();
  });

  it('should initialize with default admin', () => {
    const users = auth.listUsers();
    assert(users.some(u => u.username === 'admin' && u.role === 'admin'));
  });

  it('should authenticate valid credentials', async () => {
    const result = await auth.authenticate('admin', 'admin123');
    assert(result !== null);
    assert(result.token);
    assert.equal(result.user.role, 'admin');
  });

  it('should reject invalid credentials', async () => {
    const result = await auth.authenticate('admin', 'wrongpass');
    assert.equal(result, null);
  });

  it('should verify JWT tokens', async () => {
    const { token } = await auth.authenticate('admin', 'admin123');
    const decoded = auth.verifyToken(token);
    assert(decoded !== null);
    assert.equal(decoded.username, 'admin');
    assert.equal(decoded.role, 'admin');
  });

  it('should reject invalid tokens', () => {
    const decoded = auth.verifyToken('invalid-token');
    assert.equal(decoded, null);
  });

  it('should create new users', async () => {
    const user = await auth.createUser('viewer1', 'pass123', 'viewer');
    assert.equal(user.username, 'viewer1');
    assert.equal(user.role, 'viewer');
  });

  it('should reject duplicate usernames', async () => {
    await assert.rejects(
      async () => auth.createUser('admin', 'pass', 'viewer'),
      { message: 'User already exists' }
    );
  });

  it('should generate API keys', () => {
    const key = auth.generateApiKey('test-agent', 'agent');
    assert(key.startsWith('km_'));
    assert(key.length > 20);
  });

  it('should verify API keys', () => {
    const key = auth.generateApiKey('my-agent', 'agent');
    const result = auth.verifyApiKey(key);
    assert.equal(result.name, 'my-agent');
    assert.equal(result.role, 'agent');
  });

  it('should reject invalid API keys', () => {
    const result = auth.verifyApiKey('invalid-key');
    assert.equal(result, null);
  });

  it('should revoke API keys', () => {
    const key = auth.generateApiKey('temp', 'agent');
    assert(auth.verifyApiKey(key) !== null);
    auth.revokeApiKey(key);
    assert.equal(auth.verifyApiKey(key), null);
  });
});
