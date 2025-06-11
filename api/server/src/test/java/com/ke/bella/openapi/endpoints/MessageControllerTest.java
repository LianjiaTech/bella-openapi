package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testMessageEndpoint() throws Exception {
        MessageRequest request = new MessageRequest();
        // Set necessary fields for the request

        mockMvc.perform(post("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("") // Add serialized request here
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
    }
}