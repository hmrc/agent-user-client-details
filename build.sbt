import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings


val appName = "agent-user-client-details"

val silencerVersion = "1.7.7"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    PlayKeys.playDefaultPort         := 9449,
    PlayKeys.devSettings             += "play.server.http.idleTimeout" -> "3600 seconds",
    routesImport                     ++= Seq("uk.gov.hmrc.agentuserclientdetails.binders.Binders._"),
    majorVersion                     := 0,
    scalaVersion                     := "2.13.10",
    Compile / scalafmtOnCompile      := true,
    Test / scalafmtOnCompile         := true,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .settings(
      //fix for scoverage compile errors for scala 2.13.10
      libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always)
    )

Test / javaOptions += "-Dlogger.resource=logback-test.xml"
IntegrationTest / javaOptions += "-Dlogger.resource=logback-test.xml"