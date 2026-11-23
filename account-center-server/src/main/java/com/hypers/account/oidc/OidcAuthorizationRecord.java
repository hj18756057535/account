package com.hypers.account.oidc;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 内部载荷含令牌，禁止生成 toString 或暴露到管理 API。 */
@Getter
@Setter
public class OidcAuthorizationRecord {
    private String id;
    private String clientId;
    private String principalName;
    private String payload;
    private String stateHash;
    private String codeHash;
    private String accessTokenHash;
    private String idTokenHash;
    private boolean codeRedeemed;
    private Instant createdAt;
    private Instant updatedAt;
}
