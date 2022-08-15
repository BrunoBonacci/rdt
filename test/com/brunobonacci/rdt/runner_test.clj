(ns com.brunobonacci.rdt.runner-test
  (:require [com.brunobonacci.rdt :refer :all]
            [com.brunobonacci.rdt.runner :as run]))



(repl-test "testing matching labels"

  ((run/matches-labels? :all nil) {})
  => true

  ((run/matches-labels? :all nil) {:labels []})
  => true

  ((run/matches-labels? :all nil) {:labels [:slow]})
  => true

  ((run/matches-labels? [] nil) {:labels [:slow]})
  => false

  ((run/matches-labels? [:slow] nil) {:labels [:slow]})
  => true

  ((run/matches-labels? [:slow :integration] nil) {:labels [:slow :generated]})
  => true

  ((run/matches-labels? [:slow :integration] nil) {:labels [:slow :integration]})
  => true

  ((run/matches-labels? [:slow :integration] [:linux-only]) {:labels [:slow :generated]})
  => true

  ((run/matches-labels? [:slow :integration] [:generated]) {:labels [:slow :generated]})
  => false

  ((run/matches-labels? [:integration] [:generated]) {:labels [:slow :generated]})
  => false

  ((run/matches-labels? :all [:generated]) {:labels [:slow :generated]})
  => false

  )
