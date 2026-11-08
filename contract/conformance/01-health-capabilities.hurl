# Health and capabilities

GET {{base}}/finsim/v1/institution/health
HTTP 200
[Asserts]
jsonpath "$.status" == "UP"
jsonpath "$.contractVersions" includes "finsim/v1"

GET {{base}}/finsim/v1/institution/capabilities
HTTP 200
[Asserts]
jsonpath "$.institutionType" exists
jsonpath "$.capabilities" includes "ACCOUNT_DEBIT"
jsonpath "$.capabilities" includes "ACCOUNT_CREDIT"
jsonpath "$.capabilities" includes "STATUS_QUERY"
jsonpath "$.currencies" includes "USD"
