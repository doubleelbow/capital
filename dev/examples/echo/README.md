# echo

This is a simple command line application that serves as an example of using Capital. It consist of a single Capital service named "echo-service" that returns received string message or error info if received message contains "error". Additionally echo service stores non-error responses in a cache. A " - cached value" string is added to response if it's obtained from cache.

## Usage

The application can be started by

```console
lein run
```

or by

```console
lein uberjar
java -jar target/uberjar/echo-0.1.0-SNAPSHOT-standalone.jar
```

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
