# Circuit breaker

Martin Fowler's bliki offers a good explanation of [circuit breaker](https://www.martinfowler.com/bliki/CircuitBreaker.html).

[Circuit breaker interceptor](https://github.com/doubleelbow/capital/blob/master/src/com/doubleelbow/capital/interceptor/impl/alpha/circuit_breaker.clj) is created with `interceptor` function that receives configuration map parameter `config`.

### Configuration map

Configuration map should have following keys `::threshold`, `::interval`, `::min-requests` and `::open-duration`.

Keys `::threshold`, `::interval` and `::min-requests` help determine whether the circuit breaker should trip (from `::closed`) to `::opened` state. Circuit breaker trips if at least `::min-requests` have been sent over a given `::interval` in seconds and `::threshold` level is reached. The `::threshold` could be either a positive integer or ratio between 0 and 1 and it denotes the number or ratio of transient errors that should occur before circuit breaker trips.

Key `::open-duration` helps determine if circuit breaker should trip (from `::opened`) to `::half-opened` state. The value of `::open-duration` should be a duration in seconds for which circuit breaker is opened.

Currently circuit breaker is in `::half-opened` state only for a single request. If response is transient error, circuit breaker is opened otherwise it is closed.

Circuit breaker always starts in `::closed` state.

#### Example

```clojure
{
  ::threshold 10
  ::interval 600
  ::min-requests 20
  ::open-duration 60
}
```

Circuit breaker configured with the above configuration map would trip from closed to opened state on 10<sup>th</sup> transient error response in last 600 seconds provided that at least 20 requests were issued (in that same 600 seconds interval). Once opened, circuit breaker would stay opened for 60 seconds.

### Dependencies on other interceptors

Circuit breaker depends explicitly on [time interceptor](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/time.md) because it needs access to current time for time based calculations. It depends implicitly on [response error interceptor](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/response_error.md) to check if error is transient.
