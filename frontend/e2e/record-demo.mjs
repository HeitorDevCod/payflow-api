// Grava o fluxo de compra do modo demonstração para o README.
// Uso: node e2e/record-demo.mjs http://localhost:4300 ./video
import { chromium } from 'playwright';

const base = process.argv[2] ?? 'http://localhost:4300';
const outDir = process.argv[3] ?? './video';
const size = { width: 1280, height: 800 };

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: size,
  deviceScaleFactor: 1,
  locale: 'pt-BR',
  colorScheme: 'light',
  recordVideo: { dir: outDir, size },
});
await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: new URL(base).origin });
const page = await context.newPage();
const pause = (ms) => page.waitForTimeout(ms);
const type = async (selector, text) => {
  await page.click(selector);
  await page.keyboard.type(text, { delay: 35 });
};

await page.goto(`${base}/#/`);
await page.getByRole('heading', { name: 'Cursos e mentorias' }).waitFor();
await pause(1500);

const add = page.getByRole('button', { name: 'Adicionar' });
await add.nth(1).click();
await pause(600);
await page.getByRole('button', { name: 'Adicionar' }).nth(1).click();
await pause(1200);

await page.getByRole('link', { name: 'Ir para o pagamento' }).click();
await page.getByRole('heading', { name: 'Entrar' }).waitFor();
await pause(800);
await page.getByRole('link', { name: 'Criar conta' }).click();
await page.getByRole('heading', { name: 'Criar conta' }).waitFor();

await type('#name', 'Maria Silva');
await type('#email', 'maria@exemplo.com');
await type('#document', '52998224725');
await type('#password', 'senha-forte-1');
await pause(400);
await page.getByRole('button', { name: 'Criar conta' }).click();

await page.getByRole('heading', { name: 'Pagamento' }).waitFor();
await pause(1500);
await page.getByRole('tab', { name: /Cartão/ }).click();
await pause(1200);
await page.getByRole('tab', { name: /Pix/ }).click();
await pause(700);
await page.getByRole('button', { name: /Gerar Pix/ }).click();

await page.getByRole('heading', { name: 'Pague com Pix' }).waitFor();
await pause(3500);
await page.getByRole('button', { name: 'Copiar código' }).click().catch(() => undefined);
await pause(1200);
await page.getByRole('button', { name: /Simular pagamento/ }).click();
await page.getByRole('heading', { name: 'Pagamento aprovado' }).waitFor();
await pause(2800);

await page.getByRole('link', { name: 'Ver pedido' }).click();
await page.getByRole('heading', { name: /Pedido/ }).waitFor();
await pause(2500);

await context.close();
await browser.close();
