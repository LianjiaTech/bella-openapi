package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.live.session.LiveSessionAdmissionService;
import com.ke.bella.openapi.protocol.live.session.LiveSessionConstants;
import com.ke.bella.openapi.protocol.live.session.LiveSessionHandler;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.service.EndpointDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@EndpointAPI
@RestController
@RequestMapping("/v1/live")
public class LiveSessionController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LiveSessionAdmissionService admissionService;
    @Autowired
    private EndpointLogger logger;
    @Autowired
    private EndpointDataService endpointDataService;

    @RequestMapping("/sessions")
    public void sessions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if(!"websocket".equalsIgnoreCase(request.getHeader("Upgrade"))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        endpointDataService.setEndpointData(LiveSessionConstants.ENDPOINT, "", null);
        LiveSessionHandler handler = new LiveSessionHandler(EndpointContext.getProcessData(), EndpointContext.getApikey(), router,
                adaptorManager, admissionService, logger);
        new WebSocketHttpRequestHandler(handler).handleRequest(request, response);
    }
}
