# Elucidator

This project is the Activator analytics engine and was formerly a part of the Typesafe Console project and is now being used in the [Typesafe Activator](https://www.typesafe.com/activator) under the `Inspect` tab.

### Running

To run the project:

    sbt> project runner
    sbt> run

or

    >sbt runner/run

This will start an analyzer process and a Query API servicing requests.

### Unit Tests

To run unit tests, simply:

    sbt> test

To run the tests of a particular project, simply:

    sbt> <project>/test

To run a specific test, simply:

    sbt> test-only TestName

### Dependencies

Elucidator has a dependency on [Echo](http://github.com/typesafehub/echo) which is the actual tracing module.

When Echo receives data it feeds Elucidator with `trace events` which in turn are analyzed and statistics information is created. It is this information that is retrievable in the Query API at [http://localhost:9898/monitoring/api.html](http://localhost:9898/monitoring/api.html).

