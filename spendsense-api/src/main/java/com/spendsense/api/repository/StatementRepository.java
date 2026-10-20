package com.spendsense.api.repository;

import com.spendsense.api.entity.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    List<Statement> findByUserIdOrderByUploadedAtDesc(Long userId);
}
