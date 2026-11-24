package com.hypers.account.app.directory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountAlreadyExistsException;
import com.hypers.account.app.AccountStore;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.contract.directory.AccountDirectoryUser;
import com.hypers.account.web.management.ApiException;
import com.hypers.account.web.management.UserImportFeature;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 先只读拉取并预览，确认时批量建档及保存来源关联；不覆盖已有全局用户。 */
@Service
@RequiredArgsConstructor
public class DirectoryImportService {
    private final AccountStore accounts;
    private final DirectoryImportStore imports;
    private final ApplicationDirectoryClient client;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditLogService audit;
    private final UserImportFeature feature;

    public DirectoryImportPreview preview(String appCode, String operatorId) {
        requireEnabled();
        AccountApplication app = application(appCode);
        ApplicationDirectorySnapshot snapshot = client.fetch(app);
        validate(snapshot);
        if (!app.getVersion().equals(application(appCode).getVersion())) {
            throw changed();
        }
        DirectoryImportRecord record = new DirectoryImportRecord();
        record.setId(UUID.randomUUID().toString().replace("-", ""));
        record.setAppCode(appCode);
        record.setOperatorId(operatorId);
        record.setApplicationVersion(app.getVersion());
        record.setStatus("preview");
        record.setSnapshotJson(serialize(snapshot));
        record.setCreatedAt(clock.instant());
        record.setExpiresAt(clock.instant().plus(Duration.ofMinutes(15)));
        DirectoryImportPreview preview = response(record, snapshot, app);
        imports.insert(record);
        return preview;
    }

    public DirectoryImportPreview preview(String appCode, String id, String operatorId) {
        DirectoryImportRecord record = record(appCode, id, operatorId);
        return response(record, parse(record.getSnapshotJson(), ApplicationDirectorySnapshot.class), application(appCode));
    }

    public ApplicationDirectorySnapshot latest(String appCode) {
        application(appCode);
        DirectoryImportRecord record = imports.latestCommitted(appCode);
        if (record != null) {
            return parse(record.getSnapshotJson(), ApplicationDirectorySnapshot.class);
        }
        ApplicationDirectorySnapshot empty = new ApplicationDirectorySnapshot();
        empty.setAppCode(appCode);
        return empty;
    }

    @Transactional
    public DirectoryImportResult commit(String appCode, String id, String operatorId) {
        requireEnabled();
        DirectoryImportRecord record = record(appCode, id, operatorId);
        if ("committed".equals(record.getStatus())) {
            return parse(record.getResultJson(), DirectoryImportResult.class);
        }
        AccountApplication app = application(appCode);
        if (app.getVersion() != record.getApplicationVersion() || !"enabled".equals(app.getStatus())
                || !"active".equals(app.getSecretState())) {
            throw changed();
        }
        ApplicationDirectorySnapshot snapshot = parse(record.getSnapshotJson(), ApplicationDirectorySnapshot.class);
        Map<String, ApplicationUserLink> known = known(app);
        Map<String, AccountUser> matches = matches(snapshot, known);
        List<SaveUserCommand> commands = new ArrayList<>();
        Set<String> disabled = new HashSet<>();
        for (AccountDirectoryUser source : snapshot.getUsers()) {
            if (!matches.containsKey(source.getLocalUserId())) {
                commands.add(new SaveUserCommand(source.getAccount(), text(source.getEmail()),
                        source.getName() == null || source.getName().isBlank() ? source.getAccount() : source.getName(),
                        text(source.getPhone())));
                if ("disabled".equals(source.getStatus())) {
                    disabled.add(source.getAccount());
                }
            }
        }
        try {
            Map<String, AccountUser> created = accounts.saveNewUsers(commands, disabled, operatorId).stream()
                    .collect(Collectors.toMap(AccountUser::getAccount, Function.identity()));
            List<ApplicationUserLink> links = new ArrayList<>();
            Set<String> existingLinkIds = imports.links(appCode).stream().map(ApplicationUserLink::getLocalUserId)
                    .collect(Collectors.toSet());
            for (AccountDirectoryUser source : snapshot.getUsers()) {
                AccountUser user = matches.getOrDefault(source.getLocalUserId(), created.get(source.getAccount()));
                if (!existingLinkIds.contains(source.getLocalUserId())) {
                    ApplicationUserLink link = new ApplicationUserLink();
                    link.setAppCode(appCode);
                    link.setUserId(user.getId());
                    link.setLocalUserId(source.getLocalUserId());
                    link.setTenantCode(scope(app));
                    link.setCreatedBy(operatorId);
                    link.setCreatedAt(clock.instant());
                    links.add(link);
                }
            }
            imports.insertLinks(links);
            DirectoryImportResult result = new DirectoryImportResult();
            result.setImportId(id);
            result.setAppCode(appCode);
            result.setCreatedUsers(created.size());
            result.setReusedUsers(snapshot.getUsers().size() - created.size());
            result.setLinkedUsers(snapshot.getUsers().size());
            result.setRoles(snapshot.getRoles().size());
            result.setOrganizations(snapshot.getOrganizations().size());
            record.setResultJson(serialize(result));
            record.setCommittedAt(clock.instant());
            if (imports.commit(record) != 1) {
                throw changed();
            }
            audit.log(operatorId, "APPLICATION_DIRECTORY_IMPORTED", "APPLICATION", appCode,
                    "{\"created\":" + created.size() + ",\"reused\":" + result.getReusedUsers()
                            + ",\"roles\":" + result.getRoles() + ",\"organizations\":" + result.getOrganizations() + "}");
            return result;
        } catch (AccountAlreadyExistsException | DataIntegrityViolationException error) {
            throw identityConflict();
        }
    }

