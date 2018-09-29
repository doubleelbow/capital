# Time

[Time is a small interceptor](https://github.com/doubleelbow/capital/blob/master/src/com/doubleelbow/capital/interceptor/impl/alpha/time.clj) that puts `time-fn` a function for obtaining current time, during the initialization stage, on context. `time-fn` can be called from other interceptors with the help of `current-time` function. It receives `context` as parameter and should return `org.joda.time.DateTime` object as a result.

Interceptor adds a bit of complexity but is useful to have, particularly in testing environment, because it adds ability to easily fake current time.
