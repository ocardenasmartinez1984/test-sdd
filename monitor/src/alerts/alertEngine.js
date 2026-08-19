/**
 * Alert Engine - Evaluates metrics against configurable rules
 * and triggers alerts when thresholds are exceeded.
 */

class AlertEngine {
  constructor() {
    // Default alert rules
    this.rules = [
      {
        name: 'High CPU Usage',
        metric: 'cpu.usage',
        operator: '>',
        threshold: 90,
        severity: 'critical',
        duration: 3, // must exceed for N consecutive checks
        message: 'CPU usage exceeded 90%'
      },
      {
        name: 'High Memory Usage',
        metric: 'memory.usedPercent',
        operator: '>',
        threshold: 85,
        severity: 'warning',
        duration: 3,
        message: 'Memory usage exceeded 85%'
      },
      {
        name: 'Critical Memory',
        metric: 'memory.usedPercent',
        operator: '>',
        threshold: 95,
        severity: 'critical',
        duration: 1,
        message: 'Memory usage exceeded 95% - Critical!'
      },
      {
        name: 'High Disk Usage',
        metric: 'filesystem.0.usedPercent',
        operator: '>',
        threshold: 90,
        severity: 'warning',
        duration: 1,
        message: 'Disk usage exceeded 90%'
      }
    ];

    // Track consecutive violations per rule per host
    this.violations = new Map();

    // Alert history
    this.history = [];
    this.maxHistory = 500;

    // Cooldown: don't re-fire the same alert within N ms
    this.cooldown = 60000; // 1 minute
    this.lastFired = new Map();
  }

  /**
   * Get a nested value from an object using dot notation
   * Supports array index notation like "filesystem.0.usedPercent"
   */
  getNestedValue(obj, path) {
    const parts = path.split('.');
    let current = obj;
    for (const part of parts) {
      if (current === undefined || current === null) return undefined;
      if (!isNaN(part)) {
        current = current[parseInt(part)];
      } else {
        current = current[part];
      }
    }
    return current;
  }

  /**
   * Compare a value against a threshold using the given operator
   */
  compare(value, operator, threshold) {
    switch (operator) {
      case '>': return value > threshold;
      case '>=': return value >= threshold;
      case '<': return value < threshold;
      case '<=': return value <= threshold;
      case '==': return value === threshold;
      case '!=': return value !== threshold;
      default: return false;
    }
  }

  /**
   * Evaluate all rules against the current metrics
   * Returns an array of triggered alerts
   */
  evaluate(hostId, metrics) {
    const triggered = [];

    for (const rule of this.rules) {
      const value = this.getNestedValue(metrics, rule.metric);
      if (value === undefined || value === null) continue;

      const key = `${hostId}:${rule.name}`;
      const isViolation = this.compare(value, rule.operator, rule.threshold);

      if (isViolation) {
        // Increment violation counter
        const count = (this.violations.get(key) || 0) + 1;
        this.violations.set(key, count);

        // Check if duration threshold met
        if (count >= (rule.duration || 1)) {
          // Check cooldown
          const lastTime = this.lastFired.get(key) || 0;
          if (Date.now() - lastTime > this.cooldown) {
            const alert = {
              rule: rule.name,
              metric: rule.metric,
              value: Math.round(value * 100) / 100,
              threshold: rule.threshold,
              operator: rule.operator,
              severity: rule.severity || 'warning',
              message: rule.message || `${rule.metric} ${rule.operator} ${rule.threshold}`,
              hostId,
              timestamp: Date.now()
            };

            triggered.push(alert);
            this.history.unshift(alert);
            if (this.history.length > this.maxHistory) {
              this.history.pop();
            }
            this.lastFired.set(key, Date.now());

            console.log(`[ALERT] ${alert.severity.toUpperCase()}: ${alert.rule} on ${hostId} (${value} ${rule.operator} ${rule.threshold})`);
          }
        }
      } else {
        // Reset violation counter when condition clears
        this.violations.set(key, 0);
      }
    }

    return triggered;
  }

  /**
   * Add or update a rule
   */
  addRule(rule) {
    const existing = this.rules.findIndex(r => r.name === rule.name);
    if (existing >= 0) {
      this.rules[existing] = { ...this.rules[existing], ...rule };
    } else {
      this.rules.push({
        duration: 1,
        severity: 'warning',
        ...rule
      });
    }
  }

  /**
   * Remove a rule by name
   */
  removeRule(name) {
    this.rules = this.rules.filter(r => r.name !== name);
  }

  /**
   * Get all rules
   */
  getRules() {
    return this.rules;
  }

  /**
   * Get alert history
   */
  getHistory(limit = 50) {
    return this.history.slice(0, limit);
  }
}

module.exports = { AlertEngine };
