name := "DatabaseActor"
version := "1.0"
scalaVersion := "2.12.4"
scalacOptions := Seq(
  "-language:_",
  "-feature",
  "-deprecation"
)

val versions = new {
  val akkaFullVersion = "2.5.4"
  val scalatestFullVersion = "3.0.3"

  val junitFullVersion = "4.12"
  val jooqVersion = "3.10.1"
  val scalaMockVersion = "3.6.0"
  val mockitoVersion = "2.8.47"
  val h2Version = "1.4.196"
}

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % versions.akkaFullVersion,
  "org.jooq" % "jooq" % versions.jooqVersion,
  "org.jooq" %% "jooq-scala" % versions.jooqVersion,
  "com.typesafe.akka" %% "akka-testkit" % versions.akkaFullVersion % Test,
  "org.scalatest" %% "scalatest" % versions.scalatestFullVersion % Test,
  "org.scalamock" %% "scalamock-scalatest-support" % versions.scalaMockVersion % Test,
  "org.mockito" % "mockito-core" % versions.mockitoVersion % Test,
  "junit" % "junit" % versions.junitFullVersion % Test,
  "com.h2database" % "h2" % versions.h2Version % Test,
  "mysql" % "mysql-connector-java" % "6.0.6" % Runtime
)


unmanagedSourceDirectories in Test += baseDirectory(_ / "src" / "integration_test" / "scala").value