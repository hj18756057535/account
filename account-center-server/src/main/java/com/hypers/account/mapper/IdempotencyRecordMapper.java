package com.hypers.account.mapper;

import com.hypers.account.app.IdempotencyRecord;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotencyRecordMapper {

    IdempotencyRecord selectActive(@Param("operatorId") String operatorId,
                                   @Param("requestMethod") String requestMethod,
                                   @Param("requestPath") String requestPath,
                                   @Param("idempotencyKey") String idempotencyKey,
                                   @Param("now") Instant now);

    void insert(@Param("record") IdempotencyRecord record);

    int complete(@Param("id") String id,
                 @Param("responseStatus") int responseStatus,
                 @Param("responseBody") String responseBody,
                 @Param("expiresAt") Instant expiresAt);

    void delete(@Param("id") String id);

    void deleteExpiredScope(@Param("operatorId") String operatorId,
                            @Param("requestMethod") String requestMethod,
                            @Param("requestPath") String requestPath,
                            @Param("idempotencyKey") String idempotencyKey,
                            @Param("now") Instant now);
}
