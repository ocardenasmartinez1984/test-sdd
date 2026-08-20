import { test, expect } from '@playwright/test';

const API_URL = process.env.API_URL || 'http://localhost:8080';

test.describe('Despacho Service E2E', () => {
  test('should list all dispatches', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/despachos`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body)).toBeTruthy();
  });

  test('should filter dispatches by status', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/despachos/status/PREPARANDO`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body)).toBeTruthy();
    body.forEach((dispatch: any) => {
      expect(dispatch.status).toBe('PREPARANDO');
    });
  });

  test('should return 404 for non-existent tracking', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/despachos/tracking/TRK-NONEXIST`);
    expect(response.status()).toBe(404);
  });
});
