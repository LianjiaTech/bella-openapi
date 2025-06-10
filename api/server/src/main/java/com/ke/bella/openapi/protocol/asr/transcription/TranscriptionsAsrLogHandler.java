package com.ke.bella.openapi.protocol.asr.transcription;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class TranscriptionsAsrLogHandler implements EndpointLogHandler {
    @Autowired
    private ChannelRouter router;

    @Override
    public void process(EndpointProcessData processData) {
        String model = processData.getModel();
        String endpoint = processData.getEndpoint();
        if (!model.isEmpty()) {
            ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), processData.isMock());
            processData.setPriceInfo(channel.getPriceInfo());
        }
        processData.setInnerLog(true);
    }

    @Override
    public String endpoint() {
        return "/v1/audio/transcriptions";
    }
}
