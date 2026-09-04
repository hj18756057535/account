package com.hypers.account.web.management;

import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.ApiMessages;
import com.hypers.account.web.AuthController;
import com.hypers.account.web.management.UserImportModels.Preview;
import com.hypers.account.web.management.UserImportModels.Reference;
import com.hypers.account.web.management.UserImportModels.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user-imports")
@RequiredArgsConstructor
public class UserImportController {
    private final UserImportFeature feature;
    private final ExcelUserImportCodec codec;
    private final UserImportService service;
    private final IdempotencyService idempotency;
    private final ApiMessages messages;
    private final com.hypers.account.app.AccountDirectoryService directory;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(HttpSession session) {
        checkEnabled();
        operator(session);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"users-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(codec.template());
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Preview> preview(@RequestParam("file") MultipartFile file,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            HttpSession session, HttpServletRequest request) throws IOException {
        checkEnabled();
        if (file.getSize() > ExcelUserImportCodec.MAX_FILE_BYTES) throw ExcelUserImportCodec.tooLarge();
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(ExcelUserImportCodec.MAX_FILE_BYTES + 1);
        }
        var rows = codec.read(bytes, file.getOriginalFilename());
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        String operator = operator(session);
        Reference ref = idempotency.execute(operator, "POST", "/api/user-imports/preview", key, hash,
                201, Reference.class, () -> new Reference(service.create(rows, hash, operator)));
        return ResponseEntity.status(HttpStatus.CREATED).body(localize(service.preview(ref.getImportId(), operator), request));
    }

    @GetMapping("/{importId}")
    public Preview get(@PathVariable String importId, HttpSession session, HttpServletRequest request) {
        checkEnabled();
        return localize(service.preview(importId, operator(session)), request);
    }

    @PostMapping("/{importId}/commit")
    public Result commit(@PathVariable String importId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key, HttpSession session) {
        checkEnabled();
        String operator = operator(session);
        if (importId.length() > 64) {
            throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "import.notFound");
        }
        return idempotency.execute(operator, "POST", "/api/user-imports/" + importId + "/commit",
                key, importId, 200, Result.class, () -> service.commit(importId, operator));
    }

    private Preview localize(Preview preview, HttpServletRequest request) {
        for (var row : preview.getRows()) {
            for (var error : row.getErrors()) error.setMessage(messages.text(request, error.getCode()));
        }
        return preview;
    }

    private String operator(HttpSession session) {
        String id = ((AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY)).getUserId();
        try {
            if ("enabled".equals(directory.getUser(id).getStatus())) return id;
        } catch (IllegalArgumentException ignored) {
            // 删除或禁用的用户不能复用旧会话导入。
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "error.accessDenied");
    }

    private void checkEnabled() {
        if (!feature.enabled()) throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_DISABLED", "import.disabled");
    }
}
