# RDT - REPL-Driven tests
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/rdt.svg)](https://clojars.org/com.brunobonacci/rdt)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/rdt)](https://cljdoc.org/d/com.brunobonacci/rdt/CURRENT) [![com.brunobonacci](https://circleci.com/gh/com.brunobonacci/rdt.svg?style=shield)](https://circleci.com/gh/com.brunobonacci/rdt) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/rdt.svg)

RDT is a library designed to create tests that are like REPL sessions,
low structure, easy to debug when they fail because they are just
REPL sessions.

**WORK IN PROGRESS**

## Usage

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/rdt "0.1.0-SNAPSHOT"]

;; deps.edn format
{:deps { com.brunobonacci/rdt {:mvn/version "0.1.0-SNAPSHOT"}}}
```

Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/rdt.svg)](https://clojars.org/com.brunobonacci/rdt)


Then require the namespace:

``` clojure
(ns foo.bar
  (:require [com.brunobonacci.rdt :as rdt]))
```

Check the [online documentation](https://cljdoc.org/d/com.brunobonacci/rdt/CURRENT)

Then it's up to you...

## License

Copyright © 2021 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
