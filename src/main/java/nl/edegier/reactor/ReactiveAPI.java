package nl.edegier.reactor;

import reactor.core.publisher.Mono;

public interface ReactiveAPI {

    Mono<String> monoWithException();
}
