package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;

public interface ISafetyCheckService<T extends SafetyCheckRequest> {
    Object safetyCheck(T request, boolean isMock);

    interface IChatSafetyCheckService extends ISafetyCheckService<SafetyCheckRequest.Chat> {
        /**
         * 检查请求输入的安全性
         * 根据 processData 中配置的 safetyCheckMode 自动选择同步/异步/跳过模式
         * 自动将风险数据设置到 processData 中
         *
         * @param request 完成请求
         * @param processData 处理数据（包含 safetyCheckMode 配置）
         * @param apikeyInfo API密钥信息
         * @param isMock 是否Mock模式
         * @return 风险数据（异步和跳过模式返回null）
         */
        default Object checkRequestInput(CompletionRequest request, EndpointProcessData processData,
                                         ApikeyInfo apikeyInfo, boolean isMock) {
            SafetyCheckContext context = SafetyCheckContext.builder()
                    .mode(SafetyCheckMode.fromString(processData.getSafetyCheckMode()))
                    .requestId(processData.getRequestId())
                    .stage("input")
                    .processData(processData)
                    .build();

            Object requestRiskData = ISafetyCheckDelegatorService.create(this, context)
                    .safetyCheck(
                            SafetyCheckRequest.Chat.convertFrom(request, processData, apikeyInfo),
                            isMock
                    );

            // 自动设置到 processData
            processData.setRequestRiskData(requestRiskData);

            return requestRiskData;
        }

        /**
         * 检查响应输出的安全性
         * 根据 processData 中配置的 safetyCheckMode 自动选择同步/异步/跳过模式
         * 对于异步模式的非流式响应，会短暂等待结果（最多50ms）
         *
         * @param response 完成响应
         * @param processData 处理数据（包含 safetyCheckMode 配置）
         * @param apikeyInfo API密钥信息
         * @param isMock 是否Mock模式
         * @return 风险数据（skip模式返回null，异步模式尽力返回）
         */
        default Object checkResponseOutput(CompletionResponse response, EndpointProcessData processData,
                                           ApikeyInfo apikeyInfo, boolean isMock) {
            SafetyCheckContext context = SafetyCheckContext.builder()
                    .mode(SafetyCheckMode.fromString(processData.getSafetyCheckMode()))
                    .requestId(processData.getRequestId())
                    .stage("output")
                    .processData(processData)
                    .build();

            Object result = ISafetyCheckDelegatorService.create(this, context)
                    .safetyCheck(
                            SafetyCheckRequest.Chat.convertFrom(response, processData, apikeyInfo),
                            isMock
                    );

            // 如果是异步模式且返回null，短暂等待后从processData读取（5ms查一次，最多50ms）
            if (result == null && SafetyCheckMode.async == context.getMode()) {
                for (int i = 0; i < 10; i++) {
                    result = processData.getResponseRiskData();
                    if (result != null) {
                        break;
                    }
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            return result;
        }

        /**
         * 检查流式响应输出的安全性
         * 结果会写入 processData.responseRiskData，由调用方轮询获取
         *
         * @param response 完成响应
         * @param processData 处理数据（包含 safetyCheckMode 配置）
         * @param apikeyInfo API密钥信息
         * @param isMock 是否Mock模式
         */
        default void checkStreamOutput(CompletionResponse response, EndpointProcessData processData,
                                       ApikeyInfo apikeyInfo, boolean isMock) {
            SafetyCheckContext context = SafetyCheckContext.builder()
                    .mode(SafetyCheckMode.fromString(processData.getSafetyCheckMode()))
                    .requestId(processData.getRequestId())
                    .stage("output")
                    .processData(processData)
                    .build();

            ISafetyCheckDelegatorService.create(this, context)
                    .safetyCheck(
                            SafetyCheckRequest.Chat.convertFrom(response, processData, apikeyInfo),
                            isMock
                    );
        }
    }
}
