import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings


val appName = "agent-user-client-details"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    PlayKeys.playDefaultPort         := 9449,
    PlayKeys.devSettings             += "play.server.http.idleTimeout" -> "3600 seconds",
    routesImport                     ++= Seq("uk.gov.hmrc.agentuserclientdetails.binders.Binders._"),
    majorVersion                     := 0,
    scalaVersion                     := "2.13.8",
    Compile / scalafmtOnCompile      := true,
    Test / scalafmtOnCompile         := true,
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
    //fix for scoverage compile errors for scala 2.13
    libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always),
    scalacOptions ++= Seq(
      //"-Werror",
      //"-language:implicitConversions",
      "-Xlint",
      "-Wdead-code",
      "-feature",
      "-Wconf:src=target/.*:s", // silence warnings from compiled files
      "-Wconf:src=*html:w", // silence html warnings as they are wrong
      "-Wconf:src=*.routes:s" // silence routes warnings as they are wrong
    ),
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)

Test / javaOptions += "-Dlogger.resource=logback-test.xml"
IntegrationTest / javaOptions += "-Dlogger.resource=logback-test.xml"