/**
 * Auth Module - JWT authentication + API keys for agents
 * 
 * - JWT tokens for dashboard users (login/session)
 * - API keys for agent-to-server communication
 * - Rate limiting per client
 * - Role-based access (admin, viewer, agent)
 */

const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const crypto = require('crypto');

const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(32).toString('hex');
const JWT_EXPIRY = process.env.JWT_EXPIRY || '24h';

// In-memory user store (replace with DB in production)
const users = new Map();
const apiKeys = new Map();

// Default admin user
const DEFAULT_ADMIN = {
  username: process.env.ADMIN_USER || 'admin',
  password: process.env.ADMIN_PASS || 'admin123',
  role: 'admin'
};

class AuthManager {
  constructor() {
    this.initialized = false;
  }

  async init() {
    // Create default admin
    const hash = await bcrypt.hash(DEFAULT_ADMIN.password, 10);
    users.set(DEFAULT_ADMIN.username, {
      username: DEFAULT_ADMIN.username,
      passwordHash: hash,
      role: DEFAULT_ADMIN.role,
      createdAt: Date.now()
    });

    // Create default agent API key
    const defaultKey = process.env.AGENT_API_KEY || 'kiro-agent-key-' + crypto.randomBytes(8).toString('hex');
    apiKeys.set(defaultKey, {
      name: 'default-agent',
      role: 'agent',
      createdAt: Date.now(),
      lastUsed: null
    });

    this.initialized = true;
    console.log(`[Auth] Initialized - Admin: ${DEFAULT_ADMIN.username}, Agent Key: ${defaultKey.slice(0, 16)}...`);
    return defaultKey;
  }

  // === User Management ===

  async createUser(username, password, role = 'viewer') {
    if (users.has(username)) throw new Error('User already exists');
    const hash = await bcrypt.hash(password, 10);
    users.set(username, { username, passwordHash: hash, role, createdAt: Date.now() });
    return { username, role };
  }

  async authenticate(username, password) {
    const user = users.get(username);
    if (!user) return null;

    const valid = await bcrypt.compare(password, user.passwordHash);
    if (!valid) return null;

    const token = jwt.sign(
      { username: user.username, role: user.role },
      JWT_SECRET,
      { expiresIn: JWT_EXPIRY }
    );

    return { token, user: { username: user.username, role: user.role } };
  }

  verifyToken(token) {
    try {
      return jwt.verify(token, JWT_SECRET);
    } catch (err) {
      return null;
    }
  }

  // === API Key Management ===

  generateApiKey(name, role = 'agent') {
    const key = 'km_' + crypto.randomBytes(24).toString('hex');
    apiKeys.set(key, { name, role, createdAt: Date.now(), lastUsed: null });
    return key;
  }

  verifyApiKey(key) {
    const entry = apiKeys.get(key);
    if (!entry) return null;
    entry.lastUsed = Date.now();
    return { name: entry.name, role: entry.role };
  }

  revokeApiKey(key) {
    return apiKeys.delete(key);
  }

  listApiKeys() {
    const keys = [];
    for (const [key, data] of apiKeys) {
      keys.push({ key: key.slice(0, 12) + '...', ...data });
    }
    return keys;
  }

  listUsers() {
    const result = [];
    for (const [, user] of users) {
      result.push({ username: user.username, role: user.role, createdAt: user.createdAt });
    }
    return result;
  }
}

// === Express Middleware ===

/**
 * Middleware: Authenticate via JWT token OR API key
 * Sets req.auth with { username/name, role }
 */
function authMiddleware(authManager) {
  return (req, res, next) => {
    // Skip auth for login and health endpoints
    const publicPaths = ['/api/auth/login', '/api/health', '/'];
    if (publicPaths.includes(req.path) || req.path.startsWith('/api/auth/login')) {
      return next();
    }

    // Allow static files
    if (!req.path.startsWith('/api/')) {
      return next();
    }

    // Check API key header
    const apiKey = req.headers['x-api-key'];
    if (apiKey) {
      const agent = authManager.verifyApiKey(apiKey);
      if (agent) {
        req.auth = agent;
        return next();
      }
    }

    // Check JWT Bearer token
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.slice(7);
      const decoded = authManager.verifyToken(token);
      if (decoded) {
        req.auth = { username: decoded.username, role: decoded.role };
        return next();
      }
    }

    return res.status(401).json({ error: 'Unauthorized', message: 'Valid API key or JWT token required' });
  };
}

/**
 * Middleware: Require specific role
 */
function requireRole(...roles) {
  return (req, res, next) => {
    if (!req.auth || !roles.includes(req.auth.role)) {
      return res.status(403).json({ error: 'Forbidden', message: `Requires role: ${roles.join(' or ')}` });
    }
    next();
  };
}

module.exports = { AuthManager, authMiddleware, requireRole };
