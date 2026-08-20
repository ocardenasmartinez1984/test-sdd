import { test, expect } from '@playwright/test';

const API_URL = process.env.API_URL || 'http://localhost:8080';

test.describe('Cart Service E2E', () => {
  const sessionId = `e2e-session-${Date.now()}`;
  let productId: string;

  test.beforeAll(async ({ request }) => {
    const response = await request.post(`${API_URL}/api/v1/stock`, {
      data: {
        name: `Cart Test Product ${Date.now()}`,
        sku: `SKU-CART-${Date.now()}`,
        price: 25.00,
        quantity: 100,
      },
    });
    const body = await response.json();
    productId = body.id;
  });

  test('should add item to cart', async ({ request }) => {
    const response = await request.post(`${API_URL}/api/v1/cart/${sessionId}/items`, {
      data: {
        productId: productId,
        quantity: 2,
        unitPrice: 25.00,
      },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.productId).toBe(productId);
    expect(body.quantity).toBe(2);
  });

  test('should get cart items', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/cart/${sessionId}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body)).toBeTruthy();
    expect(body.length).toBeGreaterThan(0);
  });

  test('should clear cart', async ({ request }) => {
    const response = await request.delete(`${API_URL}/api/v1/cart/${sessionId}`);
    expect(response.status()).toBe(200);

    // Verify empty
    const getResp = await request.get(`${API_URL}/api/v1/cart/${sessionId}`);
    const body = await getResp.json();
    expect(body.length).toBe(0);
  });
});
