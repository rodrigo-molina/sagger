package com.example

import scala.concurrent.duration.{DurationInt, FiniteDuration}

import cats.effect.{IO, Ref}
import cats.implicits._

import munit.CatsEffectSuite

final class RateLimiterTest extends CatsEffectSuite {

  test("RateLimiter constructor should only allow positive maxConcurrent values") {
    interceptMessageIO[IllegalArgumentException]("maxConcurrent must be greater than 0") {
      RateLimiter[IO](0, 1.seconds)
    }
  }

  test("RateLimiter.getToken should get rated token") {
    RateLimiter[IO](2, 100.millis).flatMap { rateLimiter =>
      for {
        executionTimesRef <- Ref.of[IO, List[FiniteDuration]](List[FiniteDuration]())
        addExecutionTime = IO.monotonic.flatMap(time => executionTimesRef.update(_ :+ time))
        // run getRatedToken concurrently and store the execution time after getting the token
        _ <- (1 to 10).toList.parTraverse { _ =>
          rateLimiter.getRatedToken >> addExecutionTime
        }
        executionTimes <- executionTimesRef.get
        executionTimesSorted = executionTimes.sorted[FiniteDuration]
        // get the execution time in 100 milliseconds brackets.
        // e.g. an effect executed at 150 since the beginning is in the `1` bracket
        executionsInMillisBracket = executionTimesSorted.map(_ - executionTimesSorted.head).map(_.toMillis / 100)

        _ = assertEquals(executionsInMillisBracket.map(_.toInt), List(0, 0, 1, 1, 2, 2, 3, 3, 4, 4))
      } yield ()
    }
  }

  (1 to 100).foreach { n =>
    test(s"RateLimiter.getToken should release token on cancellation (execution $n / 100 times)") {
      RateLimiter[IO](2, 100.millis).flatMap { rateLimiter =>
        for {
          fiber <- rateLimiter.getRatedToken.start
          _     <- fiber.cancel
          _     <- fiber.join

          _ = assertIO(rateLimiter.availableTokens, 2L)
        } yield ()
      }
    }
  }

}
