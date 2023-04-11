import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    "uk.gov.hmrc.BuildInfo",
    ".*UserEnrolment.*",
    ".*AppConfigImpl.*",
    ".*DoNotCache.*",
    ".*Es3Cache.*",
    ".*DesIfHeaders.*",
    ".*FriendlyNameWorker.*",
    ".*LocalCaffeineCache.*",
    "app.*",
    "prod.*",
    ".*Routes.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*"
  )

  val settings: Seq[Setting[_]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90.00,
    ScoverageKeys.coverageMinimumStmtPerFile := 90.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
