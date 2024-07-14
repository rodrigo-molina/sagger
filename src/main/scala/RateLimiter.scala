package com.example

import scala.concurrent.duration.FiniteDuration

import cats.Monad
import cats.effect.GenTemporal
import cats.effect.implicits._
import cats.effect.std.Semaphore
import cats.implicits._

sealed abstract class RateLimiter[F[_] : Monad] {

  /** This method gets semantically blocked after [[tokensPerPeriod]] calls, which are reset every [[period]].
   *
   * It's useful when there is a need to synchronize rated access to trigger an action.
   */
  def getRatedToken: F[Unit]

  /** Returns the number of token currently available. Always non-negative. */
  def availableTokens: F[Long]

  /** Runs `f` if when a token is acquired. */
  def runWhenTokenAvailable[A](f: F[A]): F[A] = getRatedToken >> f

}

object RateLimiter {

  def apply[F[_]](tokensPerPeriod: Long, period: FiniteDuration)(implicit
                                                                 genTemporal: GenTemporal[F, Throwable]
  ): F[RateLimiter[F]] = for {
    _ <- GenTemporal[F, Throwable]
      .raiseWhen(tokensPerPeriod < 1)(new IllegalArgumentException("maxConcurrent must be greater than 0"))
    semaphore <- Semaphore[F](tokensPerPeriod)
  } yield new RateLimiter[F] {

    def availableTokens: F[Long] = semaphore.available

    def getRatedToken: F[Unit] =
      semaphore.acquire.bracket(_ => GenTemporal[F, Throwable].unit) { _ =>
        semaphore.release.delayBy(period).uncancelable.start.void
      }
  }

}
