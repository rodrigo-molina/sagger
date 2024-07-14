package com.example

import scala.concurrent.duration.DurationInt

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._

object ExampleApp extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = for {
    rateLimiter <- RateLimiter[IO](tokensPerPeriod = 2, period = 1000.millis)
    tasks = (1 to 5).toList.map(neverEndingTask)
    _ <- IO.parSequenceN(Int.MaxValue)(tasks.map(rateLimiter.runWhenTokenAvailable))
  } yield ExitCode.Success

  private def neverEndingTask(i: Int): IO[Unit] = for {
    time <- IO.realTimeInstant
    _    <- IO.println(s"[$time] Task $i started")
    _    <- IO.never[Unit].onCancel(IO.println(s"Task $i cancelled"))
  } yield ()

}
