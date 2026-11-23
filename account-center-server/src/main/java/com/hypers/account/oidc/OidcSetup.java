package com.hypers.account.oidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 离线准备配置，不启动 Spring，不连接数据库，不输出凭据。 */
public final class OidcSetup {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FILES = Set.of("signing.p12", "account-oidc.properties",
            "agent-client.json", "application-registration.json");
    private static final String ALIAS = "account-signing";

    private OidcSetup() { }

    public static void main(String[] args) {
        try {
            if (args.length != 4) throw new IllegalArgumentException("Expected output directory, issuer, Agent URL and client ID");
            prepare(Path.of(args[0]), args[1], args[2], args[3]);
            System.out.println("OIDC configuration prepared. Existing keys and credentials are preserved.");
            System.out.println("Private material directory: " + Path.of(args[0]).toAbsolutePath().normalize());
        } catch (Exception exception) {
            // 只显示受控错误，不打印配置对象、进程输出或异常链中的敏感值。
            System.err.println("OIDC setup failed. Check URLs, dedicated output directory, file permissions and JDK keytool.");
            System.exit(1);
        }
    }

    static void prepare(Path output, String requestedIssuer, String requestedAgent, String requestedClient) throws Exception {
        Path directory = output.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (!directory.toRealPath().equals(directory)) throw new IllegalArgumentException("Use a real dedicated directory");
        try (var children = Files.list(directory)) {
            if (children.anyMatch(path -> !FILES.contains(path.getFileName().toString())
                    || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))) {
                throw new IllegalArgumentException("Use a dedicated OIDC directory");
            }
        }
        secure(directory, true);
        Path serverFile = directory.resolve("account-oidc.properties");
        Path clientFile = directory.resolve("agent-client.json");
        Path keyFile = directory.resolve("signing.p12");
        boolean existing = Files.exists(serverFile) && Files.exists(clientFile) && Files.exists(keyFile);
        if (!existing && (Files.exists(serverFile) || Files.exists(clientFile) || Files.exists(keyFile))) {
            throw new IllegalArgumentException("Incomplete OIDC material; preserve and repair existing files");
        }
        Properties server = new Properties();
        ObjectNode client = JSON.createObjectNode();
        if (existing) {
            try (var reader = Files.newBufferedReader(serverFile, StandardCharsets.UTF_8)) {
                server.load(reader);
            }
            client = (ObjectNode) JSON.readTree(clientFile.toFile());
        }
        String clientId = choose(requestedClient, client.path("client_id").asText(), "agent");
        if (!clientId.matches("[a-z][a-z0-9-]{0,63}")) throw new IllegalArgumentException("Invalid client ID");
        if (existing && !clientId.equals(client.path("client_id").asText())) {
            throw new IllegalArgumentException("A different client requires a separate directory");
        }
        String issuer = origin(choose(requestedIssuer, server.getProperty("account.oidc.issuer"), "http://localhost:8088"));
        String agent = baseUrl(choose(requestedAgent, client.path("agent_base_url").asText(), "http://localhost:9000"));
        // 用户明确选择私有 IP 的 HTTP 地址时，生成该环境的兼容开关；公网 HTTP 始终拒绝。
        OidcUrlPolicy.validateIssuer(issuer, true);
        OidcUrlPolicy.validateWebUri(agent, true);
        String callback = agent + "/auth/oidc/callback";
        String logout = agent + "/logged-out";
        if (callback.length() > 512 || logout.length() > 512) {
            throw new IllegalArgumentException("Agent URLs exceed the application registration limit");
        }
        boolean intranetHttp = needsIntranetHttp(issuer) || needsIntranetHttp(agent);
        String secret = existing ? client.path("client_secret").asText() : randomSecret();
        String storePassword = existing ? server.getProperty("account.oidc.key-store-password") : randomSecret();
        String signingAlias = existing ? server.getProperty("account.oidc.signing-key-alias", ALIAS) : ALIAS;
        if (secret.isBlank() || storePassword == null || storePassword.isBlank()) {
            throw new IllegalArgumentException("Missing credentials");
        }
        if (existing) {
            var keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(keyFile)) {
                keys.load(input, storePassword.toCharArray());
            }
            if (!keys.isKeyEntry(signingAlias)) throw new IllegalArgumentException("Missing signing key");
            if (!new BCryptPasswordEncoder().matches(secret,
                    server.getProperty("account.oidc.clients." + clientId + ".secret-hash", "").replaceFirst("^\\{bcrypt}", ""))) {
                throw new IllegalArgumentException("Client credentials do not match");
            }
        } else {
            createKeyStore(keyFile, storePassword);
        }
        server.setProperty("account.oidc.enabled", "true");
        server.setProperty("account.oidc.issuer", issuer);
        server.setProperty("account.oidc.allow-intranet-http", Boolean.toString(intranetHttp));
        server.setProperty("account.oidc.key-store", keyFile.toUri().toString());
        server.setProperty("account.oidc.key-store-password", storePassword);
        server.setProperty("account.oidc.signing-key-alias", signingAlias);
        server.setProperty("account.oidc.code-ttl", "60s");
        server.setProperty("account.oidc.access-token-ttl", "5m");
        server.setProperty("server.servlet.session.cookie.secure", Boolean.toString(issuer.startsWith("https://")));
        if (issuer.startsWith("http://")) {
            int port = URI.create(issuer).getPort();
            server.setProperty("server.port", Integer.toString(port < 0 ? 80 : port));
        }
        if (!existing) server.setProperty("account.oidc.clients." + clientId + ".secret-hash",
                "{bcrypt}" + new BCryptPasswordEncoder().encode(secret));
        server.setProperty("account.oidc.clients." + clientId + ".post-logout-redirect-uris[0]", logout);
        client.put("issuer", issuer);
        client.put("server_metadata_url", issuer + "/.well-known/openid-configuration");
        client.put("agent_base_url", agent);
        client.put("client_id", clientId);
        client.put("client_secret", secret);
        client.put("redirect_uri", callback);
        client.put("post_logout_redirect_uri", logout);
        client.put("scope", "openid profile");
        client.put("response_type", "code");
        client.put("code_challenge_method", "S256");
        client.put("token_endpoint_auth_method", "client_secret_basic");
        client.put("allow_insecure_http", issuer.startsWith("http://") || agent.startsWith("http://"));
        var registration = JSON.createObjectNode();
        registration.put("appCode", clientId).put("name", "Agent")
                .put("entryUrl", agent).put("ssoCallbackUrl", callback)
                .put("permissionIframeUrl", agent + "/permissions").put("notifyBaseUrl", agent)
                .put("defaultTenantCode", "default");
        registration.putArray("protocolCapabilities").add("sso");
        secureFile(serverFile);
        try (var writer = Files.newBufferedWriter(serverFile, StandardCharsets.UTF_8)) {
            server.store(writer, "Private OIDC configuration. Do not commit or print.");
        }
        writeJson(clientFile, client);
        writeJson(directory.resolve("application-registration.json"), registration);
    }

    private static boolean needsIntranetHttp(String url) {
        try {
            OidcUrlPolicy.validateWebUri(url, false);
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static String choose(String requested, String previous, String fallback) {
        if (requested != null && !requested.isBlank()) return requested;
        return previous == null || previous.isBlank() ? fallback : previous;
    }

    private static String origin(String value) {
        String result = baseUrl(value);
        if (!URI.create(result).getPath().isEmpty()) {
            throw new IllegalArgumentException("Initializer expects issuer scheme, host and optional port");
        }
        return result;
    }

    private static String baseUrl(String value) {
        OidcUrlPolicy.validateWebUri(value, true);
        if (URI.create(value).getRawQuery() != null) throw new IllegalArgumentException("Base URL cannot contain query");
        return value.replaceAll("/+$", "");
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void createKeyStore(Path path, String password) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", windows ? "keytool.exe" : "keytool");
        var builder = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", ALIAS,
                "-keyalg", "RSA", "-keysize", "3072", "-dname", "CN=Account OIDC Signing",
                "-validity", "3650", "-storetype", "PKCS12", "-keystore", path.toString(),
                "-storepass:env", "ACCOUNT_OIDC_SETUP_STORE_PASSWORD")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("ACCOUNT_OIDC_SETUP_STORE_PASSWORD", password);
        var process = builder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Signing key preparation timed out");
        }
        if (process.exitValue() != 0) throw new IOException("Signing key preparation failed");
        secure(path, false);
    }

    private static void writeJson(Path path, ObjectNode value) throws IOException {
        secureFile(path);
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private static void secureFile(Path path) throws IOException {
        if (!Files.exists(path)) Files.createFile(path);
        secure(path, false);
    }

    private static void secure(Path path, boolean directory) throws IOException {
        var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) {
            var owner = path.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            var rule = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (directory) rule.setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT);
            acl.setAcl(List.of(rule.build()));
        } else {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
        }
    }
}
