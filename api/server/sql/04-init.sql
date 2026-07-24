insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/chat/completions', 'ep-cd2190a7-38dc-427e-a167-2f138b9078e7', N'智能问答', '0', 'system', 'active',
        0, 'system', 0, 'system');

insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/embeddings', 'ep-4eb69c9c-4acc-40e8-a671-0b25d9473139', N'向量化', '0', 'system', 'active', 0, 'system', 0, 'system');

insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/audio/speech', 'ep-c73addd8-0517-4ad9-8272-8fb1286ef456', N'语音合成', '0', 'system', 'active', 0, 'system', 0, 'system');

insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/audio/transcriptions', 'ep-bd4b69f0-f9b8-4175-8296-3b267bf76fb1', N'语音识别', '0', 'system', 'active', 0, 'system', 0, 'system');

insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/audio/asr/stream', 'ep-e2b95d24-8b74-4316-8cab-5943e899854d', N'流式语音识别', '0', 'system', 'active', 0, 'system', 0, 'system');

insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/audio/asr/flash', 'ep-0371270b-0b90-4bb2-a760-25a00b4f9ef6', N'一句话语音识别', '0', 'system', 'active', 0, 'system', 0, 'system');

insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/images/generations', 'ep-c1079a7b-1c29-44fa-b5d8-ef8a2bfd493d', N'文生图', '0', 'system', 'active', 0, 'system', 0, 'system');

insert into endpoint ( endpoint, endpoint_code, endpoint_name, maintainer_code, maintainer_name, status, cuid
                     , cu_name, muid, mu_name)
values ('/v1/images/edits', 'ep-ed2a031e-15e7-4199-9f93-73e1d190798e', N'图生图', '0', 'system', 'active', 0, 'system', 0, 'system');

insert into category (category_code, category_name, parent_code, status, cuid, cu_name, muid, mu_name)
values ('0001', N'语言类', '', 'active', 0, 'system', 0, 'system');

insert into category (category_code, category_name, parent_code, status, cuid, cu_name, muid, mu_name)
values ('0002', N'语音类', '', 'active', 0, 'system', 0, 'system');

insert into category (category_code, category_name, parent_code, status, cuid, cu_name, muid, mu_name)
values ('0003', N'图像类', '', 'active', 0, 'system', 0, 'system');

insert into category (category_code, category_name, parent_code, status, cuid, cu_name, muid, mu_name)
values ('0002-0001', N'语音合成', '0002', 'active', 0, 'system', 0, 'system');

