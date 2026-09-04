package com.hypers.account.app;

import com.hypers.account.mapper.UserImportMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisUserImportStore implements UserImportStore {
    private final UserImportMapper mapper;

    public void insert(UserImportBatch batch) { mapper.insert(batch); }
    public UserImportBatch find(String id, String operatorId, boolean lock) {
        return mapper.find(id, operatorId, lock);
    }
    public void complete(UserImportBatch batch) {
        if (mapper.complete(batch) != 1) throw new IllegalStateException("import completion conflict");
    }
    public int deleteExpired(Instant now) {
        var ids = mapper.expiredIds(now);
        return ids.isEmpty() ? 0 : mapper.deleteExpired(ids, now);
    }
}
