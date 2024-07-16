package com.example

import scala.concurrent.duration.FiniteDuration
import cats.Monad
import cats.effect.{GenTemporal, MonadCancel}
import cats.effect.implicits.*
import cats.effect.kernel.Outcome
import cats.effect.std.Semaphore
import cats.implicits.*

sealed abstract class RateLimiter[F[_]: Monad] {

  /** This method gets semantically blocked after [[tokensPerPeriod]] calls, which are reset every [[period]].
    *
    * It's useful when there is a need to synchronize rated access to trigger an action.
    */
  def getRatedToken: F[Unit]

  /** Returns the number of tokens currently available. Always non-negative. */
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

    def availableTokens: F[Long] = semaphore.available // acquires the permit

    def getRatedToken: F[Unit] =
      semaphore.acquire
        .guaranteeCase {
          case Outcome.Succeeded(_) => semaphore.release.delayBy(period).uncancelable.start.void
          // from acquire documentation: semaphore.acquire method is interruptible, and in case of interruption it will
          // take care of restoring any permits it has acquired. If it does succeed however, managing permits correctly
          // is the user's responsibility
          case Outcome.Errored(_) => GenTemporal[F, Throwable].unit
          case Outcome.Canceled() => GenTemporal[F, Throwable].unit
        }
  }

}
