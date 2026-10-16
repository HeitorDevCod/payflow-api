import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { delay, dematerialize, materialize, of, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { DemoBackend } from './demo-backend';

const backend = new DemoBackend();

/** No modo demonstração, responde às chamadas da API dentro do próprio navegador. */
export const demoInterceptor: HttpInterceptorFn = (req, next) => {
  if (!environment.demo || !req.url.startsWith(environment.apiUrl)) {
    return next(req);
  }
  const path = req.url.slice(environment.apiUrl.length).split('?')[0];
  const result = backend.handle({ method: req.method, path, body: req.body, headers: req.headers });
  const latency = 250 + Math.random() * 350;

  if (result.status >= 400) {
    const error = new HttpErrorResponse({ status: result.status, error: result.body, url: req.url });
    return throwError(() => error).pipe(materialize(), delay(latency), dematerialize());
  }
  return of(new HttpResponse({ status: result.status, body: result.body, url: req.url })).pipe(delay(latency));
};
