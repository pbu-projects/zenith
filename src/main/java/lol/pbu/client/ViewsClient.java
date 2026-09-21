package lol.pbu.client;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.client.annotation.Client;
import lol.pbu.model.ViewsResponse;
import lol.pbu.z4j.model.TicketsResponse;
import reactor.core.publisher.Mono;

@Client(id = "zendesk")
public interface ViewsClient {

    @Get("/api/v2/views.json")
    Mono<ViewsResponse> listViews();

    @Get("/api/v2/views/{viewId}/tickets.json")
    Mono<TicketsResponse> listTicketsForView(@PathVariable("viewId") Long viewId);
}
