package nl.edegier.reactor.test;

import nl.edegier.reactor.ReactiveAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.RetryExhaustedException;

import static java.time.Duration.ofMillis;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static reactor.retry.Retry.any;

@ExtendWith(MockitoExtension.class)
public class ExampleTest {

    @Mock
    private ReactiveAPI api;

    @BeforeEach
    public void setup() {
        when(api.monoWithException()).thenReturn(Mono.error(new RuntimeException()));
    }

    @Test
    public void testRetryWithDelay() {
        callAPI()
                .log()
                .retryWhen(errors -> errors
                        .zipWith(Flux.range(1, 4), (n, i) -> i)
                        .flatMap(error -> Mono.delay(ofMillis(10)))).block();
        verify(api, times(5)).monoWithException();
    }

    @Test
    public void testRetryWithBackoff() {
        callAPI()
                .log()
                .retryWhen(errors -> errors
                        .zipWith(Flux.range(1, 4), (n, i) -> i)
                        .flatMap(error -> Mono.delay(ofMillis((int) Math.pow(10, error))))).block();


        verify(api, times(5)).monoWithException();
    }

    @Test
    public void testRetryWithBackoffOperator() {
        Executable apiCallWithRetry = () -> {
            callAPI()
                    .log()
                    .retryBackoff(5, ofMillis(10), ofMillis(1000))
                    .block();
            verify(api, times(5)).monoWithException();
        };

        assertThrows(IllegalStateException.class, apiCallWithRetry);
    }

    @Test
    public void testRetryWithBackoffReactorExtra() {
        Executable apiCallWithRetry = () -> {
            callAPI()
                    .retryWhen(any().exponentialBackoff(ofMillis(10), ofMillis(1000)).retryMax(5))
                    .block();
            verify(api, times(5)).monoWithException();
        };

        assertThrows(RetryExhaustedException.class, apiCallWithRetry);
    }


    private Mono<String> callAPI() {
        return Mono.just("").flatMap(v -> {
            System.out.println("API call ");
            return api.monoWithException();
        });
    }
}
