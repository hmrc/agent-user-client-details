import sbt._

object AppDependencies {

  private val bootstrapVer: String = "8.6.0"
  private val mongoVer: String = "1.9.0"
  private val pekkoVersion = "1.0.2"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-30" % mongoVer,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"        % bootstrapVer,
    "uk.gov.hmrc"             %% "agent-mtd-identifiers"             % "2.0.0",
    "uk.gov.hmrc"             %% "cluster-work-throttling"           % "8.6.0",
    "uk.gov.hmrc"             %% "crypto-json-play-30"               % "7.6.0",
    "com.github.blemale"      %% "scaffeine"                         % "5.2.1",
    "net.codingwell"          %% "scala-guice"                       % "6.0.0"
  )

  val test = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % mongoVer        % Test,
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVer    % Test,
    "org.apache.pekko"        %% "pekko-stream-testkit"       % pekkoVersion    % Test,
    "org.scalamock"           %% "scalamock"                  % "6.0.0"         % Test
  )
}
