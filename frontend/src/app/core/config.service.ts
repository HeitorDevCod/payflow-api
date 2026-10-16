import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, shareReplay } from 'rxjs';
import { ApiService } from './api.service';
import { PublicConfig } from './models';

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly api = inject(ApiService);

  readonly config$: Observable<PublicConfig> = this.api.config().pipe(
    catchError(() => of<PublicConfig>({ mercadoPagoPublicKey: null, demo: false })),
    shareReplay(1),
  );
}
