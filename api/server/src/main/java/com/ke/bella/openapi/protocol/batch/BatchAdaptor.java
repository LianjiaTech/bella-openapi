package com.ke.bella.openapi.protocol.batch;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Task;

import java.util.List;

public interface BatchAdaptor<T extends BatchProperty> extends IProtocolAdaptor {

    String uploadTasks(List<Task> tasks, T property);

    List<String> downloadTasks(String fileId, T property);

    Batch createBatch(BatchRequest request, String url, T property);

    Batch retrieveBatch(String batchId, String url, T property);

    OpenAiResponse<Batch> listBatches(ListSearchParameters request, String url, T property);

    Batch cancelBatch(String batchId, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/batches";
    }
}
