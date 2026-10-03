package lol.pbu.ratelimit

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.ClientFilterChain
import lol.pbu.z4j.ratelimit.RateLimitConfiguration
import lol.pbu.z4j.ratelimit.RateLimitTracker
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class ZenithRateLimitFilterSpec extends Specification {

    def "fail-fast mode fails immediately on HTTP 429 without retrying"() {
        given:
        def tracker = new RateLimitTracker()
        def z4jConfig = new RateLimitConfiguration()
        z4jConfig.setAutoWaitEnabled(false)
        def zenithConfig = new ZenithRateLimitConfiguration()
        zenithConfig.setMode("fail-fast")
        zenithConfig.setMaxWindowSeconds(60)

        def filter = new ZenithRateLimitFilter(tracker, z4jConfig, zenithConfig)

        def request = Mock(MutableHttpRequest)
        request.getPath() >> "/api/v2/tickets"
        request.getMethodName() >> "GET"
        request.getUri() >> URI.create("https://example.zendesk.com/api/v2/tickets")

        def response429 = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "45")
        def ex429 = new HttpClientResponseException("Too Many Requests", response429)

        def chain = Mock(ClientFilterChain)
        def attempts = 0
        chain.proceed(request) >> {
            attempts++
            return Flux.error(ex429)
        }

        when:
        def start = System.currentTimeMillis()
        Mono.from(filter.doFilter(request, chain)).block()

        then:
        def err = thrown(ZenithRateLimitException)
        err.reason == ZenithRateLimitException.Reason.FAIL_FAST
        err.status == HttpStatus.TOO_MANY_REQUESTS
        err.waitDuration.get().seconds == 45
        err.maxWindow.get().seconds == 60
        err.nextAllowableCall.isPresent()
        err.message.contains("fail-fast")
        err.message.contains("45 seconds")
        err.message.contains("Next allowable call at:")
        attempts == 1
        (System.currentTimeMillis() - start) < 1000 // Did not wait
    }

    def "retry mode aborts immediately without waiting when Retry-After exceeds 60s window"() {
        given:
        def tracker = new RateLimitTracker()
        def z4jConfig = new RateLimitConfiguration()
        z4jConfig.setAutoWaitEnabled(false)
        def zenithConfig = new ZenithRateLimitConfiguration()
        zenithConfig.setMode("retry")
        zenithConfig.setMaxWindowSeconds(60)

        def filter = new ZenithRateLimitFilter(tracker, z4jConfig, zenithConfig)

        def request = Mock(MutableHttpRequest)
        request.getPath() >> "/api/v2/tickets"
        request.getMethodName() >> "POST"
        request.getUri() >> URI.create("https://example.zendesk.com/api/v2/tickets")

        def response429 = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "90")
        def ex429 = new HttpClientResponseException("Too Many Requests", response429)

        def chain = Mock(ClientFilterChain)
        def attempts = 0
        chain.proceed(request) >> {
            attempts++
            return Flux.error(ex429)
        }

        when:
        def start = System.currentTimeMillis()
        Mono.from(filter.doFilter(request, chain)).block()

        then:
        def err = thrown(ZenithRateLimitException)
        err.reason == ZenithRateLimitException.Reason.WINDOW_EXCEEDED
        err.waitDuration.get().seconds == 90
        err.maxWindow.get().seconds == 60
        err.message.contains("exceeded maximum window of 60 seconds")
        err.message.contains("90 seconds")
        err.message.contains("Next allowable call at:")
        attempts == 1
        (System.currentTimeMillis() - start) < 1000 // Did not sleep 90s
    }

    def "retry mode retries when wait is within window and succeeds"() {
        given:
        def tracker = new RateLimitTracker()
        def z4jConfig = new RateLimitConfiguration()
        z4jConfig.setAutoWaitEnabled(false)
        def zenithConfig = new ZenithRateLimitConfiguration()
        zenithConfig.setMode("retry")
        zenithConfig.setMaxWindowSeconds(60)

        def filter = new ZenithRateLimitFilter(tracker, z4jConfig, zenithConfig)

        def request = Mock(MutableHttpRequest)
        request.getPath() >> "/api/v2/tickets"
        request.getMethodName() >> "GET"
        request.getUri() >> URI.create("https://example.zendesk.com/api/v2/tickets")

        def response429 = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "1")
        def ex429 = new HttpClientResponseException("Too Many Requests", response429)
        def responseOk = HttpResponse.ok("success")

        def chain = Mock(ClientFilterChain)
        def attempts = 0
        chain.proceed(request) >> {
            attempts++
            if (attempts == 1) {
                return Flux.error(ex429)
            } else {
                return Flux.just(responseOk)
            }
        }

        when:
        def start = System.currentTimeMillis()
        def result = Mono.from(filter.doFilter(request, chain)).block()
        def duration = System.currentTimeMillis() - start

        then:
        result.status == HttpStatus.OK
        attempts == 2
        duration >= 900 // Waited ~1s for Retry-After
    }

    def "retry mode aborts when cumulative elapsed time and wait exceed window"() {
        given:
        def tracker = new RateLimitTracker()
        def z4jConfig = new RateLimitConfiguration()
        z4jConfig.setAutoWaitEnabled(false)
        def zenithConfig = new ZenithRateLimitConfiguration()
        zenithConfig.setMode("retry")
        zenithConfig.setMaxWindowSeconds(60)

        def filter = new ZenithRateLimitFilter(tracker, z4jConfig, zenithConfig)

        def request = Mock(MutableHttpRequest)
        request.getPath() >> "/api/v2/incremental/tickets.json"
        request.getMethodName() >> "GET"
        request.getUri() >> URI.create("https://example.zendesk.com/api/v2/incremental/tickets.json")

        def response429 = HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "20")
        def ex429 = new HttpClientResponseException("Too Many Requests", response429)

        def chain = Mock(ClientFilterChain)
        def attempts = 0
        chain.proceed(request) >> {
            attempts++
            return Flux.error(ex429)
        }

        // Budget where 45 seconds have already elapsed for this tool action
        def budget = new ZenithRateLimitBudget(Instant.now().minusSeconds(45), Duration.ofSeconds(60), "retry")

        when:
        def start = System.currentTimeMillis()
        Mono.from(filter.doFilter(request, chain))
                .contextWrite({ ctx -> ctx.put(ZenithRateLimitBudget.KEY, budget) })
                .block()

        then:
        // 45s elapsed + 20s wait = 65s > 60s window -> immediate abort
        def err = thrown(ZenithRateLimitException)
        err.reason == ZenithRateLimitException.Reason.WINDOW_EXCEEDED
        err.waitDuration.get().seconds == 20
        err.maxWindow.get().seconds == 60
        attempts == 1
        (System.currentTimeMillis() - start) < 1000 // Did not wait 20s
    }

    def "tracks endpoint-specific rate limit headers into tracker for low-quota endpoints"() {
        given:
        def tracker = new RateLimitTracker()
        def z4jConfig = new RateLimitConfiguration()
        def zenithConfig = new ZenithRateLimitConfiguration()
        def filter = new ZenithRateLimitFilter(tracker, z4jConfig, zenithConfig)

        def request = Mock(MutableHttpRequest)
        request.getPath() >> "/api/v2/incremental/tickets.json"
        request.getMethodName() >> "GET"
        request.getUri() >> URI.create("https://example.zendesk.com/api/v2/incremental/tickets.json")

        def headers = Mock(HttpHeaders)
        headers.names() >> (["zendesk-ratelimit-incremental-tickets"] as Set)
        headers.get("zendesk-ratelimit-incremental-tickets") >> "total=10; remaining=0; resets=60"
        headers.get("ratelimit-remaining") >> "0"
        headers.get("ratelimit-limit") >> "10"
        headers.get("ratelimit-reset") >> "60"

        def response = Mock(HttpResponse)
        response.getStatus() >> HttpStatus.OK
        response.getHeaders() >> headers

        def chain = Mock(ClientFilterChain)
        chain.proceed(request) >> Flux.just(response)

        when:
        Mono.from(filter.doFilter(request, chain)).block()

        then:
        def snapshot = tracker.getLatestSnapshot()
        snapshot != null
        snapshot.globalRemaining == 0
        snapshot.globalLimit == 10
        snapshot.globalResetSeconds == 60
        snapshot.endpointLimits.containsKey("incremental-tickets")
        snapshot.endpointLimits.get("incremental-tickets").remaining == 0
        snapshot.endpointLimits.get("incremental-tickets").total == 10
        snapshot.endpointLimits.get("incremental-tickets").resets == 60
    }
}
