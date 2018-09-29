# Response error

[Response error interceptor](https://github.com/doubleelbow/capital/blob/master/src/com/doubleelbow/capital/interceptor/impl/alpha/response_error.clj) is created with `interceptor` function that receives error functions.

### Error functions

Error function is a 2-arity function with parameters `context` and `error` that returns modified `error`. It is normally used to add specific `::response-error-type` to the error with the help of `add-type` function. Types `::transient` and `::retriable` are already available and are used by [circuit breaker](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/circuit_breaker.md) and [retry](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/retry.md) interceptors.
