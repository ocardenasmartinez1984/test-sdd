import { test, expect } from '@playwright/test';

const API_URL = process.env.API_URL || 'http://localhost:8080';

test.describe('Auth Service E2E', () => {
  test('should login with valid credentials and return JWT token', async ({ request }) => {
    const response = await request.post(`${API_URL}/api/v1/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.token).toBeDefined();
    expect(body.token.length).toBeGreaterThan(10);
  });

  test('should reject invalid credentials', async ({ request }) => {
    const response = await request.post(`${API_URL}/api/v1/auth/login`, {
      data: { username: 'invalid', password: 'wrong' },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });

  test('should validate a valid token', async ({ request }) => {
    const loginResponse = await request.post(`${API_URL}/api/v1/auth/login`, {
      data: { username: 'admin', password: 'admin123' },
    });
    const { token } = await loginResponse.json();

    const validateResponse = await request.get(`${API_URL}/api/v1/auth/validate`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(validateResponse.status()).toBe(200);
  });

  test('should reject expired/invalid token', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/auth/validate`, {
      headers: { Authorization: 'Bearer invalid.token.here' },
    });
    expect(response.status()).toBeGreaterThanOrEqual(400);
  });
});
