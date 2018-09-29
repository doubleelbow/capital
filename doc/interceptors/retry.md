# Retry

[Retry interceptor](https://github.com/doubleelbow/capital/blob/master/src/com/doubleelbow/capital/interceptor/impl/alpha/retry.clj) is used to retry a request that returned a **retriable** error response. Interceptor is created with `interceptor` function that receives configuration map parameter.

### Configuration map

Configuration map should contain `::delay` and `::max-retries` keys and could also have optional `::delay-type` and `::retirable-fn` keys.

`::max-retries` states the number of times a request is retried before propagating the error down the interceptor chain.

`::delay` is a vector of two values that express first and second delay in milliseconds. Delays are used before retrying a request. Based on those two values and `::delay-type` all further delays are calculated. If the value of `::delay-type` is `::linear` then delays increase linearly, otherwise they increase exponentially.

Optional `::retriable-fn` is a 2-arity function receiving `context` and `error` as parameters and should return truthy value only if error is deemed retriable. If `::retriable-fn` is not present then a function is used that checks if error was marked as `::retriable` by response error interceptor.

Requests are retried until non retriable response is returned or `::max-retries` is reached.

### Dependencies on other interceptors

Retry interceptor depends implicitly on [response error interceptor](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/response_error.md) to check if error is retriable.
