import { chromium } from 'playwright';
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
  try {
    await page.goto('http://localhost:8085/dashboard', { waitUntil: 'networkidle', timeout: 15000 });
    await page.waitForTimeout(2000);
    const skulifyText = await page.locator('text=Skulify').first().isVisible().catch(() => false);
    const menuText = await page.locator('text=Dashboard').first().isVisible().catch(() => false);
    console.log('SIDEBAR_SKULIFY_VISIBLE=' + skulifyText);
    console.log('MENU_DASHBOARD_VISIBLE=' + menuText);
    await page.screenshot({ path: '/tmp/dashboard_check.png', fullPage: true });
    console.log('SCREENSHOT=/tmp/dashboard_check.png');
  } catch (e) {
    console.error('ERR:' + e.message);
  }
  await browser.close();
})();
