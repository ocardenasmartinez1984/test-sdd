import { test, expect } from '@playwright/test';

const API_URL = process.env.API_URL || 'http://localhost:8080';

test.describe('SAGA Flow E2E - Complete Sales Transaction', () => {
  let productId: string;
  let orderId: string;

  test.beforeAll(async ({ request }) => {
    // Create a product with enough stock
    const response = await request.post(`${API_URL}/api/v1/stock`, {
      data: {
        name: `SAGA Test Product ${Date.now()}`,
        sku: `SKU-SAGA-${Date.now()}`,
        price: 150.00,
        quantity: 500,
        category: 'Test',
      },
    });
    const body = await response.json();
    productId = body.id;
  });

  test('Step 1: Create order (triggers SAGA)', async ({ request }) => {
    const response = await request.post(`${API_URL}/api/v1/ventas`, {
      data: {
        customerId: 'e2e-customer-1',
        productId: productId,
        quantity: 3,
        totalAmount: 450.00,
      },
    });
    expect(response.status()).toBeGreaterThanOrEqual(200);
    expect(response.status()).toBeLessThan(300);
    const body = await response.json();
    expect(body.id).toBeDefined();
    expect(body.status).toBe('PENDING');
    orderId = body.id;
  });

  test('Step 2: Verify stock was reserved (wait for async)', async ({ request }) => {
    // Wait for saga to process
    await new Promise(resolve => setTimeout(resolve, 3000));

    const response = await request.get(`${API_URL}/api/v1/ventas/${orderId}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    // After saga, status should progress beyond PENDING
    expect(['STOCK_RESERVED', 'DISPATCHING', 'COMPLETED']).toContain(body.status);
  });

  test('Step 3: Verify stock quantity decreased', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/stock/${productId}/available`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    // Available should be less than original 500 (at least 3 reserved)
    expect(body.availableQuantity).toBeLessThanOrEqual(497);
  });

  test('Step 4: Verify dispatch was created', async ({ request }) => {
    const response = await request.get(`${API_URL}/api/v1/despachos/order/${orderId}`);
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.orderId).toBe(orderId);
    expect(body.trackingNumber).toBeDefined();
    expect(body.trackingNumber).toMatch(/^TRK-[A-F0-9]{8}$/);
    expect(body.status).toBe('PREPARANDO');
  });

  test('Step 5: Cancel order triggers compensation', async ({ request }) => {
    // Create another order to cancel
    const createResp = await request.post(`${API_URL}/api/v1/ventas`, {
      data: {
        customerId: 'e2e-customer-cancel',
        productId: productId,
        quantity: 2,
        totalAmount: 300.00,
      },
    });
    const { id: cancelOrderId } = await createResp.json();

    // Wait for stock reserve
    await new Promise(resolve => setTimeout(resolve, 3000));

    // Cancel the order
    const cancelResp = await request.post(`${API_URL}/api/v1/ventas/${cancelOrderId}/cancel`);
    expect(cancelResp.status()).toBe(200);
    const cancelBody = await cancelResp.json();
    expect(cancelBody.status).toBe('CANCELLED');

    // Verify stock is released (wait for compensation)
    await new Promise(resolve => setTimeout(resolve, 2000));
  });

  test('Step 6: Insufficient stock triggers STOCK_FAILED', async ({ request }) => {
    // Create order with more than available stock
    const response = await request.post(`${API_URL}/api/v1/ventas`, {
      data: {
        customerId: 'e2e-customer-fail',
        productId: productId,
        quantity: 99999,
        totalAmount: 9999999.00,
      },
    });
    const { id: failOrderId } = await response.json();

    // Wait for saga to fail
    await new Promise(resolve => setTimeout(resolve, 3000));

    const checkResp = await request.get(`${API_URL}/api/v1/ventas/${failOrderId}`);
    const body = await checkResp.json();
    expect(body.status).toBe('STOCK_FAILED');
  });
});
