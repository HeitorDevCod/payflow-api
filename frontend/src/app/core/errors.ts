import { HttpErrorResponse } from '@angular/common/http';
import { Problem } from './models';

const FALLBACK = 'Não foi possível concluir a operação. Tente novamente em instantes.';

export function problemOf(error: unknown): Problem {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { detail: 'Sem conexão com o servidor. Verifique se a API está no ar.' };
    }
    const body = error.error as Problem | null;
    if (body && typeof body === 'object') {
      return { ...body, status: error.status };
    }
    return { status: error.status, detail: FALLBACK };
  }
  return { detail: FALLBACK };
}

export function messageOf(error: unknown): string {
  const problem = problemOf(error);
  const firstField = problem.errors ? Object.values(problem.errors)[0] : undefined;
  if (problem.code === 'validation_error' && firstField) {
    return firstField.charAt(0).toUpperCase() + firstField.slice(1);
  }
  return problem.detail ?? FALLBACK;
}
