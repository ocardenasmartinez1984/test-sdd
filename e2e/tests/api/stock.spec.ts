import { test, expect } from '@playwright/test';

const API_URL = process.env.API_URL || 'http://localhost:8080';

test.describe('Stock Service E2E', () => {
  let productId: string;

  test('should create a product', async ({ request }) => {
    const response = await request.post(`${API_URL}/api/v1/stock`, {
      data: {
        name: `E2E Product ${Date.now()}`,
        sku: `SKU-E2E-${Date.now()}`,
        price: 99.99,
        quantity: 100,
        category: 'Electronics',
      },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.id).toBeDefined();
    expect(body.name).toContain('E2E Product');
    productId = body.id;
  });

  test('should get all products', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/stock`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body)).toBeTruthy();
  });

  test('should get product by id', async ({ request }) => {
    // Create first
    const createResp = await request.post(`${API_URL}/api/v1/stock`, {
      data: { name: 'GetById Test', sku: `SKU-${Date.now()}`, price: 50, quantity: 20 },
    });
    const { id } = await createResp.json();

    const response = await request.get(`${API_URL}/api/v1/stock/${id}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.id).toBe(id);
    expect(body.name).toBe('GetById Test');
  });

  test('should return 404 for non-existent product', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/stock/nonexistent-id-12345`);
    expect(response.status()).toBe(404);
  });

  test('should check available quantity', async ({ request }) => {
    const createResp = await request.post(`${API_URL}/api/v1/stock`, {
      data: { name: 'Avail Test', sku: `SKU-${Date.now()}`, price: 30, quantity: 50 },
    });
    const { id } = await createResp.json();

    const response = await request.get(`${API_URL}/api/v1/stock/${id}/available`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.availableQuantity).toBe(50);
  });

  test('should update stock quantity', async ({ request }) => {
    const createResp = await request.post(`${API_URL}/api/v1/stock`, {
      data: { name: 'Update Test', sku: `SKU-${Date.now()}`, price: 25, quantity: 30 },
    });
    const { id } = await createResp.json();

    const response = await request.put(`${API_URL}/api/v1/stock/${id}/quantity`, {
      data: { quantity: 200 },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.quantity).toBe(200);
  });
});
