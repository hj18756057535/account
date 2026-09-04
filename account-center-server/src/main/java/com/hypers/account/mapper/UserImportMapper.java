package com.hypers.account.mapper;

import com.hypers.account.app.UserImportBatch;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserImportMapper {
    void insert(@Param("batch") UserImportBatch batch);
    UserImportBatch find(@Param("id") String id, @Param("operatorId") String operatorId,
                        @Param("lock") boolean lock);
    int complete(@Param("batch") UserImportBatch batch);
    List<String> expiredIds(@Param("now") Instant now);
    int deleteExpired(@Param("ids") List<String> ids, @Param("now") Instant now);
}
