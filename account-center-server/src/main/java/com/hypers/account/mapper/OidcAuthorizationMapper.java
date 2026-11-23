package com.hypers.account.mapper;

import com.hypers.account.oidc.OidcAuthorizationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OidcAuthorizationMapper {
    OidcAuthorizationRecord findById(@Param("id") String id);
    OidcAuthorizationRecord findByTokenHash(@Param("hash") String hash, @Param("type") String type);
    int insert(OidcAuthorizationRecord record);
    int update(OidcAuthorizationRecord record);
    int delete(@Param("id") String id);
}
