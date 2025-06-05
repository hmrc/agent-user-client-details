import uk.gov.hmrc.DefaultBuildSettings
import play.sbt.routes.RoutesKeys

val appName = "agent-user-client-details"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.6.1"

val scalaCOptions = Seq(
  "-feature",
  "-language:implicitConversions",
  "-Wconf:src=target/.*:s", // silence warnings from compiled files
  "-Wconf:src=Routes/.*:s", // silence warnings from routes files
  "-Wconf:msg=Flag.*repeatedly:s" // silence warnings regarding compiler settings being set multiple times by both sbt-auto-build and sbt-plugin
)


lazy val root = (project in file("."))
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 9449,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    routesImport ++= Seq("uk.gov.hmrc.agentuserclientdetails.binders.Binders._"),
    scalacOptions ++= scalaCOptions,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"
  )
  .settings(
    RoutesKeys.routesImport += "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl"
  )
  .settings(Test / parallelExecution := false,
    CodeCoverageSettings.settings
  )
  .settings(
    PlayKeys.devSettings             += "play.server.http.idleTimeout" -> "3600 seconds",
  )
  .settings(commands ++= SbtCommands.commands)
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)


lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
  .settings(
    scalacOptions ++= scalaCOptions,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )

