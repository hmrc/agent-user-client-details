import sbt._

object AppDependencies {

  private val bootstrapVer: String = "7.22.0"
  private val mongoVer: String = "1.1.0"

  val compile = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-28" % mongoVer,
    "uk.gov.hmrc"             %% "agent-mtd-identifiers"             % "1.15.0",
    "uk.gov.hmrc"             %% "cluster-work-throttling"           % "8.5.0-play-28",
    "uk.gov.hmrc"             %% "agent-kenshoo-monitoring"          % "5.5.0",
    "uk.gov.hmrc"             %% "crypto-json-play-28"               % "7.3.0",
    "com.github.blemale"      %% "scaffeine"                         % "5.2.1",
    "joda-time"               %  "joda-time"                         % "2.12.5",
    "net.codingwell"          %% "scala-guice"                       % "6.0.0"
  )

  val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.74.0"        % "test, it",
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVer    % "test, it",
    "com.typesafe.akka"       %% "akka-stream-testkit"        % "2.6.21"        % "test, it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"         % "test, it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"        % "test, it"
  )
}
