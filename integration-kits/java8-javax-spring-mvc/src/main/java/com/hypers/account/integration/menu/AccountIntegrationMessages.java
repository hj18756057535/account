package com.hypers.account.integration.menu;

import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

@UtilityClass
class AccountIntegrationMessages {

    private static final Map<String, String> ZH = messages(false);
    private static final Map<String, String> EN = messages(true);

    static String language(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.trim().isEmpty()) {
            try {
                List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
                for (Locale.LanguageRange range : ranges) {
                    if (range.getWeight() == 0) continue;
                    String language = range.getRange().split("-", 2)[0];
                    if ("en".equals(language)) return "en-US";
                    if ("zh".equals(language) || "*".equals(language)) return "zh-CN";
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid language headers never change authentication or validation.
            }
        }
        return "zh-CN";
    }

    static String text(HttpServletRequest request, String code) {
        Map<String, String> catalog = "en-US".equals(language(request)) ? EN : ZH;
        String value = catalog.get(code);
        return value == null ? catalog.get("INTEGRATION_ERROR") : value;
    }

    private static Map<String, String> messages(boolean english) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("AUTHENTICATION_REQUIRED", english
                ? "Integration request authentication failed."
                : "集成请求认证失败");
        values.put("INTEGRATION_AUTH_FAILED", english
                ? "Menu permission integration authentication failed."
                : "菜单权限集成请求认证失败");
        values.put("PROTOCOL_VERSION_UNSUPPORTED", english
                ? "The menu permission protocol version is not supported."
                : "菜单权限协议版本不受支持");
        values.put("VALIDATION_FAILED", english
                ? "The menu permission request does not conform to the contract."
                : "菜单权限请求不符合契约");
        values.put("PERMISSION_CODE_INVALID", english
                ? "The menu permission code is invalid."
                : "菜单权限编码无效");
        values.put("CATALOG_INVALID", english
                ? "The application returned an invalid menu catalog."
                : "应用返回的菜单目录无效");
        values.put("SUBJECT_NOT_FOUND", english
                ? "The local application user does not exist or the identity mapping is stale."
                : "应用本地用户不存在或身份映射失效");
        values.put("SUBJECT_UNMANAGEABLE", english
                ? "Menu permissions for this user cannot be managed by Account."
                : "该用户不能由 Account 配置菜单权限");
        values.put("PERMISSION_REVISION_CONFLICT", english
                ? "Menu permissions changed. Reload and try again."
                : "菜单权限已被其他操作修改，请重新加载");
        values.put("IDEMPOTENCY_CONFLICT", english
                ? "The idempotency key was reused for a different request."
                : "相同幂等键对应了不同请求");
        values.put("PAYLOAD_TOO_LARGE", english
                ? "The menu catalog or permission selection exceeds the protocol limit."
                : "菜单目录或授权集合超过协议限制");
        values.put("PERMISSION_PROVIDER_UNAVAILABLE", english
                ? "The menu permission provider is temporarily unavailable."
                : "菜单权限服务暂不可用");
        values.put("MALFORMED_REQUEST", english
                ? "The request body could not be parsed."
                : "请求体无法解析");
        values.put("INTEGRATION_ERROR", english
                ? "The integration request failed. Please retry later."
                : "集成请求处理失败，请稍后重试");
        return values;
    }
}
