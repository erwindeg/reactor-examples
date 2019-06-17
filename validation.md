#Awesome title about reactive

##Dealing with asynchronous and parallel validation

There are scenarios when the outcome of a code block is depends on several separate expressions and if any of them goes wrong (or right depends on the problem), you can already decide about final result and you don't need to wait for outcome of every single expression.
Thinks of it like a `short circuit` in `if` statements, but in a parallel manner. One of the very common use cases is when you are developing an enterprise application and want to validate an end-user's request based on current business status of system before applying the request to the system.

To validate a request, you might need to do multiple `validations` and if all of them validate the request successfully, then we can continue with serving the request.

However, if any of the `validators` fails to validate, then user's request is invalid, regardless of all other validators result.

We will do all of the validations asynchronously and in parallel as their results are independent of each other. As soon as any of them fails, we don't want to waste time any more and respond back to the user.

Now let's see some code.

##Building blocks

We have an Interface that every validator class is going to implement:
```
public interface Validator {
    CompletableFuture<ValidationResponse> validate();
}
```

Next is our first validator which returns a failed response after 2 seconds:  
```
public class Validator1 implements Validator{

    public CompletableFuture<ValidationResponse> validate()  {
        return CompletableFuture.supplyAsync(this::doStuff);
    }

    public ValidationResponse doStuff()  {
        System.out.println("Validator1 started. Time: " + Instant.now());
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new ValidationResponse(false, ValidationFailureReason.ORDER_ALREADY_EXISTS);
    }
}
```


Our second validator returns a success response after 5 seconds:
```
public class Validator2 implements Validator{

    public CompletableFuture<ValidationResponse> validate()  {
        return CompletableFuture.supplyAsync(this::doStuff);
    }

    public ValidationResponse doStuff()  {
        System.out.println("Validator2 started. time: "+ Instant.now());
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return new ValidationResponse(true);
    }
}
```

The ValidationResponse can be any object, like this:
```
public class ValidationResponse {

    private boolean isSuccessful;
    private ValidationFailureReason reason;
    private Object optionalObject;

    public ValidationResponse(boolean isSuccessful) {
        this.isSuccessful = isSuccessful;
    }

    public ValidationResponse(boolean isSuccessful ,ValidationFailureReason reason) {
        this.isSuccessful = isSuccessful;
        this.reason = reason;
    }
}
```

So what we aim for is to validate the user's request and reject it after 2 secs and do not wait for the other validator.

##First solution: using Java Streams API and CompletableFuture

```
    public void validate(){
        Validator1 validator1 = new Validator1();
        Validator2 validator2 = new Validator2();

        CompletableFuture<ValidationResponse> cfValidation2 = validator2.validate();
        CompletableFuture<ValidationResponse> cfValidation1 = validator1.validate();

        List<CompletableFuture<ValidationResponse>> validationFutures = Arrays.asList(cfValidation1, cfValidation2);

        CompletableFuture<ValidationResponse> validationResponseCompletableFuture;
        Mono<ValidationResponse> validationResponseMono;

        validationFutures.stream().parallel()
                .filter( validationFuture -> checkValidationResponse(validationFuture))
                .map(e -> {
                    System.out.println("after filter. Time: " + Instant.now() );
                    return e;
                 })
                .findFirst()
                .ifPresent(validationFuture -> System.out.println("after findFirst. Time: " + Instant.now() ));
    }
```
and `checkValidationResponse` log the time when each validationResponse hit the method and at the end just does a blocking `join()` to return.
```
    private Boolean checkValidationResponse(CompletableFuture<ValidationResponse> validationResponseCF) {
        return validationResponseCF.thenApply(validationResponse -> {
            if (validationResponse.isFailed()){
                System.out.println("found failed one. Time: " + Instant.now()+ " Thread is " + Thread.currentThread().getName());
                return true;
            }
            System.out.println("found normal one. Time: " + Instant.now() + " Thread is " + Thread.currentThread().getName());
            return false;
        }).join();
    }
```

If we run the code, the terminal looks like this:
```
Validator2 started. time: 2019-06-12T11:49:50.808Z
Validator1 started. Time: 2019-06-12T11:49:50.808Z
found failed one. Time: 2019-06-12T11:49:52.828Z Thread is ForkJoinPool.commonPool-worker-2
after filter. Time: 2019-06-12T11:49:52.829Z
found successful one. Time: 2019-06-12T11:49:55.827Z Thread is ForkJoinPool.commonPool-worker-1
after findFirst. Time: 2019-06-12T11:49:55.828Z
```

This is very interesting as you can see Java 8 Stream API worked as intended until findFirst(). And although it already has the first response, but it findFirst() does not pass it down the stream until all elements get processed. So in our case it unnecessarily waits also for Validator2 to finish it's job and then passes down the response of Validator1.

So Java 8 Stream API did not work for this specific problem. //tell me more

##Second solution: using reactive streams and project reactor

Now we are going to do the same test using `project reactor`. `Project reactor` is an implementation of `reactive streams` from `Pivotal`.

For using it, Add the following dependency to your project:
```
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-core</artifactId>
    <version>3.2.10.RELEASE</version>
</dependency>
```
And the code :
```
    Mono<ValidationResponse> cfValidation2Mono = Mono.fromFuture(cfValidation2);
    Mono<ValidationResponse> cfValidation1Mono = Mono.fromFuture(cfValidation1);

    List<Mono<ValidationResponse>> validationMonos = Arrays.asList(cf2ValidationMono, cf1ValidationMono);

    Flux.fromStream(validationMonos.stream())
            .parallel()
            .runOn(Schedulers.parallel())
            .flatMap(mono -> mono)
            .filter(vr ->  checkValidationResponse(vr))
            .sequential()
            .next()
            .switchIfEmpty(Mono.just(new ValidationResponse(true)))
            .block();

    System.out.println(String.format("Validations Result is ready. time: %s , success: %s",Instant.now(), validationResponse.isSuccessful()));
```

Let's have a closer look at the way we assembled the reactive pipeline

`Schedulers.parallel()` uses a fixed threadpool with the size of number of cpu cores. If you need more threads, you can use `Schedulers.elastic()`.
`flatMap` is needed to flatten `ParallelFlux<Mono<ValidationResponse>>` to `ParallelFlux<ValidationResponse>`.
`sequential()` changes the pipeline from parallel to flux again for downstream processors.
`switchIfEmpty()` subscribes to another publisher if it receives no element. So in our case we if receive no element, it means all validators returned success responds, so we just subscribe to `Mono.just(new ValidationResponse(true))`.


Now time to run this piece of code and check the behaviour:

```
Validator1 started. Time: 2019-06-12T12:52:09.623Z
Validator2 started. time: 2019-06-12T12:52:09.623Z
[DEBUG] (main) Using Console logging
found failed one. Time: 2019-06-12T12:52:11.651Z Thread is ForkJoinPool.commonPool-worker-2
Validations Result is ready. time: 2019-06-12T12:52:11.651Z , success: false
```

As you can see, as soon as the failed validation happened, we managed to end the flow and did not waste time waiting for the other validator as the final result is already determined.

##Conclusion
`project reactor` provides a very powerful API to do parallel and non-blocking way of programming. When Running concurrent jobs and determining the first job which meets a specific condition as quickly as possible is easily doable by project reactor out of the box processors while Java 8 Stream API does not succeed.
you can use this while you have complex business validation for a user's request in enterprise applications.
