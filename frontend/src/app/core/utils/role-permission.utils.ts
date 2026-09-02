import type { PapelEmpresa } from '../models/business.models';

export function papelPermitido(
  papel: PapelEmpresa | null | undefined,
  permitidos: readonly PapelEmpresa[],
): boolean {
  return papel != null && permitidos.includes(papel);
}
