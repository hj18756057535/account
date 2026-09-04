package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.web.management.CsrfTokenManager;
import com.hypers.account.web.management.ExcelUserImportCodec;
import com.hypers.account.web.management.UserImportFeature;
import com.hypers.account.web.management.UserImportService;
import jakarta.servlet.MultipartConfigElement;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_import_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class UserImportIntegrationTest {
    private final MockMvc mvc;
    private final ObjectMapper json;
    private final AccountDirectoryService directory;
    private final AdminRoleMapper roles;
    private final JdbcTemplate jdbc;
    private final ExcelUserImportCodec codec;
    private final UserImportService imports;
    private final PlatformTransactionManager transactions;
    private final MultipartConfigElement multipartConfig;
    private MockHttpSession session;
    private String csrf;
    private String operator;

    @BeforeEach
    void admin() throws Exception {
        String account = "operator-" + UUID.randomUUID();
        var user = directory.createManagedUser(new SaveUserCommand(account, "operator@example.invalid", "Test", "01"), null);
        operator = user.getId();
        roles.insert(operator, "ACCOUNT_ADMIN");
        session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser(operator, account, "Test"));
        csrf = json.readTree(mvc.perform(get("/api/session").session(session)).andReturn()
                .getResponse().getContentAsString()).get("csrfToken").asText();
    }

    @Test
    void templatePreviewCommitAndRetriesPreserveLeadingZerosAndClearPersonalData() throws Exception {
        mvc.perform(get("/api/user-imports/template").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/session").session(session))
                .andExpect(jsonPath("$.capabilities").value(org.hamcrest.Matchers.hasItem("users:import")));
        String account = "import-" + UUID.randomUUID();
        byte[] file = workbook(account, account + "-second");
        String key = UUID.randomUUID().toString();
        var preview = preview(file, key);
        assertThat(preview.get("valid").asBoolean()).isTrue();
        assertThat(preview.at("/rows/0/phone").asText()).isEqualTo("001234");
        String id = preview.get("importId").asText();
        assertThat(preview(file, key).get("importId").asText()).isEqualTo(id);
        assertThat(jdbc.queryForObject("select response_body from account_idempotency_records where idempotency_key = ?",
                String.class, key)).doesNotContain(account, "001234");
        String body = commit(id, "first").getResponse().getContentAsString();
        assertThat(json.readTree(body).get("createdCount").asInt()).isEqualTo(2);
        assertThat(commit(id, "first").getResponse().getContentAsString()).isEqualTo(body);
        assertThat(commit(id, "second").getResponse().getContentAsString()).isEqualTo(body);
        assertThat(jdbc.queryForObject("select count(*) from account_users where account = ?", Integer.class, account)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select rows_json from account_user_imports where id = ?", String.class, id)).isNull();
        assertThat(jdbc.queryForObject("select password from account_users where account = ?", String.class, account)).isNull();
    }

    @Test
    void duplicatesArePreviewedInEnglishAndCannotCommit() throws Exception {
        byte[] file = workbook("duplicate-" + UUID.randomUUID());
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(file)); var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheetAt(0);
            var row = sheet.createRow(2);
            for (int i = 0; i < 4; i++) row.createCell(i).setCellValue(sheet.getRow(1).getCell(i).getStringCellValue());
            book.write(output);
            var preview = preview(output.toByteArray(), "duplicate");
            assertThat(preview.get("valid").asBoolean()).isFalse();
            assertThat(preview.at("/rows/0/errors/0/message").asText()).contains("duplicated");
            mvc.perform(post("/api/user-imports/" + preview.get("importId").asText() + "/commit")
                    .session(session).header(CsrfTokenManager.HEADER_NAME, csrf).header("Idempotency-Key", "duplicate-commit"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void protectsAuthenticationCsrfOwnershipAndDisabledUsers() throws Exception {
        mvc.perform(get("/api/user-imports/template")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        byte[] file = workbook("protected-" + UUID.randomUUID());
        mvc.perform(multipart("/api/user-imports/preview").file(upload(file)).session(session)
                .header("Idempotency-Key", "no-csrf")).andExpect(status().isForbidden());
        String id = preview(file, "owner").get("importId").asText();
        admin();
        mvc.perform(get("/api/user-imports/" + id).session(session)).andExpect(status().isNotFound());
        jdbc.update("update account_users set status = 'disabled' where id = ?", operator);
        mvc.perform(get("/api/user-imports/template").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void databaseConflictAfterPreviewCreatesNoUsers() throws Exception {
        String account = "race-" + UUID.randomUUID();
        String id = preview(workbook(account), "race").get("importId").asText();
        directory.createManagedUser(new SaveUserCommand(account, "other@example.invalid", "Other", "02"), operator);
        mvc.perform(post("/api/user-imports/" + id + "/commit").session(session)
                .header(CsrfTokenManager.HEADER_NAME, csrf).header("Idempotency-Key", "race"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from account_user_imports where id = ?", String.class, id)).isEqualTo("preview");
    }

    @Test
    void rollbackAndConcurrentConfirmationAreAtomic() throws Exception {
        String account = "atomic-" + UUID.randomUUID();
        String id = preview(workbook(account, account + "-second"), "atomic").get("importId").asText();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            imports.commit(id, operator);
            throw new IllegalStateException("synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from account_users where account = ?", Integer.class, account)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from account_users where account = ?", Integer.class, account + "-second")).isZero();
        assertThat(jdbc.queryForObject("select status from account_user_imports where id = ?", String.class, id)).isEqualTo("preview");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> imports.commit(id, operator));
            var second = executor.submit(() -> imports.commit(id, operator));
            assertThat(first.get().getItems().get(0).getUserId()).isEqualTo(second.get().getItems().get(0).getUserId());
        }
        assertThat(jdbc.queryForObject("select count(*) from account_users where account = ?", Integer.class, account)).isEqualTo(1);
    }

    @Test
    void expirationCleanupAndIdempotencyMismatch() throws Exception {
        String id = preview(workbook("expires-" + UUID.randomUUID()), "expires").get("importId").asText();
        mvc.perform(multipart("/api/user-imports/preview").file(upload(workbook("different")))
                .session(session).header(CsrfTokenManager.HEADER_NAME, csrf).header("Idempotency-Key", "expires"))
                .andExpect(status().isConflict());
        jdbc.update("update account_user_imports set expires_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), id);
        mvc.perform(get("/api/user-imports/" + id).session(session)).andExpect(status().isGone());
        imports.cleanup();
        assertThat(jdbc.queryForObject("select count(*) from account_user_imports where id = ?", Integer.class, id)).isZero();
    }

    @Test
    void rejectsUnsafeWorkbooksAndFlagsNumericCells() throws Exception {
        byte[] file = workbook("unsafe");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(file)); var output = new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(1).getCell(3).setCellValue(1234);
            book.write(output);
            assertThat(preview(output.toByteArray(), "numeric").get("valid").asBoolean()).isFalse();
        }
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(file)); var output = new ByteArrayOutputStream()) {
            book.getSheetAt(0).getRow(1).getCell(3).setCellFormula("1+1");
            book.write(output);
            assertThatThrownBy(() -> codec.read(output.toByteArray(), "users.xlsx")).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(() -> codec.read(new byte[ExcelUserImportCodec.MAX_FILE_BYTES + 1], "users.xlsx"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> codec.read(file, "users.xls")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void featureDefaultsOnAndMigrationHasAllColumnComments() {
        var environment = new MockEnvironment();
        assertThat(new UserImportFeature(environment).enabled()).isTrue();
        assertThat(multipartConfig.getMaxFileSize()).isEqualTo(5L * 1024 * 1024);
        assertThat(multipartConfig.getMaxRequestSize()).isEqualTo(6L * 1024 * 1024);
        environment.setProperty("account.user-import.enabled", "false");
        assertThat(new UserImportFeature(environment).enabled()).isFalse();
        environment.setProperty("account.user-import.enabled", "true");
        assertThat(new UserImportFeature(environment).enabled()).isTrue();
        environment.setProperty("account.store.type", "memory");
        assertThat(new UserImportFeature(environment).enabled()).isFalse();
        var comments = jdbc.queryForList("select remarks from information_schema.columns where table_name = 'ACCOUNT_USER_IMPORTS'",
                String.class);
        assertThat(comments).hasSize(12).allMatch(value -> value != null && !value.isBlank());
    }

    @Test
    void defaultImportIsNotAvailableToAuditorsOrOrdinaryUsers() throws Exception {
        for (boolean auditor : new boolean[]{true, false}) {
            String account = "non-admin-" + UUID.randomUUID();
            var user = directory.createManagedUser(
                    new SaveUserCommand(account, "reader@example.invalid", "Reader", "01"), operator);
            if (auditor) roles.insert(user.getId(), "ACCOUNT_AUDITOR");
            var reader = new MockHttpSession();
            reader.setAttribute(AuthController.SESSION_USER_KEY,
                    new AccountSessionUser(user.getId(), account, "Reader"));
            var response = mvc.perform(get("/api/session").session(reader)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.capabilities").value(
                            org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("users:import"))))
                    .andReturn().getResponse();
            String readerCsrf = json.readTree(response.getContentAsString()).get("csrfToken").asText();
            mvc.perform(get("/api/user-imports/template").session(reader)).andExpect(status().isForbidden());
            mvc.perform(multipart("/api/user-imports/preview").file(upload(workbook(account)))
                    .session(reader).header(CsrfTokenManager.HEADER_NAME, readerCsrf)
                    .header("Idempotency-Key", "forbidden-import")).andExpect(status().isForbidden());
        }
    }

    @Test
    void enforcesRowLimitAndRejectsExternalLinksBeforeOpeningWorkbook() throws Exception {
        String[] accounts = java.util.stream.IntStream.range(0, 1000).mapToObj(i -> "row-" + i).toArray(String[]::new);
        assertThat(codec.read(workbook(accounts), "users.xlsx")).hasSize(1000);
        String[] tooMany = java.util.stream.IntStream.range(0, 1001).mapToObj(i -> "row-" + i).toArray(String[]::new);
        byte[] oversized = workbook(tooMany);
        assertThatThrownBy(() -> codec.read(oversized, "users.xlsx"))
                .isInstanceOfSatisfying(com.hypers.account.web.management.ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(workbook("external")));
             var output = new ByteArrayOutputStream()) {
            var link = book.getCreationHelper().createHyperlink(org.apache.poi.common.usermodel.HyperlinkType.URL);
            link.setAddress("https://example.invalid");
            book.getSheetAt(0).getRow(1).getCell(0).setHyperlink(link);
            book.write(output);
            assertThatThrownBy(() -> codec.read(output.toByteArray(), "users.xlsx")).isInstanceOf(RuntimeException.class);
        }
    }

    private JsonNode preview(byte[] file, String key) throws Exception {
        return json.readTree(mvc.perform(multipart("/api/user-imports/preview").file(upload(file)).session(session)
                .header(CsrfTokenManager.HEADER_NAME, csrf).header("Idempotency-Key", key)
                .header("Accept-Language", "en")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.MvcResult commit(String id, String key) throws Exception {
        return mvc.perform(post("/api/user-imports/" + id + "/commit").session(session)
                .header(CsrfTokenManager.HEADER_NAME, csrf).header("Idempotency-Key", key))
                .andExpect(status().isOk()).andReturn();
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private byte[] workbook(String... accounts) throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(codec.template())); var output = new ByteArrayOutputStream()) {
            for (int r = 0; r < accounts.length; r++) {
                var row = book.getSheetAt(0).createRow(r + 1);
                String[] values = {accounts[r], "synthetic@example.invalid", "Synthetic", "001234"};
                for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            }
            book.write(output);
            return output.toByteArray();
        }
    }
}
