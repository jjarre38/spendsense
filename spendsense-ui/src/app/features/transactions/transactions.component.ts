import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { StatementService } from '../../core/services/statement.service';
import { TransactionStateService } from '../../core/services/transaction-state.service';
import { Transaction, CATEGORIES } from '../../core/models/statement.model';

const PAGE_SIZE = 25;

@Component({
  selector: 'app-transactions',
  standalone: false,
  templateUrl: './transactions.component.html',
})
export class TransactionsComponent implements OnInit {
  statementId!: number;
  transactions: Transaction[] = [];
  loading = true;
  error = '';

  // Pagination
  page = 1;
  readonly pageSize = PAGE_SIZE;

  // Category options for the dropdown
  readonly categories = CATEGORIES;

  // Optimistic update: track pending saves per transaction id
  savingIds = new Set<number>();

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private statementService: StatementService,
    private stateService: TransactionStateService,
  ) {}

  ngOnInit(): void {
    this.statementId = Number(this.route.snapshot.paramMap.get('statementId'));
    this.loadTransactions();
  }

  loadTransactions(): void {
    this.loading = true;
    this.statementService.getTransactions(this.statementId).subscribe({
      next: (txs) => {
        this.transactions = txs;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load transactions.';
        this.loading = false;
      },
    });
  }

  // ── Pagination ────────────────────────────────────────────────────────────

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.transactions.length / this.pageSize));
  }

  get pagedTransactions(): Transaction[] {
    const start = (this.page - 1) * this.pageSize;
    return this.transactions.slice(start, start + this.pageSize);
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  goToPage(p: number): void {
    if (p < 1 || p > this.totalPages) return;
    this.page = p;
  }

  // ── Category override ─────────────────────────────────────────────────────

  onCategoryChange(tx: Transaction, newCategory: string): void {
    const original = tx.userCategory ?? tx.aiCategory;
    if (newCategory === original) return;

    // Optimistic update
    tx.userCategory = newCategory;
    tx.isOverridden = true;
    tx.effectiveCategory = newCategory;
    this.savingIds.add(tx.id);

    this.statementService.overrideCategory(tx.id, newCategory).subscribe({
      next: (updated) => {
        // Sync with server response
        Object.assign(tx, updated);
        this.savingIds.delete(tx.id);
        // Notify dashboard to refresh
        this.stateService.notifyCategoryChanged(this.statementId);
      },
      error: () => {
        // Revert optimistic update on failure
        tx.userCategory = original === tx.aiCategory ? null : original;
        tx.isOverridden = !!tx.userCategory;
        this.savingIds.delete(tx.id);
      },
    });
  }

  isSaving(id: number): boolean {
    return this.savingIds.has(id);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  formatAmount(tx: Transaction): string {
    const prefix = tx.type === 'DEBIT' ? '-' : '+';
    return `${prefix}$${Number(tx.amount).toFixed(2)}`;
  }

  goToDashboard(): void {
    this.router.navigate(['/dashboard'], { queryParams: { statementId: this.statementId } });
  }
}
