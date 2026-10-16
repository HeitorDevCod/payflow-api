import { Component, input } from '@angular/core';
import { Tone } from '../core/labels';

@Component({
  selector: 'pf-status',
  template: `<span class="status" [attr.data-tone]="tone()">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly label = input.required<string>();
  readonly tone = input<Tone>('muted');
}
