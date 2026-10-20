package com.spendsense.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryOverrideRequest {

    @NotBlank(message = "Category is required")
    private String category;
}
