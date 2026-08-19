const si = require('systeminformation');
const os = require('os');
const http = require('http');

// Configuration
const CONFIG = {
  serverUrl: process.env.MONITOR_SERVER || 'http://localhost:3000',
  interval: parseInt(process.env.COLLECT_INTERVAL) || 5000,
  hostId: process.env.HOST_ID || os.hostname()
};

async function collectMetrics() {
  try {
    const [cpu, mem, disk, network, load, processes, fsSize, battery, temp] = await Promise.all([
      si.currentLoad(),
      si.mem(),
      si.disksIO(),
      si.networkStats(),
      si.fullLoad(),
      si.processes(),
      si.fsSize(),
      si.battery(),
      si.cpuTemperature()
    ]);

    const metrics = {
      timestamp: Date.now(),
      cpu: {
        usage: Math.round(cpu.currentLoad * 100) / 100,
        cores: cpu.cpus.map(c => Math.round(c.load * 100) / 100),
        system: Math.round(cpu.currentLoadSystem * 100) / 100,
        user: Math.round(cpu.currentLoadUser * 100) / 100
      },
      memory: {
        total: mem.total,
        used: mem.used,
        free: mem.free,
        usedPercent: Math.round((mem.used / mem.total) * 10000) / 100,
        swapTotal: mem.swaptotal,
        swapUsed: mem.swapused
      },
      disk: {
        readBytes: disk?.rIO || 0,
        writeBytes: disk?.wIO || 0,
        readPerSec: disk?.rIO_sec || 0,
        writePerSec: disk?.wIO_sec || 0
      },
      filesystem: fsSize.map(fs => ({
        mount: fs.mount,
        size: fs.size,
        used: fs.used,
        usedPercent: Math.round(fs.use * 100) / 100
      })),
      network: network.map(iface => ({
        interface: iface.iface,
        rxBytes: iface.rx_bytes,
        txBytes: iface.tx_bytes,
        rxPerSec: Math.round(iface.rx_sec || 0),
        txPerSec: Math.round(iface.tx_sec || 0)
      })),
      processes: {
        all: processes.all,
        running: processes.running,
        blocked: processes.blocked,
        sleeping: processes.sleeping,
        topCpu: processes.list
          .sort((a, b) => b.cpu - a.cpu)
          .slice(0, 5)
          .map(p => ({ name: p.name, pid: p.pid, cpu: p.cpu, mem: p.mem })),
        topMemory: processes.list
          .sort((a, b) => b.mem - a.mem)
          .slice(0, 5)
          .map(p => ({ name: p.name, pid: p.pid, cpu: p.cpu, mem: p.mem }))
      },
      system: {
        uptime: os.uptime(),
        platform: os.platform(),
        arch: os.arch(),
        hostname: os.hostname(),
        loadAvg: os.loadavg()
      },
      temperature: {
        main: temp?.main || null,
        cores: temp?.cores || []
      },
      battery: battery.hasBattery ? {
        percent: battery.percent,
        isCharging: battery.isCharging,
        timeRemaining: battery.timeRemaining
      } : null
    };

    return metrics;
  } catch (error) {
    console.error('[Agent] Error collecting metrics:', error.message);
    return null;
  }
}

function sendMetrics(metrics) {
  const data = JSON.stringify({ hostId: CONFIG.hostId, metrics });

  const url = new URL(CONFIG.serverUrl + '/api/metrics');
  const options = {
    hostname: url.hostname,
    port: url.port,
    path: url.pathname,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(data)
    }
  };

  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => resolve(body));
    });
    req.on('error', (err) => reject(err));
    req.write(data);
    req.end();
  });
}

async function run() {
  console.log(`
╔══════════════════════════════════════════════╗
║         KIRO MONITOR - Agent                 ║
╠══════════════════════════════════════════════╣
║  Host ID:   ${CONFIG.hostId.padEnd(30)}  ║
║  Server:    ${CONFIG.serverUrl.padEnd(30)}  ║
║  Interval:  ${(CONFIG.interval / 1000 + 's').padEnd(30)}  ║
╚══════════════════════════════════════════════╝
  `);

  // Initial collection
  const initial = await collectMetrics();
  if (initial) {
    try {
      await sendMetrics(initial);
      console.log(`[Agent] Initial metrics sent successfully`);
    } catch (err) {
      console.error(`[Agent] Server not available: ${err.message}`);
      console.log('[Agent] Will retry on next interval...');
    }
  }

  // Periodic collection
  setInterval(async () => {
    const metrics = await collectMetrics();
    if (metrics) {
      try {
        await sendMetrics(metrics);
        const ts = new Date().toLocaleTimeString();
        console.log(`[${ts}] CPU: ${metrics.cpu.usage}% | RAM: ${metrics.memory.usedPercent}% | Processes: ${metrics.processes.all}`);
      } catch (err) {
        console.error(`[Agent] Failed to send metrics: ${err.message}`);
      }
    }
  }, CONFIG.interval);
}

run();
