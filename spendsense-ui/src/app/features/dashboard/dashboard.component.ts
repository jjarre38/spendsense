import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ChartData, ChartOptions } from 'chart.js';
import { StatementService } from '../../core/services/statement.service';
import { TransactionStateService } from '../../core/services/transaction-state.service';
import { MonthlySpend, Statement, SummaryResponse } from '../../core/models/statement.model';

/**
 * Fixed color per category — color follows the category, never its rank, so a category keeps
 * its color across statements and overrides. Categorical slots validated for CVD separation and
 * contrast against the dark surface (#111118). Catch-all buckets share a neutral gray.
 */
const CATEGORY_COLORS: Record<string, string> = {
  Groceries:     '#3987e5', // blue
  Dining:        '#d95926', // orange
  Utilities:     '#199e70', // aqua
  Transport:     '#c98500', // yellow
  Entertainment: '#d55181', // magenta
  Health:        '#008300', // green
  Shopping:      '#9085e9', // violet
  Income:        '#e66767', // red
};
const NEUTRAL_COLOR = '#64748b';
const SURFACE = '#111118';
const MONTHLY_BAR_COLOR = '#3987e5';

export function categoryColor(category: string): string {
  return CATEGORY_COLORS[category] ?? NEUTRAL_COLOR;
}

@Component({
  selector: 'app-dashboard',
  standalone: false,
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit, OnDestroy {

  // ── State ──────────────────────────────────────────────────────────────────
  statements: Statement[] = [];
  selectedStatementId: number | null = null;
  summary: SummaryResponse | null = null;
  monthly: MonthlySpend[] = [];
  /** Category rows sorted by spend, with their chart color — doubles as the chart's table view */
  categoryRows: { category: string; amount: number; color: string }[] = [];
  loadingStatements = true;
  loadingSummary = false;
  error = '';

  private stateSub?: Subscription;

  // ── Chart data ─────────────────────────────────────────────────────────────

  donutData: ChartData<'doughnut'> = { labels: [], datasets: [] };
  donutOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right',
        labels: {
          font: { size: 11 },
          color: '#94a3b8',
          boxWidth: 10,
          padding: 14,
        },
      },
      tooltip: {
        backgroundColor: '#111118',
        borderColor: '#1e1e2e',
        borderWidth: 1,
        titleColor: '#f1f5f9',
        bodyColor: '#94a3b8',
        callbacks: {
          label: (ctx) => ` ${ctx.label}: ${this.formatCurrency(Number(ctx.raw))}`,
        },
      },
    },
  };

  barData: ChartData<'bar'> = { labels: [], datasets: [] };
  barOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#111118',
        borderColor: '#1e1e2e',
        borderWidth: 1,
        titleColor: '#f1f5f9',
        bodyColor: '#94a3b8',
        callbacks: {
          label: (ctx) => ` ${this.formatCurrency(Number(ctx.raw))}`,
        },
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        ticks: { callback: (v) => `$${Number(v).toLocaleString('en-US')}`, color: '#64748b', font: { size: 11 } },
        grid: { color: '#1e1e2e' },
        border: { color: '#1e1e2e' },
      },
      x: {
        grid: { display: false },
        ticks: { color: '#64748b', font: { size: 11 } },
        border: { color: '#1e1e2e' },
      },
    },
  };

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private statementService: StatementService,
    private stateService: TransactionStateService,
  ) {}

  ngOnInit(): void {
    // Pre-select statementId if routed from transactions page
    const preselect = this.route.snapshot.queryParamMap.get('statementId');

    this.loadMonthly();

    this.statementService.getStatements().subscribe({
      next: (stmts) => {
        this.statements = stmts.sort(
          (a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime()
        );
        this.loadingStatements = false;

        // Auto-select: preselected id, or most recent statement
        const target = preselect
          ? Number(preselect)
          : this.statements[0]?.id ?? null;

        if (target) {
          this.selectedStatementId = target;
          this.loadSummary(target);
        }
      },
      error: () => {
        this.error = 'Failed to load statements.';
        this.loadingStatements = false;
      },
    });

    // Re-fetch summary when user overrides a category in the transactions table
    this.stateSub = this.stateService.categoryChanged$.subscribe((statementId) => {
      if (statementId && statementId === this.selectedStatementId) {
        this.loadSummary(statementId);
        this.loadMonthly();
      }
    });
  }

  ngOnDestroy(): void {
    this.stateSub?.unsubscribe();
  }

  // ── Data loading ────────────────────────────────────────────────────────────

  onStatementChange(id: number): void {
    this.selectedStatementId = id;
    this.loadSummary(id);
  }

  private loadMonthly(): void {
    this.statementService.getMonthlySpend().subscribe({
      next: (months) => {
        this.monthly = months;
        this.barData = {
          labels: months.map(m => this.formatMonth(m.month)),
          datasets: [{
            label: 'Monthly spend',
            data: months.map(m => Number(m.totalSpend)),
            backgroundColor: MONTHLY_BAR_COLOR,
            hoverBackgroundColor: '#5598e7',
            borderRadius: { topLeft: 4, topRight: 4 },
            borderSkipped: 'bottom',
            maxBarThickness: 48,
          }],
        };
      },
      error: () => { this.monthly = []; },
    });
  }

  private loadSummary(statementId: number): void {
    this.loadingSummary = true;
    this.error = '';

    this.statementService.getSummary(statementId).subscribe({
      next: (summary) => {
        this.summary = summary;
        this.buildCharts(summary);
        this.loadingSummary = false;
      },
      error: () => {
        this.error = 'Failed to load summary data.';
        this.loadingSummary = false;
      },
    });
  }

  // ── Chart builders ──────────────────────────────────────────────────────────

  private buildCharts(summary: SummaryResponse): void {
    this.categoryRows = Object.entries(summary.spendByCategory)
      .map(([category, amount]) => ({ category, amount: Number(amount), color: categoryColor(category) }))
      .sort((a, b) => b.amount - a.amount);

    // Donut — spend breakdown by effective category
    this.donutData = {
      labels: this.categoryRows.map(r => r.category),
      datasets: [{
        data: this.categoryRows.map(r => r.amount),
        backgroundColor: this.categoryRows.map(r => r.color),
        borderWidth: 2,
        borderColor: SURFACE,
        hoverOffset: 6,
      }],
    };
  }

  // ── Helpers ─────────────────────────────────────────────────────────────────

  formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value);
  }

  formatMonth(isoMonth: string): string {
    const [year, month] = isoMonth.split('-').map(Number);
    return new Date(year, month - 1, 1).toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  }

  viewTransactions(): void {
    if (this.selectedStatementId) this.router.navigate(['/transactions', this.selectedStatementId]);
  }

  get selectedStatement(): Statement | undefined {
    return this.statements.find(s => s.id === this.selectedStatementId);
  }
}
