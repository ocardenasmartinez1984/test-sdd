const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const path = require('path');
const { AlertEngine } = require('./alerts/alertEngine');
const { ServiceMonitor } = require('./services/serviceMonitor');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// Service Monitor for microservices
const serviceMonitor = new ServiceMonitor({
  interval: parseInt(process.env.SERVICE_CHECK_INTERVAL) || 10000
});

// Broadcast service status changes via WebSocket
serviceMonitor.onUpdate((results) => {
  broadcastWS({ type: 'services', services: serviceMonitor.getStatus() });
});

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// In-memory metrics store (last 1000 entries per host)
const metricsStore = new Map();
const MAX_ENTRIES = 1000;

// Alert engine
const alertEngine = new AlertEngine();

// Store a metric entry
function storeMetric(hostId, metric) {
  if (!metricsStore.has(hostId)) {
    metricsStore.set(hostId, []);
  }
  const entries = metricsStore.get(hostId);
  entries.push({ ...metric, receivedAt: Date.now() });
  if (entries.length > MAX_ENTRIES) {
    entries.shift();
  }

  // Check alerts
  const alerts = alertEngine.evaluate(hostId, metric);
  if (alerts.length > 0) {
    broadcastWS({ type: 'alerts', hostId, alerts });
  }
}

// Broadcast to all WebSocket clients
function broadcastWS(data) {
  const message = JSON.stringify(data);
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(message);
    }
  });
}

// === API Routes ===

// Receive metrics from agent
app.post('/api/metrics', (req, res) => {
  const { hostId, metrics } = req.body;
  if (!hostId || !metrics) {
    return res.status(400).json({ error: 'hostId and metrics required' });
  }
  storeMetric(hostId, metrics);
  broadcastWS({ type: 'metrics', hostId, metrics });
  res.json({ status: 'ok' });
});

// Get all hosts
app.get('/api/hosts', (req, res) => {
  const hosts = [];
  for (const [hostId, entries] of metricsStore) {
    const last = entries[entries.length - 1];
    hosts.push({ hostId, lastSeen: last?.receivedAt, entryCount: entries.length });
  }
  res.json(hosts);
});

// Get metrics for a host
app.get('/api/metrics/:hostId', (req, res) => {
  const { hostId } = req.params;
  const limit = parseInt(req.query.limit) || 100;
  const entries = metricsStore.get(hostId) || [];
  res.json(entries.slice(-limit));
});

// Get current alerts configuration
app.get('/api/alerts/rules', (req, res) => {
  res.json(alertEngine.getRules());
});

// Add/update alert rule
app.post('/api/alerts/rules', (req, res) => {
  const rule = req.body;
  if (!rule.name || !rule.metric || !rule.threshold || !rule.operator) {
    return res.status(400).json({ error: 'name, metric, threshold, operator required' });
  }
  alertEngine.addRule(rule);
  res.json({ status: 'ok', rules: alertEngine.getRules() });
});

// Delete alert rule
app.delete('/api/alerts/rules/:name', (req, res) => {
  alertEngine.removeRule(req.params.name);
  res.json({ status: 'ok' });
});

// Get alert history
app.get('/api/alerts/history', (req, res) => {
  const limit = parseInt(req.query.limit) || 50;
  res.json(alertEngine.getHistory(limit));
});

// === Service Monitor API ===

// Get all services status
app.get('/api/services', (req, res) => {
  res.json(serviceMonitor.getStatus());
});

// Get single service status
app.get('/api/services/:serviceId', (req, res) => {
  const status = serviceMonitor.getStatus();
  const service = status.services.find(s => s.id === req.params.serviceId);
  if (!service) {
    return res.status(404).json({ error: 'Service not found' });
  }
  res.json(service);
});

// Get service status change history
app.get('/api/services-history', (req, res) => {
  const limit = parseInt(req.query.limit) || 50;
  res.json(serviceMonitor.getHistory(limit));
});

// Add a custom service to monitor
app.post('/api/services', (req, res) => {
  const { id, name, port, healthPath, type } = req.body;
  if (!id || !port) {
    return res.status(400).json({ error: 'id and port required' });
  }
  try {
    serviceMonitor.addService({ id, name, port, healthPath, type });
    res.json({ status: 'ok', services: serviceMonitor.getStatus() });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// Remove a service from monitoring
app.delete('/api/services/:serviceId', (req, res) => {
  serviceMonitor.removeService(req.params.serviceId);
  res.json({ status: 'ok' });
});

// WebSocket connection
wss.on('connection', (ws) => {
  console.log('[WS] Client connected');
  ws.send(JSON.stringify({
    type: 'connected',
    hosts: Array.from(metricsStore.keys()),
    services: serviceMonitor.getStatus()
  }));
  ws.on('close', () => console.log('[WS] Client disconnected'));
});

// Start server
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  // Start service monitoring
  serviceMonitor.start();

  console.log(`
╔══════════════════════════════════════════════╗
║         KIRO MONITOR - Server                ║
╠══════════════════════════════════════════════╣
║  Dashboard:  http://localhost:${PORT}            ║
║  API:        http://localhost:${PORT}/api        ║
║  WebSocket:  ws://localhost:${PORT}              ║
║  Services:   ${serviceMonitor.services.length} microservices monitored     ║
╚══════════════════════════════════════════════╝
  `);
});
