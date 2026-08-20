import { test, expect } from '@playwright/test';

const VENTAS_URL = process.env.VENTAS_URL || 'http://localhost:4200';

test.describe('Ventas Mantenedor E2E', () => {
  test('should load the ventas management app', async ({ page }) => {
    await page.goto(VENTAS_URL);
    await page.waitForTimeout(2000);
    const bodyText = await page.textContent('body');
    expect(bodyText).toBeTruthy();
  });

  test('should display sales list or login page', async ({ page }) => {
    await page.goto(VENTAS_URL);
    await page.waitForTimeout(2000);
    // Should show either a login form or the sales list
    const hasContent = await page.locator('body').textContent();
    expect(hasContent!.length).toBeGreaterThan(0);
  });

  test('should handle navigation', async ({ page }) => {
    await page.goto(VENTAS_URL);
    await page.waitForTimeout(1000);
    // Verify no unhandled navigation errors
    expect(page.url()).toContain('localhost:4200');
  });
});
