package lol.pbu.auth;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Manages OAuth 2.0 token acquisition and caching for Zendesk.
 * Supports static Bearer tokens or automatic Client Credentials grant.
 */
@Singleton
public class ZendeskTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ZendeskTokenProvider.class);

    private final String zendeskUrl;
    private final String clientId;
    private final String clientSecret;
    private final String staticToken;
    private final String scope;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private String cachedToken;
    private long tokenExpiresAtMs = 0L;

    public ZendeskTokenProvider(
            @Value("${micronaut.http.services.zendesk.url}") String zendeskUrl,
            @Value("${micronaut.http.services.zendesk.oauth.client-id:}") String clientId,
            @Value("${micronaut.http.services.zendesk.oauth.client-secret:}") String clientSecret,
            @Value("${micronaut.http.services.zendesk.oauth.token:}") String staticToken,
            @Value("${micronaut.http.services.zendesk.oauth.scope:read write}") String scope,
            ObjectMapper objectMapper) {
        this.zendeskUrl = zendeskUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.staticToken = staticToken;
        this.scope = scope;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Returns a valid Bearer token, refreshing via client_credentials grant if expired.
     */
    public synchronized Optional<String> getValidToken() {
        if (StringUtils.isNotEmpty(staticToken)) {
            return Optional.of(staticToken);
        }

        if (StringUtils.isEmpty(clientId) || StringUtils.isEmpty(clientSecret)) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        // Refresh token if within 60 seconds of expiration
        if (cachedToken != null && now < (tokenExpiresAtMs - 60_000L)) {
            return Optional.of(cachedToken);
        }

        try {
            refreshClientCredentialsToken();
            return Optional.ofNullable(cachedToken);
        } catch (Exception e) {
            log.error("Failed to acquire Zendesk OAuth token using client_credentials", e);
            return Optional.ofNullable(cachedToken);
        }
    }

    private void refreshClientCredentialsToken() throws IOException, InterruptedException {
        String tokenUrl = zendeskUrl.replaceAll("/+$", "") + "/oauth/tokens";
        log.info("Requesting new OAuth token from Zendesk: {}", tokenUrl);

        String formBody = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            OAuthTokenResponse tokenResponse = objectMapper.readValue(response.body(), OAuthTokenResponse.class);
            this.cachedToken = tokenResponse.accessToken();
            long expiresInSeconds = (tokenResponse.expiresIn() != null) ? tokenResponse.expiresIn() : 1800L;
            this.tokenExpiresAtMs = System.currentTimeMillis() + (expiresInSeconds * 1000L);
            log.info("Successfully acquired Zendesk OAuth token, valid for {} seconds", expiresInSeconds);
        } else {
            throw new IllegalStateException("Zendesk OAuth token request failed with HTTP "
                    + response.statusCode() + ": " + response.body());
        }
    }
}
