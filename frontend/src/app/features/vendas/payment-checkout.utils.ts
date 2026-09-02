export interface PaymentRuleLike {
  ativo: boolean;
  formaPagamento: string;
  parcelas: number;
  adquirenteId: number | null;
  bandeira: string | null;
  taxaPercentual: number;
  taxaFixa: number;
}

export interface PaymentChoiceLike {
  formaPagamento: string;
  parcelas?: number;
  adquirenteId?: number | null;
  bandeira?: string | null;
}

export function roundMoney(value: number): number {
  return Math.round((Number(value || 0) + Number.EPSILON) * 100) / 100;
}

export function selectPaymentRule<T extends PaymentRuleLike>(
  rules: T[],
  payment: PaymentChoiceLike,
): T | undefined {
  const brand = payment.bandeira?.trim().toUpperCase() || null;
  return rules
    .filter((rule) => rule.ativo)
    .filter((rule) => rule.formaPagamento === payment.formaPagamento)
    .filter((rule) => rule.parcelas === Number(payment.parcelas || 1))
    .filter((rule) => rule.adquirenteId == null || rule.adquirenteId === payment.adquirenteId)
    .filter((rule) => rule.bandeira == null || rule.bandeira.toUpperCase() === brand)
    .sort((a, b) => specificity(b) - specificity(a))[0];
}

export function calculatePaymentFee(value: number, rule?: PaymentRuleLike): number {
  if (!rule) return 0;
  return roundMoney(value * (Number(rule.taxaPercentual || 0) / 100) + Number(rule.taxaFixa || 0));
}

export function paymentTotalsMatch(saleTotal: number, paymentValues: number[]): boolean {
  const paymentsTotal = roundMoney(paymentValues.reduce((total, value) => total + Number(value || 0), 0));
  return Math.abs(paymentsTotal - roundMoney(saleTotal)) < 0.009;
}

export function calculateCashChange(amountDue: number, amountReceived: number | null | undefined): number {
  const received = amountReceived == null ? amountDue : Number(amountReceived);
  return roundMoney(Math.max(0, received - amountDue));
}

function specificity(rule: PaymentRuleLike): number {
  return (rule.adquirenteId != null ? 2 : 0) + (rule.bandeira != null ? 1 : 0);
}
