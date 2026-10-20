import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

/**
 * Shared state between TransactionsComponent and DashboardComponent.
 * When the user overrides a category, the dashboard re-fetches its summary.
 */
@Injectable({ providedIn: 'root' })
export class TransactionStateService {
  private _categoryChanged$ = new BehaviorSubject<number | null>(null);

  /** Emits the statementId when any category override occurs */
  categoryChanged$ = this._categoryChanged$.asObservable();

  notifyCategoryChanged(statementId: number): void {
    this._categoryChanged$.next(statementId);
  }
}
