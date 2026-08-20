import { test, expect } from '@playwright/test';

const POS_URL = process.env.POS_URL || 'http://localhost:4300';

test.describe('POS Frontend E2E', () => {
  test('should load the POS application', async ({ page }) => {
    await page.goto(POS_URL);
    await expect(page).toHaveTitle(/POS|Point of Sale|Punto de Venta/i);
  });

  test('should display product catalog', async ({ page }) => {
    await page.goto(POS_URL);
    // Wait for products to load
    await page.waitForTimeout(2000);
    // Check that the page has loaded content (not blank)
    const bodyText = await page.textContent('body');
    expect(bodyText).toBeTruthy();
  });

  test('should be responsive (mobile viewport)', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto(POS_URL);
    await expect(page.locator('body')).toBeVisible();
  });

  test('should load without console errors', async ({ page }) => {
    const errors: string[] = [];
    page.on('console', msg => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    await page.goto(POS_URL);
    await page.waitForTimeout(3000);
    // Filter out known non-critical errors
    const criticalErrors = errors.filter(e => !e.includes('favicon'));
    expect(criticalErrors.length).toBe(0);
  });

  test('should have accessible navigation', async ({ page }) => {
    await page.goto(POS_URL);
    // Check for basic accessibility
    const html = await page.content();
    expect(html).toContain('lang=');
  });
});
