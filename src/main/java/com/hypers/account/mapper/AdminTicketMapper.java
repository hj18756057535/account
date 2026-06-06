package com.hypers.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;

/**
 * 管理端 ticket Mapper。
 * 用于 iframe 授权页面的短期凭证（60 秒有效期，一次性使用）。
 */
@Mapper
public interface AdminTicketMapper {

    /** 新增 ticket */
    void insert(@Param("code") String code, @Param("appCode") String appCode,
                @Param("userId") String userId, @Param("purpose") String purpose,
                @Param("expiresAt") String expiresAt);

    /** 校验 ticket 是否存在且匹配应用编码 */
    String selectAppCodeByCode(@Param("code") String code, @Param("appCode") String appCode);

    /** 查询 ticket 关联的用户 ID */
    String selectUserIdByCode(@Param("code") String code);

    /** 标记 ticket 已使用（一次性凭证） */
    void markUsed(@Param("code") String code);

    /** 检查 ticket 是否已被使用 */
    Integer countUsed(@Param("code") String code);

    /** 检查 ticket 是否有效（存在、未过期、匹配应用编码） */
    Integer countValid(@Param("code") String code, @Param("appCode") String appCode,
                       @Param("now") String now);
}
