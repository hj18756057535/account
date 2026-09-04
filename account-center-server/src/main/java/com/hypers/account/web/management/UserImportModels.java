package com.hypers.account.web.management;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

public final class UserImportModels {
    private UserImportModels() { }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Row {
        private int rowNumber;
        private String account;
        private String email;
        private String name;
        private String phone;
        private List<RowError> errors = new ArrayList<>();
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RowError {
        private String field;
        private String code;
        private String message;
    }

    @Value
    public static class Preview {
        String importId;
        Instant expiresAt;
        int rowCount;
        boolean valid;
        List<Row> rows;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Result {
        private String importId;
        private int createdCount;
        private List<CreatedRow> items;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class CreatedRow {
        private int rowNumber;
        private String userId;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Reference {
        private String importId;
    }
}