insert into category (category_code, category_name, parent_code, status, cuid, cu_name, muid, mu_name)
values ('0002-0002', N'语音识别', '0002', 'active', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/chat/completions', '0001', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/embeddings', '0001', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/audio/speech', '0002-0001', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/audio/transcriptions', '0002-0002', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/audio/asr/flash', '0002-0002', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/audio/asr/stream', '0002-0002', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/images/generations', '0003', 0, 'system', 0, 'system');

insert into endpoint_category_rel (endpoint, category_code, cuid, cu_name, muid, mu_name)
values ('/v1/images/edits', '0003', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('deepseek-reasoner', 'https://api-docs.deepseek.com/zh-cn/', 'public', 'system', '0', 'system',
        '{"max_input_context":64000,"max_output_context":8192}',
        '{"json_format":false,"stream_function_call":true,"stream":true,"function_call":true,"agent_thought":true,"reason_content":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('deepseek-reasoner', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('deepseek-chat', 'https://api-docs.deepseek.com/zh-cn/', 'public', 'system', '0', 'system',
        '{"max_input_context":64000,"max_output_context":8192}',
        '{"json_format":false,"stream_function_call":true,"stream":true,"function_call":true,"parallel_tool_calls":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('deepseek-chat', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('claude-3.7-sonnet', 'https://docs.anthropic.com/en/docs/intro-to-claude', 'public', 'system', '0', 'system',
        '{"max_input_context":128000,"max_output_context":128000}',
        '{"vision":true,"json_format":false,"stream_function_call":true,"parallel_tool_calls":false,"stream":true,"function_call":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('claude-3.7-sonnet', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('claude-3.5-sonnet', 'https://docs.anthropic.com/en/docs/intro-to-claude', 'public', 'system', '0', 'system',
        '{"max_input_context":200000,"max_output_context":200000}',
        '{"vision":true,"json_format":false,"stream_function_call":true,"parallel_tool_calls":false,"stream":true,"function_call":true,"reason_content":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('claude-3.5-sonnet', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('o3', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{"max_input_context":200000,"max_output_context":100000}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":true,"vision":true,"json_format":true,"json_schema":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('o3', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('o1', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{"max_input_context":200000,"max_output_context":100000}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":true,"vision":true,"json_format":true,"json_schema":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('o1', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('gpt-4o', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{"max_input_context":128000,"max_output_context": 16384}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":true,"vision":true,"json_format":true,"json_schema":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('gpt-4o', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen-vl-max', 'https://help.aliyun.com/zh/model-studio/getting-started/models',
        'public', 'system', '0', 'system',
        '{"max_input_context":129024,"max_output_context":8192}',
        '{"vision":true,"json_format":true,"stream_function_call":true,"parallel_tool_calls":false,"stream":true,"function_call":true,"json_schema":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen-vl-max', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('Doubao-1.5-vision-pro-32k', 'https://www.volcengine.com/docs/82379/1330310', 'public', 'system', '0', 'system',
        '{"max_input_context":32000,"max_output_context":12288}',
        '{"vision":true,"json_format":true,"stream_function_call":false,"parallel_tool_calls":false,"stream":true,"function_call":false,"json_schema":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('Doubao-1.5-vision-pro-32k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen-max', 'https://help.aliyun.com/zh/model-studio/getting-started/models', 'public', 'system', '0', 'system',
        '{"max_input_context":30720,"max_output_context":8192}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen-max', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen-vl-plus', 'https://help.aliyun.com/zh/model-studio/getting-started/models',
        'public', 'system', '0', 'system',
        '{"max_input_context":129024,"max_output_context":8192}',
        '{"json_format":true,"stream_function_call":true,"vision":true,"stream":true,"parallel_tool_calls":false,"function_call":true,"json_schema":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen-vl-plus', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen-plus', 'https://help.aliyun.com/zh/model-studio/getting-started/models',
        'public', 'system', '0', 'system',
        '{"max_input_context":129024,"max_output_context":8192}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen-plus', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen-turbo', 'https://help.aliyun.com/zh/model-studio/getting-started/models',
        'public', 'system', '0', 'system',
        '{"max_input_context":1000000,"max_output_context":8192}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen-turbo', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen-long', 'https://help.aliyun.com/zh/model-studio/getting-started/models', 'public', 'system', '0', 'system',
        '{"max_input_context":10000000,"max_output_context":6000}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen-long', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('Doubao-1.5-pro-256k', 'https://www.volcengine.com/docs/82379/1330310', 'public', 'system', '0', 'system',
        '{"max_input_context":256000,"max_output_context":12288}',
        '{"json_format":true,"stream":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('Doubao-1.5-pro-256k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('Doubao-1.5-pro-32k', 'https://www.volcengine.com/docs/82379/1330310', 'public', 'system', '0', 'system',
        '{"max_input_context":32000,"max_output_context":12288}',
        '{"json_format":true,"stream_function_call":true,"parallel_tool_calls":false,"stream":true,"function_call":true,"json_schema":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('Doubao-1.5-pro-32k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('doubao-pro-256k', 'https://www.volcengine.com/docs/82379/1330310', 'public', 'system', '0', 'system',
        '{"max_input_context":256000,"max_output_context":4096}',
        '{"json_format":true,"function_call":true,"stream_function_call":false,"stream":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('doubao-pro-256k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('doubao-pro-32k', 'https://www.volcengine.com/docs/82379/1330310', 'public', 'system', '0', 'system',
        '{"max_input_context":32000,"max_output_context":4096}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('doubao-pro-32k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('moonshot-v1-128k', 'https://platform.moonshot.cn/docs', 'public', 'system', '0', 'system',
        '{"max_input_context":128000,"max_output_context":4096}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('moonshot-v1-128k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('moonshot-v1-32k', 'https://platform.moonshot.cn/docs', 'public', 'system', '0', 'system',
        '{"max_input_context":32000,"max_output_context":4096}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('moonshot-v1-32k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('moonshot-v1-8k', 'https://platform.moonshot.cn/docs', 'public', 'system', '0', 'system',
        '{"max_input_context":8000,"max_output_context":4096}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('moonshot-v1-8k', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('GLM-3-130B', 'https://open.bigmodel.cn/console/modelcenter/square', 'public', 'system', '0', 'system',
        '{"max_input_context":8000,"max_output_context":4096}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('GLM-3-130B', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('gpt-4o-mini', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{"max_input_context":128000,"max_output_context":16384}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":true,"vision":true,"json_format":true,"json_schema":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('gpt-4o-mini', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('o1-mini', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{"max_input_context":128000,"max_output_context":65536}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":true,"vision":false,"json_format":true,"json_schema":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('o1-mini', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('o3-mini', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{"max_input_context":200000,"max_output_context":100000}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":true,"vision":false,"json_format":true,"json_schema":true}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('o3-mini', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen2-72b-instruct', 'https://help.aliyun.com/zh/model-studio/getting-started/models', 'public', 'system', '0', 'system',
        '{"max_input_context":131072,"max_output_context":129024}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen2-72b-instruct', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen2.5-72b-instruct', 'https://help.aliyun.com/zh/model-studio/getting-started/models', 'public', 'system', '0', 'system',
        '{"max_input_context":131072,"max_output_context":129024}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen2.5-72b-instruct', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen2.5-32b-instruct', 'https://help.aliyun.com/zh/model-studio/getting-started/models', 'public', 'system', '0', 'system',
        '{"max_input_context":131072,"max_output_context":129024}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen2.5-32b-instruct', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen2.5-14b-instruct', 'https://help.aliyun.com/zh/model-studio/getting-started/models', 'public', 'system', '0', 'system',
        '{"max_input_context":131072,"max_output_context":129024}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen2.5-14b-instruct', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('qwen2.5-7b-instruct', 'https://help.aliyun.com/zh/model-studio/getting-started/models', 'public', 'system', '0', 'system',
        '{"max_input_context":131072,"max_output_context":129024}',
        '{"stream":true,"function_call":true,"stream_function_call":true,"parallel_tool_calls":false,"vision":false,"json_format":false}',
        0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('qwen2.5-7b-instruct', '/v1/chat/completions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('text_embedding_v1', 'https://help.aliyun.com/zh/model-studio/user-guide/embedding', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('text_embedding_v1', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('text_embedding_v2', 'https://help.aliyun.com/zh/model-studio/user-guide/embedding', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('text_embedding_v2', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('text_embedding_v3', 'https://help.aliyun.com/zh/model-studio/user-guide/embedding', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('text_embedding_v3', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('text-embedding-3-small', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('text-embedding-3-small', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('text-embedding-3-large', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('text-embedding-3-large', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('text-embedding-ada-002', 'https://platform.openai.com/docs/models', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('text-embedding-ada-002', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('doubao-embedding', 'https://www.volcengine.com/docs/82379/1330310#doubao-embedding', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('doubao-embedding', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('doubao-embedding-large', 'https://www.volcengine.com/docs/82379/1330310#doubao-embedding', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('doubao-embedding-large', '/v1/embeddings', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('tts-1', 'https://platform.openai.com/docs/models/tts-1', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('tts-1', '/v1/audio/speech', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('tts-1-hd', 'https://platform.openai.com/docs/models/tts-1-hd', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('tts-1-hd', '/v1/audio/speech', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('huoshan-tts', 'https://www.volcengine.com/docs/6561/162929', 'public', 'system', '0', 'system',
        '{}','{"stream":true}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('huoshan-tts', '/v1/audio/speech', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('whisper-1', 'https://platform.openai.com/docs/guides/speech-to-text', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('whisper-1', '/v1/audio/transcriptions', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('huoshan-flash-asr', 'https://www.volcengine.com/docs/6561/162929', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('huoshan-flash-asr', '/v1/audio/asr/flash', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('huoshan-realtime-asr', 'https://www.volcengine.com/docs/6561/162929', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('huoshan-realtime-asr', '/v1/audio/asr/stream', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('dall-e-3', 'https://platform.openai.com/docs/models/dall-e-3', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('dall-e-3', '/v1/images/generations', 0, 'system', 0, 'system');

insert into model (model_name, document_url, visibility, owner_type, owner_code, owner_name, properties, features, cuid, cu_name, muid, mu_name)
values ('dall-e-2', 'https://platform.openai.com/docs/models/dall-e-2', 'public', 'system', '0', 'system',
        '{}','{}',0, 'system', 0, 'system');

insert into model_endpoint_rel (model_name, endpoint, cuid, cu_name, muid, mu_name)
values ('dall-e-2', '/v1/images/generations', 0, 'system', 0, 'system');

insert into apikey_role(role_code, path, cuid, cu_name, muid, mu_name)
values ('low', '{"included":["/v*/**", "/console/apikey/**", "/console/userInfo"], "excluded":["/v*/apikey/create", "/v*/route/**", "/v*/log/**", "/console/apikey/quota/update", "/console/apikey/role/update"]}', 0, 'system', 0, 'system');

insert into apikey_role(role_code, path, cuid, cu_name, muid, mu_name)
values ('high', '{"included":["/v*/**", "/console/apikey/**", "/console/userInfo"], "excluded":["/v*/route/**", "/v*/log/**", "/console/apikey/quota/update", "/console/apikey/role/update"]}', 0, 'system', 0, 'system');

insert into apikey_role(role_code, path, cuid, cu_name, muid, mu_name)
values ('console', '{"included":["/v*/**", "/console/**"], "excluded":[]}', 0, 'system', 0, 'system');

insert into apikey_role(role_code, path, cuid, cu_name, muid, mu_name)
values ('all', '{"included":["/**"], "excluded":[]}', 0, 'system', 0, 'system');

