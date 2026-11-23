package com.hypers.account.oidc;

import com.hypers.account.mapper.OidcAuthorizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/** 原子条件更新保证同一授权码只有一次兑换结果能提交。 */
@RequiredArgsConstructor
public class MyBatisOidcAuthorizationStore implements OidcAuthorizationStore {
    private final OidcAuthorizationMapper mapper;

    @Override
    public OidcAuthorizationRecord findById(String id) {
        return mapper.findById(id);
    }

    @Override
    public OidcAuthorizationRecord findByTokenHash(String hash, String type) {
        return mapper.findByTokenHash(hash, type);
    }

    @Override
    public void save(OidcAuthorizationRecord record) {
        if (mapper.findById(record.getId()) == null) {
            if (record.isCodeRedeemed()) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
            }
            mapper.insert(record);
        } else if (mapper.update(record) != 1) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
        }
    }

    @Override
    public void remove(String id) {
        mapper.delete(id);
    }
}
