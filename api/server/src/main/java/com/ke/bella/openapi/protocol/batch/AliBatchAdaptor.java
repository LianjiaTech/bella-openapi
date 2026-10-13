package com.ke.bella.openapi.protocol.batch;

import com.theokanning.openai.ListSearchParameters;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component("AliBatchAdaptor")
public class AliBatchAdaptor implements BatchAdaptor<AliProperty> {

    private final OpenAIBatchAdaptor openAiBatchAdaptor;

    @Autowired
    public AliBatchAdaptor(@Qualifier("OpenAiBatchAdaptor") OpenAIBatchAdaptor openAiBatchAdaptor) {
        this.openAiBatchAdaptor = openAiBatchAdaptor;
    }

    @Override
    public String uploadTasks(List<Task> tasks, AliProperty property) {
        return openAiBatchAdaptor.uploadTasks(tasks, property);
    }

    @Override
    public List<String> downloadTasks(String fileId, AliProperty property) {
        return openAiBatchAdaptor.downloadTasks(fileId, property);
    }

    @Override
    public Batch createBatch(BatchRequest request, String url, AliProperty property) {
        return openAiBatchAdaptor.createBatch(request, url, property);
    }

    @Override
    public Batch retrieveBatch(String batchId, String url, AliProperty property) {
        return openAiBatchAdaptor.retrieveBatch(batchId, url, property);
    }

    @Override
    public OpenAiResponse<Batch> listBatches(ListSearchParameters request, String url, AliProperty property) {
        return openAiBatchAdaptor.listBatches(request, url, property);
    }

    @Override
    public Batch cancelBatch(String batchId, String url, AliProperty property) {
        return openAiBatchAdaptor.cancelBatch(batchId, url, property);
    }

    @Override
    public String getDescription() {
        return "阿里百炼 Batch API协议（委托给OpenAI实现）";
    }

    @Override
    public Class<AliProperty> getPropertyClass() {
        return AliProperty.class;
    }
}
