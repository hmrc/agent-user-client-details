/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentuserclientdetails.repositories

import com.google.inject.ImplementedBy
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.result.UpdateResult
import org.mongodb.scala.model.Filters.{and, equal, exists}
import org.mongodb.scala.model.IndexModel
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import play.api.Logging
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import reactivemongo.play.json.ImplicitBSONHandlers.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// Keeping it generic so our options are open in case we want to track other kind of jobs later (e.g. ES11/ES12 assignments, etc.)
sealed trait JobData

object JobData {
  implicit val format: Format[JobData] = new Format[JobData] {
    // Kludgy format unfortunately due to mongo codecs not generating correctly otherwise.
    def writes(o: JobData): JsValue = o match {
      case x: FriendlyNameJobData => Json.toJsObject(x)
    }
    def reads(json: JsValue): JsResult[JobData] = json match {
      case x: JsObject =>
        (x \ "jobType") match {
          case JsDefined(JsString(FriendlyNameJobData.jobType)) => Json.fromJson[FriendlyNameJobData](x)
        }
    }
  }
}

case class FriendlyNameJobData(
  groupId: String,
  enrolmentKeys: Seq[String],
  sendEmailOnCompletion: Boolean,
  agencyName: Option[String],
  email: Option[String],
  emailLanguagePreference: Option[String], // "en" or "cy"
  startTime: LocalDateTime, // Using Java time instead of Joda time as per latest hmrc-mongo recommendations.
  finishTime: Option[LocalDateTime] = None,
  _id: Option[BSONObjectID] = None,
  jobType: String =
    FriendlyNameJobData.jobType // do not change this. Must include it explicitly or the mongo codec will not generate correctly.
) extends JobData

object FriendlyNameJobData {
  val jobType = "FriendlyNameJob"
  implicit val format: OFormat[FriendlyNameJobData] = Json.format[FriendlyNameJobData]
}

@ImplementedBy(classOf[JobMonitoringRepositoryImpl])
trait JobMonitoringRepository {
  def getUnfinishedFriendlyNameFetchJobData: Future[Seq[FriendlyNameJobData]]
  def getFriendlyNameFetchJobData(groupId: String): Future[Seq[FriendlyNameJobData]]
  def createFriendlyNameFetchJobData(jobData: FriendlyNameJobData): Future[Option[BSONObjectID]]
  def markAsFinishedFriendlyNameFetchJobData(objectId: BSONObjectID, finishTime: LocalDateTime): Future[UpdateResult]
}

@Singleton
class JobMonitoringRepositoryImpl @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[JobData](
      collectionName = "job-monitoring",
      domainFormat = JobData.format,
      mongoComponent = mongoComponent,
      extraCodecs = Seq(
        Codecs.playFormatCodec(FriendlyNameJobData.format),
        Codecs.playFormatCodec(reactivemongo.play.json.ImplicitBSONHandlers.BSONObjectIDFormat)
      ),
      indexes = Seq(
        IndexModel(ascending("groupId"), new IndexOptions().name("groupIdIdx").unique(false)),
        IndexModel(ascending("jobType"), new IndexOptions().name("jobTypeIdx").unique(false))
      )
    ) with JobMonitoringRepository with Logging {

  def getFriendlyNameFetchJobData(groupId: String): Future[Seq[FriendlyNameJobData]] =
    collection
      .find(and(equal("jobType", FriendlyNameJobData.jobType), equal("groupId", groupId)))
      .collect()
      .head()
      .map(_.collect { case x: FriendlyNameJobData => x })

  def createFriendlyNameFetchJobData(jobData: FriendlyNameJobData): Future[Option[BSONObjectID]] = {
    val objId = jobData._id.getOrElse(BSONObjectID.generate)
    collection
      .insertOne(jobData.copy(_id = Some(objId), finishTime = None))
      .headOption()
      .map(_.map(_ => objId))
  }

  def markAsFinishedFriendlyNameFetchJobData(
    objectId: BSONObjectID,
    finishTime: LocalDateTime
  ): Future[UpdateResult] =
    collection
      .updateOne(equal("_id", objectId), set("finishTime", finishTime))
      .toFuture

  def getUnfinishedFriendlyNameFetchJobData: Future[Seq[FriendlyNameJobData]] =
    collection
      .find(and(equal("jobType", FriendlyNameJobData.jobType), exists("finishTime", false)))
      .collect()
      .head()
      .map(_.collect { case x: FriendlyNameJobData => x })
}
