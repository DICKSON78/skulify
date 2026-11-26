import { chromium } from 'playwright';
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 375, height: 667 } });
  try {
    await page.goto('http://localhost:8085/dashboard', { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    const skulify = await page.locator('text=Skulify').first().isVisible().catch(() => false);
    const hamburger = await page.locator('button[aria-label="Open menu"]').first().isVisible().catch(() => false);
    console.log('SKULIFY_VISIBLE=' + skulify);
    console.log('HAMBURGER_VISIBLE=' + hamburger);
    await page.screenshot({ path: '/tmp/dashboard_mobile_check.png', fullPage: true });
    console.log('SCREENSHOT=/tmp/dashboard_mobile_check.png');
  } catch (e) {
    console.error('ERR:' + e.message);
  }
  await browser.close();
})();
