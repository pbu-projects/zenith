package lol.pbu.auth;

import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter that attaches Zendesk OAuth 2.0 Bearer token to outgoing HTTP requests reactively.
 */
@ClientFilter("/**")
public class ZendeskOAuthFilter {

    private static final Logger log = LoggerFactory.getLogger(ZendeskOAuthFilter.class);

    private final ZendeskTokenProvider tokenProvider;

    public ZendeskOAuthFilter(ZendeskTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @RequestFilter
    public <T> Publisher<MutableHttpRequest<T>> doFilter(MutableHttpRequest<T> request) {
        if (request.getContentType().isEmpty()) {
            request.header("Content-Type", "application/json");
        }
        request.header("Accept", "application/json");

        return tokenProvider.getValidTokenMono()
                .map(token -> {
                    log.debug("Attaching OAuth Bearer token to Zendesk request: {}", request.getUri());
                    return request.bearerAuth(token);
                })
                .defaultIfEmpty(request);
    }
}
