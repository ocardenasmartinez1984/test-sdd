/**
 * Basic smoke tests for Kiro Monitor
 */

const { AlertEngine } = require('./alerts/alertEngine');

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  ✓ ${message}`);
    passed++;
  } else {
    console.log(`  ✗ ${message}`);
    failed++;
  }
}

console.log('\n📊 Kiro Monitor - Tests\n');

// Test AlertEngine
console.log('AlertEngine:');

const engine = new AlertEngine();

// Test: Rules loaded
assert(engine.getRules().length === 4, 'Default rules loaded (4 rules)');

// Test: Add rule
engine.addRule({ name: 'Test Rule', metric: 'cpu.usage', operator: '>', threshold: 50, severity: 'warning' });
assert(engine.getRules().length === 5, 'Can add a new rule');

// Test: Remove rule
engine.removeRule('Test Rule');
assert(engine.getRules().length === 4, 'Can remove a rule');

// Test: Evaluate - no alert (value below threshold)
const alerts1 = engine.evaluate('test-host', { cpu: { usage: 50 }, memory: { usedPercent: 40 } });
assert(alerts1.length === 0, 'No alert when values below threshold');

// Test: Evaluate - alert triggered (value above threshold, duration=1)
const alerts2 = engine.evaluate('test-host2', {
  cpu: { usage: 95 },
  memory: { usedPercent: 96 },
  filesystem: [{ usedPercent: 95 }]
});
// Critical Memory has duration=1 and Disk has duration=1
assert(alerts2.length >= 1, 'Alert triggered when value exceeds threshold (duration=1)');

// Test: History
assert(engine.getHistory().length > 0, 'Alert history recorded');

// Test: Nested value extraction
assert(engine.getNestedValue({ a: { b: { c: 42 } } }, 'a.b.c') === 42, 'Nested value extraction works');
assert(engine.getNestedValue({ arr: [{ x: 10 }] }, 'arr.0.x') === 10, 'Array index extraction works');

// Test: Comparison operators
assert(engine.compare(10, '>', 5) === true, 'Operator > works');
assert(engine.compare(10, '<', 5) === false, 'Operator < works');
assert(engine.compare(10, '>=', 10) === true, 'Operator >= works');
assert(engine.compare(10, '==', 10) === true, 'Operator == works');
assert(engine.compare(10, '!=', 5) === true, 'Operator != works');

// Test: Cooldown (same alert should not fire again immediately)
const alerts3 = engine.evaluate('test-host2', {
  cpu: { usage: 95 },
  memory: { usedPercent: 96 },
  filesystem: [{ usedPercent: 95 }]
});
assert(alerts3.length === 0, 'Cooldown prevents re-firing same alert');

// Summary
console.log(`\n${'─'.repeat(40)}`);
console.log(`Results: ${passed} passed, ${failed} failed, ${passed + failed} total`);
console.log(`${'─'.repeat(40)}\n`);

process.exit(failed > 0 ? 1 : 0);
