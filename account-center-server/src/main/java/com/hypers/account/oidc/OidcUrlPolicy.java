package com.hypers.account.oidc;

import java.net.InetAddress;
import java.net.URI;

/** URL 校验不查询 DNS；内网 HTTP 只接受明确的私有 IP 字面量。 */
final class OidcUrlPolicy {
    private OidcUrlPolicy() { }

    static void validateIssuer(String value, boolean allowIntranetHttp) {
        validateWebUri(value, allowIntranetHttp);
        if (URI.create(value).getRawQuery() != null) {
            throw new IllegalArgumentException("OIDC issuer cannot contain query parameters");
        }
    }

    static void validateWebUri(String value, boolean allowIntranetHttp) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || value.contains("*") || uri.getPort() > 65535 || uri.getPort() == 0) {
                throw new IllegalArgumentException();
            }
            if ("https".equals(uri.getScheme())) return;
            if ("http".equals(uri.getScheme()) && isHttpHostAllowed(uri.getHost(), allowIntranetHttp)) return;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid OIDC URL");
        }
        throw new IllegalArgumentException("OIDC HTTP requires loopback or an explicitly enabled private IP");
    }

    private static boolean isHttpHostAllowed(String host, boolean allowIntranetHttp) {
        if ("localhost".equals(host)) return true;
        String literal = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        if (literal.contains("%")) return false;
        byte[] address;
        try {
            if (literal.contains(":")) {
                address = InetAddress.getByName(literal).getAddress();
            } else {
                var parts = literal.split("\\.", -1);
                if (parts.length != 4) return false;
                address = new byte[4];
                for (int i = 0; i < parts.length; i++) {
                    if (!parts[i].matches("0|[1-9][0-9]{0,2}")) return false;
                    int value = Integer.parseInt(parts[i]);
                    if (value > 255) return false;
                    address[i] = (byte) value;
                }
            }
        } catch (Exception exception) {
            return false;
        }
        if (address.length == 4) {
            int first = Byte.toUnsignedInt(address[0]);
            int second = Byte.toUnsignedInt(address[1]);
            if (first == 127) return true;
            return allowIntranetHttp && (first == 10 || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168));
        }
        boolean loopback = true;
        for (int i = 0; i < address.length - 1; i++) loopback &= address[i] == 0;
        if (loopback && address[address.length - 1] == 1) return true;
        return allowIntranetHttp && (address[0] & 0xfe) == 0xfc;
    }
}
