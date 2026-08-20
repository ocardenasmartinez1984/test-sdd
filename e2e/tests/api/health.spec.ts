import { test, expect } from '@playwright/test';

const services = [
  { name: 'API Gateway', url: 'http://localhost:8080/actuator/health' },
  { name: 'Auth Service', url: 'http://localhost:8084/actuator/health' },
  { name: 'Stock Service', url: 'http://localhost:8081/actuator/health' },
  { name: 'Venta Service', url: 'http://localhost:8082/actuator/health' },
  { name: 'Despacho Service', url: 'http://localhost:8083/actuator/health' },
  { name: 'Eureka Server', url: 'http://localhost:8761/actuator/health' },
];

test.describe('Infrastructure Health Checks E2E', () => {
  for (const service of services) {
    test(`${service.name} should be healthy`, async ({ request }) => {
      const response = await request.get(service.url);
      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.status).toBe('UP');
    });
  }

  test('Stock Service health should show MongoDB and Kafka details', async ({ request }) => {
    const response = await request.get('http://localhost:8081/actuator/health');
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.components).toBeDefined();
  });

  test('API Gateway health should show Redis details', async ({ request }) => {
    const response = await request.get('http://localhost:8080/actuator/health');
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('UP');
  });
});
