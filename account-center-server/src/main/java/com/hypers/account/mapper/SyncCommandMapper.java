package com.hypers.account.mapper;

import com.hypers.account.app.model.ApplicationSyncCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SyncCommandMapper {

    void insert(@Param("command") ApplicationSyncCommand command);
}
