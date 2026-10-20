import { Component, ElementRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { StatementService } from '../../core/services/statement.service';
import { apiErrorMessage } from '../../core/models/statement.model';

type UploadStep = 'idle' | 'uploading' | 'parsing' | 'categorizing' | 'done' | 'error';

@Component({
  selector: 'app-upload',
  standalone: false,
  templateUrl: './upload.component.html',
})
export class UploadComponent {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  selectedFile: File | null = null;
  bankType = 'REGIONS';
  step: UploadStep = 'idle';
  errorMessage = '';
  isDragOver = false;
  /** Set once parsing succeeds, so the user can still view transactions if categorization fails */
  parsedStatementId: number | null = null;

  readonly bankOptions = [
    { value: 'REGIONS', label: 'Regions Bank' },
  ];

  constructor(
    private statementService: StatementService,
    private router: Router,
  ) {}

  // ── Drag & Drop ──────────────────────────────────────────────────────────

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = true;
  }

  onDragLeave(): void {
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragOver = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) this.setFile(file);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.[0]) this.setFile(input.files[0]);
  }

  openFilePicker(): void {
    this.fileInput.nativeElement.click();
  }

  private setFile(file: File): void {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      this.errorMessage = 'Only PDF files are supported.';
      return;
    }
    this.selectedFile = file;
    this.errorMessage = '';
  }

  // ── Upload flow ──────────────────────────────────────────────────────────

  upload(): void {
    if (!this.selectedFile) return;
    this.step = 'uploading';
    this.errorMessage = '';
    this.parsedStatementId = null;

    this.statementService.uploadStatement(this.selectedFile, this.bankType).subscribe({
      next: (statement) => {
        this.step = 'categorizing';
        this.parsedStatementId = statement.id;
        this.statementService.categorizeStatement(statement.id).subscribe({
          next: () => {
            this.step = 'done';
            setTimeout(() => this.router.navigate(['/transactions', statement.id]), 800);
          },
          error: (err) => this.handleError('Categorization failed.', err),
        });
      },
      error: (err) => this.handleError('Upload or parsing failed.', err),
    });
  }

  private handleError(message: string, err: unknown): void {
    console.error(err);
    this.step = 'error';
    this.errorMessage = apiErrorMessage(err, message);
  }

  viewParsedTransactions(): void {
    if (this.parsedStatementId) this.router.navigate(['/transactions', this.parsedStatementId]);
  }

  reset(): void {
    this.parsedStatementId = null;
    this.selectedFile = null;
    this.step = 'idle';
    this.errorMessage = '';
  }

  get stepLabel(): string {
    switch (this.step) {
      case 'uploading':    return 'Uploading & parsing PDF…';
      case 'categorizing': return 'Categorizing transactions with AI…';
      case 'done':         return 'Done! Redirecting…';
      default:             return '';
    }
  }
}
