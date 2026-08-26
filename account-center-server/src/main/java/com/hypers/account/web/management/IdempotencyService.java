package com.hypers.account.web.management;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.IdempotencyRecord;
import com.hypers.account.mapper.IdempotencyRecordMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final IdempotencyRecordMapper recordMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    public <T> T execute(String operatorId,
                         String requestMethod,
                         String requestPath,
                         String idempotencyKey,
                         Object request,
                         int responseStatus,
                         Class<T> responseType,
                         Supplier<T> action) {
        validateKey(idempotencyKey);
        String requestHash = fingerprint(request);
        try {
            return new TransactionTemplate(transactionManager).execute(transactionStatus ->
                    executeInTransaction(
                            operatorId,
                            requestMethod,
                            requestPath,
                            idempotencyKey,
                            requestHash,
                            responseStatus,
                            responseType,
                            action));
        } catch (DuplicateKeyException exception) {
            IdempotencyRecord concurrent = recordMapper.selectActive(
                    operatorId, requestMethod, requestPath, idempotencyKey, clock.instant());
            if (concurrent != null) {
                return replay(concurrent, requestHash, responseType);
            }
            throw new ApiException(HttpStatus.CONFLICT,
                    "IDEMPOTENCY_IN_PROGRESS", "相同请求正在处理中，请稍后重试");
        }
    }

    private <T> T executeInTransaction(String operatorId,
                                       String requestMethod,
                                       String requestPath,
                                       String idempotencyKey,
                                       String requestHash,
                                       int responseStatus,
                                       Class<T> responseType,
                                       Supplier<T> action) {
        Instant now = clock.instant();
        IdempotencyRecord existing = recordMapper.selectActive(
                operatorId, requestMethod, requestPath, idempotencyKey, now);
        if (existing != null) {
            return replay(existing, requestHash, responseType);
        }

        recordMapper.deleteExpiredScope(operatorId, requestMethod, requestPath, idempotencyKey, now);
        IdempotencyRecord record = new IdempotencyRecord(
                UUID.randomUUID().toString().replace("-", ""),
                idempotencyKey,
                operatorId,
                requestMethod,
                requestPath,
                requestHash,
                "processing",
                null,
                null,
                now,
                now.plus(PROCESSING_TTL));
        recordMapper.insert(record);

        try {
            T response = action.get();
            String responseBody = objectMapper.writeValueAsString(response);
            int updated = recordMapper.complete(
                    record.getId(), responseStatus, responseBody, clock.instant().plus(COMPLETED_TTL));
            if (updated != 1) {
                throw new IllegalStateException("cannot complete idempotency record");
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot persist idempotent response", exception);
        }
    }

    private <T> T replay(IdempotencyRecord existing, String requestHash, Class<T> responseType) {
        if (!MessageDigest.isEqual(
                existing.getRequestHash().getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED", "幂等键已用于不同的请求内容");
        }
        if (!"completed".equals(existing.getStatus()) || existing.getResponseBody() == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "IDEMPOTENCY_IN_PROGRESS", "相同请求正在处理中，请稍后重试");
        }
        try {
            return objectMapper.readValue(existing.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot replay idempotent response", exception);
        }
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 128) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_FAILED", "请求字段校验失败",
                    java.util.Collections.singletonMap("Idempotency-Key", "幂等键长度必须在 1 到 128 个字符之间"));
        }
    }

    private String fingerprint(Object request) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("cannot fingerprint idempotent request", exception);
        }
    }
}
