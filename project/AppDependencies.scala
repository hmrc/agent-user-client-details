import sbt.*

object AppDependencies {

  private val bootstrapVer: String = "9.12.0"
  private val mongoVer: String = "2.6.0"
  private val pekkoVersion = "1.0.3"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-30" % mongoVer,
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"         % bootstrapVer,
    "uk.gov.hmrc"             %% "agent-mtd-identifiers"             % "3.0.0",
    "uk.gov.hmrc"             %% "cluster-work-throttling"           % "9.2.0",
    "uk.gov.hmrc"             %% "crypto-json-play-30"               % "8.2.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % mongoVer        % Test,
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVer    % Test,
    "org.apache.pekko"        %% "pekko-stream-testkit"       % pekkoVersion    % Test,
    "org.scalamock"           %% "scalamock"                  % "6.0.0"         % Test
  )
}
