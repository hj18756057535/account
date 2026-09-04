package com.hypers.account.web.management;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountAlreadyExistsException;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.app.UserImportBatch;
import com.hypers.account.app.UserImportStore;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.web.management.UserImportModels.CreatedRow;
import com.hypers.account.web.management.UserImportModels.Preview;
import com.hypers.account.web.management.UserImportModels.Result;
import com.hypers.account.web.management.UserImportModels.Row;
import com.hypers.account.web.management.UserImportModels.RowError;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserImportService {
    private final UserImportStore store;
    private final AccountDirectoryService directory;
    private final AuditLogService audit;
    private final ObjectMapper json;
    private final Validator validator;
    private final Clock clock;

    @Transactional
    public String create(List<Row> rows, String fileHash, String operatorId) {
        validateRows(rows);
        var batch = new UserImportBatch();
        batch.setId(UUID.randomUUID().toString());
        batch.setFileHash(fileHash);
        batch.setRowCount(rows.size());
        batch.setValid(rows.stream().allMatch(row -> row.getErrors().isEmpty()));
        String rowsJson = write(rows);
        if (rowsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4 * 1024 * 1024) {
            throw ExcelUserImportCodec.tooLarge();
        }
        batch.setRowsJson(rowsJson);
        batch.setStatus("preview");
        batch.setCreatedBy(operatorId);
        batch.setCreatedAt(clock.instant());
        batch.setExpiresAt(clock.instant().plus(Duration.ofMinutes(15)));
        store.insert(batch);
        return batch.getId();
    }

    public Preview preview(String id, String operatorId) {
        var batch = require(id, operatorId, false);
        List<Row> rows = batch.getRowsJson() == null ? List.of() : readRows(batch.getRowsJson());
        return new Preview(id, batch.getExpiresAt(), batch.getRowCount(), batch.isValid(), rows);
    }

    @Transactional
    public Result commit(String id, String operatorId) {
        var batch = require(id, operatorId, true);
        if ("committed".equals(batch.getStatus())) return readResult(batch.getResultJson());
        if (!batch.isValid()) throw conflict();
        var rows = readRows(batch.getRowsJson());
        validateRows(rows);
        if (rows.stream().anyMatch(row -> !row.getErrors().isEmpty())) throw conflict();
        List<SaveUserCommand> commands = rows.stream().map(row ->
                new SaveUserCommand(row.getAccount(), row.getEmail(), row.getName(), row.getPhone())).toList();
        try {
            var users = directory.createManagedUsers(commands, operatorId);
            List<CreatedRow> items = new ArrayList<>();
            for (int i = 0; i < users.size(); i++) items.add(new CreatedRow(rows.get(i).getRowNumber(), users.get(i).getId()));
            var result = new Result(id, items.size(), items);
            batch.setResultJson(write(result));
            batch.setExpiresAt(clock.instant().plus(Duration.ofHours(24)));
            batch.setUpdatedBy(operatorId);
            batch.setUpdatedAt(clock.instant());
            store.complete(batch);
            audit.log(operatorId, "USER_IMPORT_COMMITTED", "USER_IMPORT", id,
                    "{\"createdCount\":" + items.size() + "}");
            return result;
        } catch (AccountAlreadyExistsException exception) {
            throw conflict();
        }
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanup() {
        store.deleteExpired(clock.instant());
    }

    private void validateRows(List<Row> rows) {
        var counts = new HashMap<String, Integer>();
        for (var row : rows) counts.merge(row.getAccount(), 1, Integer::sum);
        var existing = new HashSet<>(directory.findExistingAccounts(new ArrayList<>(counts.keySet())));
        for (var row : rows) {
            var request = new ManagementUserController.CreateUserRequest();
            request.setAccount(row.getAccount());
            request.setEmail(row.getEmail());
            request.setName(row.getName());
            request.setPhone(row.getPhone());
            validator.validate(request).stream()
                    .sorted(java.util.Comparator.comparing(v -> v.getPropertyPath().toString() + v.getMessage()))
                    .forEach(v -> row.getErrors().add(new RowError(v.getPropertyPath().toString(), v.getMessage(), null)));
            if (counts.get(row.getAccount()) > 1 || existing.contains(row.getAccount())) {
                row.getErrors().add(new RowError("account", "import.duplicate", null));
            }
        }
    }

    private UserImportBatch require(String id, String operatorId, boolean lock) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw notFound();
        }
        var batch = store.find(id, operatorId, lock);
        if (batch == null) throw notFound();
        if (!batch.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.GONE, "IMPORT_EXPIRED", "import.expired");
        }
        return batch;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "import.notFound");
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "IMPORT_CONFLICT", "import.conflict");
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("cannot serialize import", exception); }
    }

    private List<Row> readRows(String value) {
        try { return json.readValue(value, new TypeReference<List<Row>>() { }); }
        catch (Exception exception) { throw new IllegalStateException("cannot deserialize import", exception); }
    }

    private Result readResult(String value) {
        try { return json.readValue(value, Result.class); }
        catch (Exception exception) { throw new IllegalStateException("cannot deserialize import result", exception); }
    }
}
