package com.spendsense.api.repository;

import com.spendsense.api.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByStatementIdOrderByDateAscIdAsc(Long statementId);

    @Query("SELECT t FROM Transaction t WHERE t.statement.user.id = :userId")
    List<Transaction> findAllByUserId(@Param("userId") Long userId);
}