    private DirectoryImportPreview response(DirectoryImportRecord record, ApplicationDirectorySnapshot snapshot, AccountApplication app) {
        Map<String, AccountUser> matched = matches(snapshot, known(app));
        DirectoryImportPreview response = new DirectoryImportPreview();
        response.setImportId(record.getId());
        response.setStatus(record.getStatus());
        response.setExpiresAt(record.getExpiresAt());
        response.setSnapshot(snapshot);
        response.setExistingUsers(matched.size());
        response.setNewUsers(snapshot.getUsers().size() - matched.size());
        return response;
    }

    private Map<String, ApplicationUserLink> known(AccountApplication app) {
        Map<String, ApplicationUserLink> known = new LinkedHashMap<>();
        List<ApplicationUserLink> links = new ArrayList<>(imports.confirmedLinks(app.getAppCode()));
        links.addAll(imports.links(app.getAppCode()));
        Map<String, String> users = new HashMap<>();
        for (ApplicationUserLink link : links) {
            if (!scope(app).equals(link.getTenantCode())) {
                throw identityConflict();
            }
            ApplicationUserLink existing = known.putIfAbsent(link.getLocalUserId(), link);
            String previous = users.putIfAbsent(link.getUserId(), link.getLocalUserId());
            if (existing != null && !existing.getUserId().equals(link.getUserId())
                    || previous != null && !previous.equals(link.getLocalUserId())) {
                throw identityConflict();
            }
        }
        return known;
    }

    private Map<String, AccountUser> matches(ApplicationDirectorySnapshot snapshot, Map<String, ApplicationUserLink> known) {
        List<String> names = snapshot.getUsers().stream().map(AccountDirectoryUser::getAccount).toList();
        List<String> ids = known.values().stream().map(ApplicationUserLink::getUserId).distinct().toList();
        List<AccountUser> existing = accounts.findUsersByAccountsOrIds(names, ids);
        Map<String, AccountUser> byName = existing.stream().collect(Collectors.toMap(AccountUser::getAccount, Function.identity()));
        Map<String, AccountUser> byId = existing.stream().collect(Collectors.toMap(AccountUser::getId, Function.identity()));
        Map<String, String> sourceByUser = known.values().stream().collect(Collectors.toMap(ApplicationUserLink::getUserId,
                ApplicationUserLink::getLocalUserId, (left, right) -> left));
        Map<String, AccountUser> result = new LinkedHashMap<>();
        for (AccountDirectoryUser source : snapshot.getUsers()) {
            ApplicationUserLink link = known.get(source.getLocalUserId());
            AccountUser nameMatch = byName.get(source.getAccount());
            AccountUser user = link == null ? nameMatch : byId.get(link.getUserId());
            if (link != null && (user == null || nameMatch != null && !nameMatch.getId().equals(user.getId()))) {
                throw identityConflict();
            }
            if (user != null) {
                String previous = sourceByUser.putIfAbsent(user.getId(), source.getLocalUserId());
                if (previous != null && !previous.equals(source.getLocalUserId())) {
                    throw identityConflict();
                }
                result.put(source.getLocalUserId(), user);
            }
        }
        return result;
    }

    private void validate(ApplicationDirectorySnapshot snapshot) {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (AccountDirectoryUser user : snapshot.getUsers()) {
            String account = user.getAccount();
            if (account == null || account.isBlank() || account.length() > 128 || !account.equals(account.trim())
                    || text(user.getName()).length() > 128 || text(user.getEmail()).length() > 255
                    || text(user.getPhone()).length() > 64 || !ids.add(user.getLocalUserId()) || !names.add(account)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DIRECTORY_RESULT_INVALID", "directory.invalid");
            }
        }
    }

    private DirectoryImportRecord record(String appCode, String id, String operator) {
        DirectoryImportRecord record = imports.find(id);
        if (record == null || !appCode.equals(record.getAppCode()) || !operator.equals(record.getOperatorId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resourceNotFound");
        }
        if (!"committed".equals(record.getStatus()) && !record.getExpiresAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.GONE, "DIRECTORY_PREVIEW_EXPIRED", "directory.expired");
        }
        return record;
    }

    private AccountApplication application(String code) {
        try {
            return accounts.requireApplication(code);
        } catch (IllegalArgumentException error) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resourceNotFound");
        }
    }

    private String scope(AccountApplication app) {
        return app.getDefaultTenantCode() == null || app.getDefaultTenantCode().isBlank() ? "default" : app.getDefaultTenantCode();
    }

    private void requireEnabled() {
        if (!feature.enabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_IMPORT_DISABLED", "import.disabled");
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private ApiException changed() {
        return new ApiException(HttpStatus.CONFLICT, "DIRECTORY_PREVIEW_CHANGED", "directory.changed");
    }

    private ApiException identityConflict() {
        return new ApiException(HttpStatus.CONFLICT, "DIRECTORY_IDENTITY_CONFLICT", "directory.identityConflict");
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot store directory snapshot");
        }
    }

    private <T> T parse(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot read directory snapshot");
        }
    }
}
