package com.hypers.account.app.directory;

import com.hypers.account.mapper.DirectoryImportMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisDirectoryImportStore implements DirectoryImportStore {
    private final DirectoryImportMapper mapper;

    @Override
    public void insert(DirectoryImportRecord record) {
        mapper.insert(record);
    }

    @Override
    public DirectoryImportRecord find(String id) {
        return mapper.find(id);
    }

    @Override
    public DirectoryImportRecord latestCommitted(String appCode) {
        return mapper.latestCommitted(appCode);
    }

    @Override
    public int commit(DirectoryImportRecord record) {
        return mapper.commit(record);
    }

    @Override
    public List<ApplicationUserLink> links(String appCode) {
        return mapper.links(appCode);
    }

    @Override
    public List<ApplicationUserLink> confirmedLinks(String appCode) {
        return mapper.confirmedLinks(appCode);
    }

    @Override
    public ApplicationUserLink link(String userId, String appCode) {
        return mapper.link(userId, appCode);
    }

    @Override
    public void insertLinks(List<ApplicationUserLink> links) {
        if (!links.isEmpty()) {
            mapper.insertLinks(links);
        }
    }
}

