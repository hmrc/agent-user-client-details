
# agent-user-client-details

This microservice is used for creating and maintaining Agent Access Groups. It supports the frontend microservice agent-permissions-frontend along with agent-permissions.

It's primary function is to orchestrate changes in EACD after an Access Group has been created or modified.

It also contains the following to support the display of a client list within ASA:
- ES3 cache used for retrieving a full client list (ASA supported services*)
- Friendly name fetch to backfill missing friendly names (async process)

*Note: Personal Income Record is not currently supported for access groups, although it is listed in agent-mtd-identifiers 


## Endpoints

### Client list

| **Method** | **Path**                       | **Description**                           |Allows Assistant user|
|------------|--------------------------------|-------------------------------------------|----|
| GET  | /arn/:arn/client-list    | gets client list for ARN             | true |
| GET  | /arn/:arn/clients?page=:{page number}&pageSize=:{page size}&search=:{search term}&filter=:{filter term}           | gets a page of clients from cache that meet search and filter terms  | false |
| GET  | /arn/:arn/client-list-status           | tbc  | false |
| GET  | /arn/:arn/tax-service-client-count            | returns client counts for each tax service id (enrolment key) using the es3 cache | true |
| GET  | /groupid/:groupid/outstanding-work-items          |  tbc  | false |
| GET  | /arn/:arn/outstanding-work-items           | tbc  | false |
| GET  | /arn/:arn/clients-assigned-users           | tbc  | false |
| POST | /work-items/clean           | tbc  | false |
| GET  | /work-items/stats           | tbc  | false |

### Agent checks

| **Method** | **Path**     | **Description**                           | Allows Assistant user |
|------------|--------------|-------------------------------------------|-----------------------|
| GET  | /arn/:arn/agent-size    | returns client count            | false                 |
| GET  | /arn/:arn/user-check            | returns no content if more than one user in group | false                 |
| GET  | /arn/:arn/work-items-exist          |  tbc  | false                 |
| GET  | /arn/:arn/assignments-work-items-exist           | tbc  | false                 |
| GET  | /arn/:arn/team-members            | tbc  | true                  |

### Friendly name

| **Method** | **Path**                       | **Description**                           | Allows Assistant user |
|------------|--------------------------------|-------------------------------------------|-----------------------|
| POST   | /arn/:arn/friendly-name    | updates a batch of friendly names as part of opt-in           | false                 |
| PUT  |  /arn/:arn/update-friendly-name            | updates one agent friendlyName field | false                 |

### Assignments

| **Method** | **Path**                       | **Description**                           | Allows Assistant user |
|------------|--------------------------------|-------------------------------------------|-----------------------|
| POST   | /user-enrolment-assignments    | tbc            | false                 |



## Running the tests

    sbt "test;IntegrationTest/test"

## Running the tests with coverage

    sbt "clean;coverageOn;test;IntegrationTest/test;coverageReport"

### Automated testing
This service is tested by the following automated test repositories:
- [agent-gran-perms-acceptance-tests](https://github.com/hmrc/agent-gran-perms-acceptance-tests/)
- [agent-granperms-performance-tests](https://github.com/hmrc/agent-granperms-performance-tests)

## Running the app locally

    sm --stop AGENT_USER_CLIENT_DETAILS
    sbt run

It should then be listening on port 9449


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
