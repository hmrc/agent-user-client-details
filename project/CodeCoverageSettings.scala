import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    ".*\\$anon.*",
    "uk.gov.hmrc.BuildInfo",
    ".*UserEnrolment.*",
    ".*AppConfigImpl.*",
    ".*DoNotCache.*",
    ".*DesIfHeaders.*",
    ".*FriendlyNameWorker.*",
    "Module.*",
    "app.*",
    "prod.*",
    ".*Routes.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*",
    ".*TestOnlyController.*"
  )

  val settings: Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90.00,
    ScoverageKeys.coverageMinimumBranchTotal:= 70,
    ScoverageKeys.coverageMinimumBranchPerFile := 00.00,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
