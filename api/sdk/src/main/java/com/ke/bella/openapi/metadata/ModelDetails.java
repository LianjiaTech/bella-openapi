package com.ke.bella.openapi.metadata;

import com.ke.bella.openapi.BaseDto;
import lombok.Data;

import java.util.List;

@Data
public class ModelDetails extends BaseDto {
    private Model model;
    private List<Channel> channels;
}
