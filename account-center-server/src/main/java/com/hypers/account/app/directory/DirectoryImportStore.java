package com.hypers.account.app.directory;

import java.util.List;

/** 目录预览与稳定身份关联的数据访问边界。 */
public interface DirectoryImportStore {
    void insert(DirectoryImportRecord record);
    DirectoryImportRecord find(String id);
    DirectoryImportRecord latestCommitted(String appCode);
    int commit(DirectoryImportRecord record);
    List<ApplicationUserLink> links(String appCode);
    List<ApplicationUserLink> confirmedLinks(String appCode);
    ApplicationUserLink link(String userId, String appCode);
    void insertLinks(List<ApplicationUserLink> links);
}

