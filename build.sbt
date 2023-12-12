import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "agent-user-client-details"

val silencerVersion = "1.7.7"

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / majorVersion := 0
Compile   / scalafmtOnCompile := true
Test      / scalafmtOnCompile := true

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    PlayKeys.playDefaultPort         := 9449,
    PlayKeys.devSettings             += "play.server.http.idleTimeout" -> "3600 seconds",
    routesImport                     ++= Seq("uk.gov.hmrc.agentuserclientdetails.binders.Binders._"),
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions ++= Seq(
      "-Xlint:-missing-interpolator,_",
      "-Ywarn-value-discard",
      "-Ywarn-dead-code",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-Wconf:src=target/.*:s", // silence warnings from compiled files
      "-Wconf:src=Routes/.*:s" // silence warnings from routes files
    )
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*) // TODO https://github.com/hmrc/sbt-settings?tab=readme-ov-file#integration-tests
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings *)
  .settings(
      //fix for scoverage compile errors for scala 2.13.10
      libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always)
    )

Test / javaOptions += "-Dlogger.resource=logback-test.xml"
IntegrationTest / javaOptions += "-Dlogger.resource=logback-test.xml"

// sbt it:test will become sbt it/test - update jenkins before using
//lazy val it = project
//  .enablePlugins(PlayScala)
//  .dependsOn(microservice % "test->test") // the "test->test" allows reusing test code and test dependencies
//  .settings(DefaultBuildSettings.itSettings)
//  .settings(libraryDependencies ++= AppDependencies.test)
