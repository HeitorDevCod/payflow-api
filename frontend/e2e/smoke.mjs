// Teste de fumaça do modo demonstração: percorre os fluxos de pagamento pela interface.
// Uso: node e2e/smoke.mjs http://localhost:4300
import { chromium } from 'playwright';

const base = process.argv[2] ?? 'http://localhost:4300';
const browser = await chromium.launch();
const context = await browser.newContext({ locale: 'pt-BR' });
await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: new URL(base).origin });
const page = await context.newPage();
const errors = [];
page.on('pageerror', (error) => errors.push(error.message));
page.on('console', (message) => message.type() === 'error' && errors.push(message.text()));

const step = async (name, fn) => {
  try {
    await fn();
    console.log(`ok   ${name}`);
  } catch (error) {
    console.log(`FAIL ${name}`);
    await page.screenshot({ path: `smoke-failure.png`, fullPage: true });
    throw error;
  }
};
const heading = (name) => page.getByRole('heading', { name }).waitFor({ timeout: 8000 });

const addAndCheckout = async (index) => {
  await page.goto(`${base}/#/`);
  await heading('Cursos e mentorias');
  await page.getByRole('button', { name: 'Adicionar' }).nth(index).click();
  await page.getByRole('link', { name: 'Ir para o pagamento' }).click();
  await heading('Pagamento');
};

await step('cadastro e login', async () => {
  await page.goto(`${base}/#/cadastro`);
  await page.fill('#name', 'Ana Souza');
  await page.fill('#email', 'ana@exemplo.com');
  await page.fill('#document', '111.444.777-35');
  await page.fill('#password', 'senha-forte-1');
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await heading('Cursos e mentorias');
});

await step('CPF inválido é recusado no cadastro', async () => {
  await page.getByRole('button', { name: 'Sair' }).click();
  await page.goto(`${base}/#/cadastro`);
  await page.fill('#name', 'Teste');
  await page.fill('#email', 'x@exemplo.com');
  await page.fill('#document', '123.456.789-00');
  await page.fill('#password', 'senha-forte-1');
  await page.getByRole('button', { name: 'Criar conta' }).click();
  await page.getByText('CPF inválido').waitFor();
  await page.goto(`${base}/#/entrar`);
  await page.fill('#email', 'ana@exemplo.com');
  await page.fill('#password', 'senha-forte-1');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await heading('Cursos e mentorias');
});

await step('cartão recusado (OTHE) e nova tentativa aprovada em 3x', async () => {
  await addAndCheckout(0);
  await page.getByRole('tab', { name: /Cartão/ }).click();
  await page.fill('#cc-name', 'OTHE');
  await page.getByRole('button', { name: /^Pagar/ }).click();
  await heading('Pagamento não aprovado');
  await page.getByText('O emissor do cartão recusou o pagamento.').waitFor();
  await page.getByRole('link', { name: 'Tentar outra forma de pagamento' }).click();
  await heading('Pagamento');
  await page.getByRole('tab', { name: /Cartão/ }).click();
  await page.selectOption('#cc-installments', { index: 2 });
  await page.getByRole('button', { name: /^Pagar/ }).click();
  await heading('Pagamento aprovado');
  await page.getByText('em 3x de').waitFor();
});

await step('boleto com validação de endereço e simulação de pagamento', async () => {
  await addAndCheckout(2);
  await page.getByRole('tab', { name: /Boleto/ }).click();
  await page.fill('#cep', '123');
  await page.fill('#uf', 'sp');
  await page.fill('#street', 'Rua das Flores');
  await page.fill('#number', '10');
  await page.fill('#district', 'Centro');
  await page.fill('#city', 'São Paulo');
  await page.getByRole('button', { name: /Emitir boleto/ }).click();
  await page.getByText('CEP inválido').waitFor();
  await page.fill('#cep', '01001-000');
  await page.getByRole('button', { name: /Emitir boleto/ }).click();
  await heading('Boleto emitido');
  await page.getByRole('button', { name: 'Copiar linha digitável' }).click();
  await page.getByRole('button', { name: 'Linha copiada' }).waitFor();
  await page.getByRole('button', { name: /Simular pagamento do boleto/ }).click();
  await heading('Pagamento aprovado');
});

await step('Pix cancelado libera o pedido para nova tentativa', async () => {
  await addAndCheckout(3);
  await page.getByRole('button', { name: /Gerar Pix/ }).click();
  await heading('Pague com Pix');
  await page.getByRole('img', { name: 'QR Code do Pix' }).waitFor();
  await page.getByRole('button', { name: 'Cancelar pagamento' }).click();
  await heading('Pagamento cancelado');
  await page.getByRole('link', { name: 'Ver pedido' }).click();
  await page.getByRole('link', { name: 'Pagar pedido' }).waitFor();
  await page.getByRole('button', { name: 'Cancelar pedido' }).click();
  await page.getByText('Cancelado').first().waitFor();
});

await step('parcela mínima de R$ 5,00 limita as opções', async () => {
  await addAndCheckout(3);
  await page.getByRole('tab', { name: /Cartão/ }).click();
  const options = await page.locator('#cc-installments option').count();
  if (options !== 3) throw new Error(`esperava 3 opções de parcela para R$ 19,90, veio ${options}`);
});

await step('assinatura: criar, pausar, retomar e cancelar', async () => {
  await page.goto(`${base}/#/assinaturas`);
  await heading('Assinaturas');
  await page.getByRole('radio', { name: /Anual/ }).click();
  await page.getByRole('button', { name: /Assinar por/ }).click();
  await page.getByText('Plano Anual', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'Pausar' }).click();
  await page.getByText('Pausada').waitFor();
  await page.getByRole('button', { name: 'Retomar' }).click();
  await page.getByText('Ativa').waitFor();
  await page.getByRole('button', { name: 'Cancelar' }).click();
  await page.getByText('Cancelada').waitFor();
});

await step('lista de pedidos', async () => {
  await page.goto(`${base}/#/pedidos`);
  await heading('Meus pedidos');
  const rows = await page.locator('.rows__item').count();
  if (rows < 4) throw new Error(`esperava ao menos 4 pedidos, veio ${rows}`);
});

await browser.close();
if (errors.length) {
  console.log('Erros no console:\n' + errors.join('\n'));
  process.exit(1);
}
console.log('Todos os fluxos passaram.');
