# capital [![CircleCI](https://circleci.com/gh/doubleelbow/capital.svg?style=svg)](https://circleci.com/gh/doubleelbow/capital) [![Clojars Project](https://img.shields.io/clojars/v/com.doubleelbow.capital/capital.svg)](https://clojars.org/com.doubleelbow.capital/capital)

Capital is badly named clojure library that helps with calls to external systems. At core it uses [the concept of interceptors](http://pedestal.io/reference/interceptors) as defined by Pedestal.

Capital is currently in early alpha which means that breaking changes will be made and usage in mission critical projects is not advised. To move capital out of early alpha usage and [contributions](CONTRIBUTING.md) are encouraged.

## Getting started

To get familiar with capital it's best to see how it can be used. There are some rather contrived examples in dev folder and there are also libraries that use capital like

* [capital http](https://github.com/doubleelbow/capital-http)
* [capital file](https://github.com/doubleelbow/capital-file)

### Why capital? (or the bad name explained)

This library would not exist without Pedestal's interceptors. Pedestal's interceptors are on incoming side (bottom - nearer the ground) of (web)applications but capital's are on outgoing side (top - nearer the cloud(s)) side of applications. The name is bad because, the way this world works, it easily elicits wrong associations. In this context it's better to think architecture than punishment.

### What are external systems?

External systems, external services, 3<sup>rd</sup> party systems are terms used interchangeably to describe any code that resides outside of given application. Examples of external systems are web services (dealt by [capital http](https://github.com/doubleelbow/capital-http)) and file system (dealt by [capital file](https://github.com/doubleelbow/capital-file)).

## Overview

The world today seems absolutely crackers &hellip; Majority of applications have to communicate with external systems. We're routinely writing code for reading files, making HTTP requests or querying databases. When failures in this between systems communication happen our applications can start to suffer. Capital strives to improve resiliency of your application by using patterns such as [retry](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/retry.md) and [circuit breaker](https://github.com/doubleelbow/capital/blob/master/doc/interceptors/circuit_breaker.md).

## Thanks

Thanks to [pedestal](https://github.com/pedestal/pedestal) for interceptors and logging. Thanks also to authors of [pathetic](https://github.com/davidsantiago/pathetic) and [clj-time](https://github.com/clj-time/clj-time) libraries.


## License

Copyright © 2018 doubleelbow

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
