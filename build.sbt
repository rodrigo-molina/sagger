ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.3"

lazy val root = (project in file("."))
  .settings(
    name              := "stagger",
    idePackagePrefix  := Some("com.example"),
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
  )

scalacOptions += "-Wnonunit-statement"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect"       % "3.5.4",
  "junit"          % "junit"             % "4.13.2" % Test,
  "org.scalameta" %% "munit"             % "1.0.0"  % Test,
  "org.typelevel" %% "munit-cats-effect" % "2.0.0"  % Test,
)
