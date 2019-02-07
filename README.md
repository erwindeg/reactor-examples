# Reactive programming use cases
Reactive programming can be used to solve a lot of different use cases. For instance, reactive programming can be really helpful for cases where timing is an issue. An example of such a case is retry logic with delay or backoff
functionality.

Lets have a look at some different implementations using Project Reactor (https://projectreactor.io) for Java. The sourcecode of the examples can be found at (https://github.com/erwindeg/reactor-examples).

## Using Reactor to build retry logic
In the next examples we will be calling the callAPI function as shown below. This function simulates throwing an exception while calling an API.

```java
private Mono<String> callAPI() {
        return Mono.just("").flatMap(v -> {
            System.out.println("API call ");
            return api.monoWithException();
        });
    }
```

In our first attempt we use the reactor operator retryWhen. This operator can be used to resubscribe to the publisher in case of an exception.
As a parameter it takes a function that allows us to conditionaly retry.
The following marble diagram (from project reactor's javadoc) describes this behavior:

![retryWhen](https://raw.githubusercontent.com/reactor/reactor-core/v3.1.1.RELEASE/src/docs/marble/retrywhen1.png)

We combine each error with a entry from the range, using zipWith and than delay the emit using a flatMap:
zipWith(error1,1).flatMap(delay)    // resubscribes after 10ms
zipWith(error2,2).flatMap(delay)    // resubscribes after 10ms
zipWith(error3,3).flatMap(delay)    // resubscribes after 10ms
zipWith(error4,4).flatMap(delay)    // completes

So, after the first try and 4 retries, the producer completes when the api call keeps producing errors.


```java
callAPI()
        .retryWhen(errors -> errors
                .zipWith(Flux.range(1, 4), (n, i) -> i)
                .flatMap(error -> Mono.delay(ofMillis(10))));
```



```java
callAPI()
        .retryWhen(errors -> errors
                .zipWith(Flux.range(1, 4), (n, i) -> i)
                .flatMap(error -> Mono.delay(ofMillis((int) Math.pow(10, error)))));
```

