package com.hypers.account.app.service;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.model.ApplicationAccess;
import com.hypers.account.app.model.ApplicationSyncCommand;
import com.hypers.account.mapper.AccountUserApplicationMapper;
import com.hypers.account.mapper.SyncCommandMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 用户与应用授权、准入状态的应用服务。
 *
 * <p>授权关系是“是否可访问”的事实源，准入记录描述下游应用的实际下发状态；带 Provider 配置的
 * 应用还要写入同步命令，交给 {@link ApplicationSynchronizationService} 处理。
 * 用户和应用的存在性统一交给各自的服务判断，本服务只负责授权与准入数据。
 */
@Service
@RequiredArgsConstructor
public class UserApplicationAccessService {

    private final UserManagementService userService;
    private final ApplicationManagementService applicationService;
    private final AccountUserApplicationMapper userApplicationMapper;
    private final SyncCommandMapper syncCommandMapper;

    public boolean isAuthorized(String userId, String appCode) {
        return userApplicationMapper.countByUserAndApp(userId, appCode) > 0;
    }

    /** 授权。带 Provider 配置的应用走版本化准入接口，这里直接拒绝以免出现两条写路径。 */
    public void authorize(String userId, String appCode) {
        userService.getUser(userId);
        AccountApplication application = applicationService.getApplication(appCode);
        requireUnmanaged(application);
        if ("disabled".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_DISABLED);
        }
        if (!isAuthorized(userId, appCode)) {
            userApplicationMapper.insert(userId, appCode);
        }
    }

    /** 撤权，规则与 {@link #authorize} 对称。 */
    public void deauthorize(String userId, String appCode) {
        userService.getUser(userId);
        AccountApplication application = applicationService.getApplication(appCode);
        requireUnmanaged(application);
        userApplicationMapper.delete(userId, appCode);
    }

    public List<AccountApplication> getUserAuthorizedApplications(String userId) {
        userService.getUser(userId);
        return userApplicationMapper.selectAuthorizedApplications(userId);
    }

    /** 列出用户对所有应用的准入状态，未配置的应用补一条禁用态，供管理页展示。 */
    public List<ApplicationAccess> getUserApplicationAccess(String userId) {
        userService.getUser(userId);
        Map<String, ApplicationAccess> configured = userApplicationMapper.selectAccessByUser(userId).stream()
                .collect(Collectors.toMap(ApplicationAccess::getAppCode, Function.identity()));
        List<ApplicationAccess> result = new ArrayList<>();
        for (AccountApplication application : applicationService.findApplications(null, null)) {
            result.add(configured.getOrDefault(application.getAppCode(), new ApplicationAccess(
                    userId,
                    application.getAppCode(),
                    "disabled",
                    0,
                    "pending_application_adaptation",
                    null,
                    null)));
        }
        return result;
    }

    public ApplicationAccess getUserApplicationAccess(String userId, String appCode) {
        userService.getUser(userId);
        applicationService.getApplication(appCode);
        return requireAccess(userId, appCode);
    }

    /**
     * 修改用户准入期望状态。
     *
     * @param syncCommand 需要下发时写入的同步命令，由调用方按同步协议构造
     */
    public ApplicationAccess changeUserApplicationAccess(String userId,
                                                         String appCode,
                                                         String desiredStatus,
                                                         long expectedVersion,
                                                         String operatorId,
                                                         ApplicationSyncCommand syncCommand) {
        userService.getUser(userId);
        AccountApplication application = applicationService.getApplication(appCode);
        if ("enabled".equals(desiredStatus) && "disabled".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.APPLICATION_DISABLED);
        }
        ApplicationAccess access = saveAccess(userId, appCode, desiredStatus, expectedVersion, operatorId);
        if (application.hasProviderConfiguration()) {
            syncCommandMapper.insert(syncCommand);
            access.setSyncCommandId(syncCommand.getId());
        } else {
            access.setIntegrationStatus("not_required");
        }
        return access;
    }

    /** 准入记录不存在时返回 404，避免调用方各自判断 null。 */
    private ApplicationAccess requireAccess(String userId, String appCode) {
        ApplicationAccess access = userApplicationMapper.selectAccess(userId, appCode);
        if (access == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return access;
    }

    /** 按显式版本保存准入状态；新增记录必须从版本 0 开始。 */
    private ApplicationAccess saveAccess(String userId,
                                         String appCode,
                                         String desiredStatus,
                                         long expectedVersion,
                                         String operatorId) {
        ApplicationAccess existing = userApplicationMapper.selectAccess(userId, appCode);
        if (existing == null) {
            if (expectedVersion != 0) {
                throw new BusinessException(ErrorCode.ACCESS_CONFLICT);
            }
            try {
                userApplicationMapper.insertAccess(userId, appCode, desiredStatus, operatorId);
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(ErrorCode.ACCESS_CONFLICT);
            }
        } else if (userApplicationMapper.updateAccessVersioned(
                userId, appCode, desiredStatus, expectedVersion, operatorId) == 0) {
            throw new BusinessException(ErrorCode.ACCESS_CONFLICT);
        }
        return requireAccess(userId, appCode);
    }

    private void requireUnmanaged(AccountApplication application) {
        if (application.hasProviderConfiguration()) {
            throw new BusinessException(ErrorCode.VERSIONED_ACCESS_REQUIRED);
        }
    }
}
