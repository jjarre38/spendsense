package com.spendsense.api.dto;

import com.spendsense.api.entity.Statement;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StatementResponse {

    private Long id;
    private String filename;
    private Statement.BankType bankType;
    private Statement.StatementStatus status;
    private LocalDateTime uploadedAt;
    private Long userId;

    public static StatementResponse from(Statement s) {
        return StatementResponse.builder()
                .id(s.getId())
                .filename(s.getFilename())
                .bankType(s.getBankType())
                .status(s.getStatus())
                .uploadedAt(s.getUploadedAt())
                .userId(s.getUser().getId())
                .build();
    }
}
