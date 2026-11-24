package com.hypers.account.boot2.oidc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 经 OIDC 验签的身份资料，供宿主建立本地账号关联。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountOidcIdentity {
    /** Account 的固定 issuer。 */
    private String issuer;
    /** Account 用户的稳定全局 ID。 */
    private String subject;
    /** profile 中的 preferred_username。 */
    private String account;
    /** profile 中的 name。 */
    private String displayName;
}
