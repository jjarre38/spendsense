import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Statement, Transaction, SummaryResponse, MonthlySpend } from '../models/statement.model';

@Injectable({ providedIn: 'root' })
export class StatementService {
  constructor(private http: HttpClient) {}

  uploadStatement(file: File, bankType: string = 'REGIONS'): Observable<Statement> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<Statement>(`/api/statements/upload?bankType=${bankType}`, formData);
  }

  categorizeStatement(statementId: number): Observable<void> {
    return this.http.post<void>(`/api/statements/${statementId}/categorize`, {});
  }

  getStatements(): Observable<Statement[]> {
    return this.http.get<Statement[]>('/api/statements');
  }

  getTransactions(statementId: number): Observable<Transaction[]> {
    return this.http.get<Transaction[]>(`/api/statements/${statementId}/transactions`);
  }

  getSummary(statementId: number): Observable<SummaryResponse> {
    return this.http.get<SummaryResponse>(`/api/statements/${statementId}/summary`);
  }

  getMonthlySpend(): Observable<MonthlySpend[]> {
    return this.http.get<MonthlySpend[]>('/api/statements/monthly-spend');
  }

  overrideCategory(transactionId: number, category: string): Observable<Transaction> {
    return this.http.patch<Transaction>(`/api/transactions/${transactionId}/category`, { category });
  }
}
