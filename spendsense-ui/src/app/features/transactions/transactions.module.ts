import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TransactionsComponent } from './transactions.component';

const routes: Routes = [{ path: ':statementId', component: TransactionsComponent }];

@NgModule({
  declarations: [TransactionsComponent],
  imports: [CommonModule, FormsModule, RouterModule.forChild(routes)],
})
export class TransactionsModule {}
