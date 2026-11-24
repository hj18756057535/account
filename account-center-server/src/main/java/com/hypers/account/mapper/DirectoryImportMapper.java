package com.hypers.account.mapper;

import com.hypers.account.app.directory.ApplicationUserLink;
import com.hypers.account.app.directory.DirectoryImportRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DirectoryImportMapper {
    void insert(@Param("record") DirectoryImportRecord record);
    DirectoryImportRecord find(@Param("id") String id);
    DirectoryImportRecord latestCommitted(@Param("appCode") String appCode);
    int commit(@Param("record") DirectoryImportRecord record);
    List<ApplicationUserLink> links(@Param("appCode") String appCode);
    List<ApplicationUserLink> confirmedLinks(@Param("appCode") String appCode);
    ApplicationUserLink link(@Param("userId") String userId, @Param("appCode") String appCode);
    void insertLinks(@Param("links") List<ApplicationUserLink> links);
}

