import { buildOpenapiLogsQuery } from "../query"

describe("buildOpenapiLogsQuery", () => {
  it("excludes batch inner request logs for AK Code queries", () => {
    expect(
      buildOpenapiLogsQuery({
        queryType: "AK Code",
        queryValue: "ak-test",
      })
    ).toBe('data_info_msg_akCode:"ak-test" AND (NOT data_info_msg_batch:true)')
  })

  it("keeps the batch filter scoped to AK Code queries", () => {
    expect(
      buildOpenapiLogsQuery({
        queryType: "Request ID",
        queryValue: "req-1",
      })
    ).toBe('data_info_msg_requestId:"req-1"')
  })

  it("preserves optional status, endpoint, and model filters", () => {
    expect(
      buildOpenapiLogsQuery({
        queryType: "AK Code",
        queryValue: "ak-test",
        httpCode: " 500 ",
        endpointCode: "/v1/chat/completions",
        modelName: "gpt-4.1",
      })
    ).toBe(
      'data_info_msg_akCode:"ak-test" AND (NOT data_info_msg_batch:true) AND data_info_msg_response: ("\\"httpCode\\"\\: 500") AND (data_info_msg_endpoint:"/v1/chat/completions" OR data_info_msg_endpoint:"/v1/messages" OR data_info_msg_endpoint:"/v1/responses") AND data_info_msg_model.keyword:"gpt-4.1"'
    )
  })
})
