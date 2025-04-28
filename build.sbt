import uk.gov.hmrc.{DefaultBuildSettings}
import play.sbt.routes.RoutesKeys

val appName = "agent-user-client-details"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.12"

val scalaCOptions = Seq(
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


lazy val root = (project in file("."))
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 9449,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers ++= Seq(Resolver.typesafeRepo("releases")),
    routesImport ++= Seq("uk.gov.hmrc.agentuserclientdetails.binders.Binders._"),
    scalacOptions ++= scalaCOptions,
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"
  )
  .settings(
    RoutesKeys.routesImport += "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl"
  )
  .settings(resolvers += Resolver.jcenterRepo)
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
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )

