package com.spendsense.api.repository;

import com.spendsense.api.entity.CategoryFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryFeedbackRepository extends JpaRepository<CategoryFeedback, Long> {
}
