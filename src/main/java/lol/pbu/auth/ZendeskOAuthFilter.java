package lol.pbu.auth;

import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter that attaches Zendesk OAuth 2.0 Bearer token to outgoing HTTP requests.
 */
@ClientFilter("/**")
public class ZendeskOAuthFilter {

    private static final Logger log = LoggerFactory.getLogger(ZendeskOAuthFilter.class);

    private final ZendeskTokenProvider tokenProvider;

    public ZendeskOAuthFilter(ZendeskTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @RequestFilter
    public void doFilter(MutableHttpRequest<?> request) {
        tokenProvider.getValidToken().ifPresent(token -> {
            log.debug("Attaching OAuth Bearer token to Zendesk request: {}", request.getUri());
            request.bearerAuth(token);
        });
        request.header("Content-Type", "application/json");
        request.header("Accept", "application/json");
    }
}
