package com.ke.bella.openapi.safety;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface ISafetyCheckService<T extends SafetyCheckRequest> {
    Object safetyCheck(T request, boolean isMock);

    /**
     * 获取异步执行器（用于异步安全检查）
     * @return 异步执行器，如果返回null则使用默认执行器
     */
    default Executor getExecutor() {
        return null;
    }

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
         *
         * @param response 完成响应
         * @param processData 处理数据（包含 safetyCheckMode 配置）
         * @param apikeyInfo API密钥信息
         * @param isMock 是否Mock模式
         * @return 风险数据（异步和跳过模式返回null）
         */
        default Object checkResponseOutput(CompletionResponse response, EndpointProcessData processData,
                                           ApikeyInfo apikeyInfo, boolean isMock) {
            SafetyCheckContext context = SafetyCheckContext.builder()
                    .mode(SafetyCheckMode.fromString(processData.getSafetyCheckMode()))
                    .requestId(processData.getRequestId())
                    .stage("output")
                    .build();

            return ISafetyCheckDelegatorService.create(this, context)
                    .safetyCheck(
                            SafetyCheckRequest.Chat.convertFrom(response, processData, apikeyInfo),
                            isMock
                    );
        }

        /**
         * 检查流式响应输出的安全性（用于 SSE 回调）
         * 支持同步模式下通过 resultCallback 发送检测结果
         *
         * @param response 完成响应
         * @param processData 处理数据（包含 safetyCheckMode 配置）
         * @param apikeyInfo API密钥信息
         * @param isMock 是否Mock模式
         * @param resultCallback 结果回调（同步模式下调用）
         */
        default void checkStreamOutput(CompletionResponse response, EndpointProcessData processData,
                                       ApikeyInfo apikeyInfo,
                                       boolean isMock, Consumer<Object> resultCallback) {
            SafetyCheckContext context = SafetyCheckContext.builder()
                    .mode(SafetyCheckMode.fromString(processData.getSafetyCheckMode()))
                    .requestId(processData.getRequestId())
                    .stage("output")
                    .resultCallback(resultCallback)
                    .build();

            ISafetyCheckDelegatorService.create(this, context)
                    .safetyCheck(
                            SafetyCheckRequest.Chat.convertFrom(response, processData, apikeyInfo),
                            isMock
                    );
        }
    }
}
