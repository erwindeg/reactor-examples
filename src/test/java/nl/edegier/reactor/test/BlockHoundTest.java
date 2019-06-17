package nl.edegier.reactor.test;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BlockHoundTest {

    @Test
    public void testBlockHoundInstalled() throws InterruptedException {

        Mono.delay(Duration.ofMillis(1))
                .doOnNext(it -> {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                })
                .block();
    }

    @Test
    public void testConcurrentHashmap() {

        Flux.range(0, 100).delayElements(Duration.ofMillis(10))
                .doOnNext(i -> {
                    ConcurrentHashMap map = new ConcurrentHashMap<>(10);
                    map.put(i, "test");
                })
                .blockLast();
    }

    @Test
    public void testUUID() {

        Flux.range(0, 100).delayElements(Duration.ofMillis(10))
                .doOnNext(i -> {
                    ThreadLocal<SecureRandom> SECURE_RANDOM = ThreadLocal.withInitial(SecureRandom::new);
                    UUID uuid = new UUID(SECURE_RANDOM.get().nextLong(),SECURE_RANDOM.get().nextLong());
                   // uuid.toString();

                })
                .blockLast();
    }
}
