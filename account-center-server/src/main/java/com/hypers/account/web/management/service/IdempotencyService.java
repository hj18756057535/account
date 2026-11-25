package com.hypers.account.web.management.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.model.IdempotencyRecord;
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

/** 管理端写接口的幂等执行与结果回放。 */
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
                            true,
                            action));
        } catch (DuplicateKeyException exception) {
            IdempotencyRecord concurrent = recordMapper.selectActive(
                    operatorId, requestMethod, requestPath, idempotencyKey, clock.instant());
            if (concurrent != null) {
                return replay(concurrent, requestHash, responseType, true);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
    }

    public <T> T executeNonReplayable(String operatorId,
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
                            false,
                            action));
        } catch (DuplicateKeyException exception) {
            IdempotencyRecord concurrent = recordMapper.selectActive(
                    operatorId, requestMethod, requestPath, idempotencyKey, clock.instant());
            if (concurrent != null) {
                return replay(concurrent, requestHash, responseType, false);
            }
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
    }

    private <T> T executeInTransaction(String operatorId,
                                       String requestMethod,
                                       String requestPath,
                                       String idempotencyKey,
                                       String requestHash,
                                       int responseStatus,
                                       Class<T> responseType,
                                       boolean replayable,
                                       Supplier<T> action) {
        Instant now = clock.instant();
        IdempotencyRecord existing = recordMapper.selectActive(
                operatorId, requestMethod, requestPath, idempotencyKey, now);
        if (existing != null) {
            return replay(existing, requestHash, responseType, replayable);
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
            String responseBody = replayable ? objectMapper.writeValueAsString(response) : "{}";
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

    private <T> T replay(IdempotencyRecord existing,
                         String requestHash,
                         Class<T> responseType,
                         boolean replayable) {
        if (!MessageDigest.isEqual(
                existing.getRequestHash().getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (!"completed".equals(existing.getStatus()) || existing.getResponseBody() == null) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        if (!replayable) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_RESULT_NOT_REPLAYABLE);
        }
        try {
            return objectMapper.readValue(existing.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot replay idempotent response", exception);
        }
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty() || idempotencyKey.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    java.util.Collections.singletonMap("Idempotency-Key", "validation.idempotency"));
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