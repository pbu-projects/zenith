package lol.pbu

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import jakarta.inject.Inject
import lol.pbu.tools.ZendeskTools
import lol.pbu.z4j.client.SearchClient
import lol.pbu.z4j.model.SearchResponse
import lol.pbu.z4j.model.SearchResult
import reactor.core.publisher.Mono

@MicronautTest
class SearchPaginationSpec extends Specification {

    @Inject
    ZendeskTools zendeskTools

    @Inject
    SearchClient searchClient

    @MockBean(SearchClient)
    SearchClient searchClient() {
        Mock(SearchClient)
    }

    void 'search paginates up to maxResults'() {
        given:
        def r1 = new SearchResponse()
        r1.setResults([new SearchResult(), new SearchResult()])
        r1.setNextPage("http://next")

        def r2 = new SearchResponse()
        r2.setResults([new SearchResult()])
        r2.setNextPage(null)

        searchClient.list("test", _, _, _, 1, 100) >> Mono.just(r1)
        searchClient.list("test", _, _, _, 2, 100) >> Mono.just(r2)

        when:
        def res = zendeskTools.search("test", (String) null, 3)

        then:
        res.results.size() == 3
        res.count == 3
    }

    void 'search truncates if page overshoots maxResults'() {
        given:
        def r1 = new SearchResponse()
        r1.setResults([new SearchResult(), new SearchResult(), new SearchResult()])
        r1.setNextPage("http://next")

        searchClient.list("test", _, _, _, 1, 100) >> Mono.just(r1)

        when:
        def res = zendeskTools.search("test", (String) null, 2)

        then:
        res.results.size() == 2
        res.count == 2
    }
}
