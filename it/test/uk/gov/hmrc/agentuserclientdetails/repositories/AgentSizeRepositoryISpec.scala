/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mongodb.scala.model.IndexModel
import uk.gov.hmrc.agentuserclientdetails.BaseIntegrationSpec
import uk.gov.hmrc.agentuserclientdetails.model.Arn
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext

class AgentSizeRepositoryISpec
extends BaseIntegrationSpec
with DefaultPlayMongoRepositorySupport[AgentSize] {

  implicit val executionContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  override protected val repository: PlayMongoRepository[AgentSize] = new AgentSizeRepositoryImpl(mongoComponent)

  val arn: Arn = Arn("KARN1234567")

  val agentSize: AgentSize = AgentSize(
    arn,
    50,
    LocalDateTime.now()
  )

  val agentSizeRepository: AgentSizeRepositoryImpl = repository.asInstanceOf[AgentSizeRepositoryImpl]

  "AgentSizeRepository" when {

    "set up" should {
      "have correct indexes" in {
        agentSizeRepository.collectionName shouldBe "agent-size"
        agentSizeRepository.indexes.size shouldBe 1
        val indexModel: IndexModel = agentSizeRepository.indexes.head
        assert(indexModel.getKeys.toBsonDocument.containsKey("arn"))
        indexModel.getOptions.getName shouldBe "arnIdx"
        assert(indexModel.getOptions.isUnique)
      }
    }

    "fetching a non-existing record" should {
      "return nothing" in {
        agentSizeRepository.get(arn).futureValue shouldBe None
      }
    }

    "fetching an existing record" should {
      "return the agentSize record" in {
        agentSizeRepository.upsert(agentSize).futureValue shouldBe Some(RecordInserted)
        agentSizeRepository.get(arn).futureValue shouldBe Some(agentSize)
      }
    }

    "updating an existing record" should {
      s"return $RecordUpdated" in {
        agentSizeRepository.upsert(agentSize).futureValue shouldBe Some(RecordInserted)
        agentSizeRepository.upsert(agentSize).futureValue shouldBe Some(RecordUpdated)
      }
    }
  }

  "delete" should {
    "delete data" in {
      agentSizeRepository.upsert(agentSize).futureValue shouldBe Some(RecordInserted)
      agentSizeRepository.delete(arn.value).futureValue shouldBe 1L
    }
  }

}
