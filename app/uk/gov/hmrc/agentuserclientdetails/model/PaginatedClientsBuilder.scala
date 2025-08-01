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

package uk.gov.hmrc.agentuserclientdetails.model

import uk.gov.hmrc.agentmtdidentifiers.model.PaginatedList
import uk.gov.hmrc.agentmtdidentifiers.model.PaginationMetaData
import uk.gov.hmrc.agents.accessgroups.Client

object PaginatedClientsBuilder {

  def build(
    page: Int,
    pageSize: Int,
    clients: Seq[Client]
  ): PaginatedList[Client] = {
    val pageStart = (page - 1) * pageSize
    val pageEnd = pageStart + pageSize
    val numberOfPages = Math.ceil(clients.length.toDouble / pageSize.toDouble).toInt
    val pageSliceUntil = Math.min(
      pageEnd,
      if (numberOfPages == page)
        clients.length
      else
        clients.length - 1
    )

    val currentPageContent = clients.slice(pageStart, pageSliceUntil)

    PaginatedList[Client](
      pageContent = currentPageContent,
      paginationMetaData = PaginationMetaData(
        firstPage = page == 1,
        lastPage = numberOfPages == page,
        totalSize = clients.length,
        pageSize = pageSize,
        totalPages = numberOfPages,
        currentPageNumber = page,
        currentPageSize = currentPageContent.length
      )
    )
  }
}
