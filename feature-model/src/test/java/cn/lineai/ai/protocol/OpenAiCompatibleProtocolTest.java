package cn.lineai.ai.protocol;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class OpenAiCompatibleProtocolTest {
    @Test
    public void serializesVisionRawInputAsContentParts() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new UserModelMessage("fallback", ImageInputPayload.rawInputJson("看图", "image/jpeg", "abc123")));

        JSONArray json = new OpenAiMessageSerializer().messagesJsonForTest(messages);

        JSONObject user = json.getJSONObject(0);
        assertEquals("user", user.getString("role"));
        JSONArray content = user.getJSONArray("content");
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("看图", content.getJSONObject(0).getString("text"));
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertEquals("data:image/jpeg;base64,abc123",
                content.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test
    public void serializesToolErrorWithFailurePrefix() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new ToolModelMessage("No matching text found", "call_1", "file_edit", true));

        JSONArray json = new OpenAiMessageSerializer().messagesJsonForTest(messages);

        JSONObject tool = json.getJSONObject(0);
        assertEquals("tool", tool.getString("role"));
        assertEquals("call_1", tool.getString("tool_call_id"));
        assertEquals("Tool file_edit failed:\nNo matching text found", tool.getString("content"));
    }

    @Test
    public void serializesToolSuccessWithoutPrefix() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new ToolModelMessage("Successfully edited demo.txt", "call_1", "file_edit", false));

        JSONArray json = new OpenAiMessageSerializer().messagesJsonForTest(messages);

        JSONObject tool = json.getJSONObject(0);
        assertEquals("Successfully edited demo.txt", tool.getString("content"));
    }

    @Test
    public void streamParsesToolCallChunksWithEmptyMetadataDeltas() throws Exception {
        LocalSseServer server = new LocalSseServer(
                data(chunk(toolDelta("call_00_abc", "shell_execute", ""), ""))
                        + data(chunk(toolDelta("", "", "{"), ""))
                        + data(chunk(toolDelta("", "", "\"command\""), ""))
                        + data(chunk(toolDelta("", "", ": "), ""))
                        + data(chunk(toolDelta("", "", "\"which git\""), ""))
                        + data(chunk(toolDelta("", "", "}"), ""))
                        + data(new JSONObject()
                        .put("choices", new JSONArray().put(new JSONObject()
                                .put("index", 0)
                                .put("delta", new JSONObject())
                                .put("finish_reason", "tool_calls"))))
                        + "data: [DONE]\n\n");
        server.start();
        try {
            ModelConfig config = ModelConfig.builder(
                    "m1",
                    "OpenAI compatible",
                    ModelProtocolType.OPENAI_COMPATIBLE,
                    "OpenAI",
                    "http://127.0.0.1:" + server.port() + "/v1/chat/completions",
                    "sk-test",
                    "deepseek-v4-flash").build();
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new UserModelMessage("check tools"));

            ModelCompletionResponse response = new OpenAiCompatibleProtocol().stream(
                    config,
                    messages,
                    new NoopCallback(),
                    null,
                    new ModelRequestOptions(
                            AiBehaviorSettings.REASONING_OFF,
                            false,
                            Collections.singletonList(new DummyShellTool())
                    )
            );

            JSONObject body = new JSONObject(server.requestBody());
            assertEquals("auto", body.getString("tool_choice"));
            assertEquals("shell_execute", body.getJSONArray("tools")
                    .getJSONObject(0)
                    .getJSONObject("function")
                    .getString("name"));
            assertEquals(1, response.getToolCalls().size());
            ToolCall call = response.getToolCalls().get(0);
            assertEquals("call_00_abc", call.getId());
            assertEquals("shell_execute", call.getName());
            assertEquals("{\"command\": \"which git\"}", call.getArguments());
        } finally {
            server.close();
        }
    }

    @Test
    public void nvidiaGatewayDoesNotSendUnsupportedThinkingParameters() throws Exception {
        ModelConfig config = ModelConfig.builder(
                "nvidia-qwen",
                "NVIDIA Qwen",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "NVIDIA",
                "https://integrate.api.nvidia.com/v1",
                "sk-test",
                "qwen/qwen3-coder").build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, true)
        );

        assertFalse(body.has("enable_thinking"));
        assertFalse(body.has("thinking_budget"));
        assertFalse(body.has("preserve_thinking"));
        assertFalse(body.has("thinking"));
        assertFalse(body.has("reasoning"));
    }

    private static JSONObject chunk(JSONObject delta, String finishReason) throws Exception {
        return new JSONObject()
                .put("id", "chunk_1")
                .put("object", "chat.completion.chunk")
                .put("choices", new JSONArray().put(new JSONObject()
                        .put("index", 0)
                        .put("delta", delta)
                        .put("finish_reason", finishReason)));
    }

    private static JSONObject toolDelta(String id, String name, String arguments) throws Exception {
        return new JSONObject()
                .put("tool_calls", new JSONArray().put(new JSONObject()
                        .put("index", 0)
                        .put("id", id)
                        .put("type", "function")
                        .put("function", new JSONObject()
                                .put("name", name)
                                .put("arguments", arguments))));
    }

    private static String data(JSONObject object) {
        return "data: " + object.toString() + "\n\n";
    }

    @org.junit.Test
    public void applyTemperatureUsesConfiguredValue() throws Exception {
        // 用户在该模型的配置中设置了采样温度（如 kimi-k3 填 1），请求就使用该值
        ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                        "https://api.x.com/v1", "k", "kimi-k3")
                .temperature(1.0)
                .build();

        JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

        org.junit.Assert.assertEquals(1.0, body.optDouble("temperature", Double.NaN), 0.0);
    }

    @org.junit.Test
    public void applyTemperatureUsesConfiguredArbitraryValue() throws Exception {
        // 采样温度支持 0–2 的任意合法值
        ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                        "https://api.x.com/v1", "k", "qwen3-coder")
                .temperature(0.7)
                .build();

        JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

        org.junit.Assert.assertEquals(0.7, body.optDouble("temperature", Double.NaN), 0.0);
    }

    @org.junit.Test
    public void applyTemperatureOmitsFieldWhenUnset() throws Exception {
        // 未设置采样温度时不发送字段，让上游使用模型默认值
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                        "https://api.x.com/v1", "k", "gpt-4o")
                .build();

        JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

        org.junit.Assert.assertFalse(body.has("temperature"));
    }

    @org.junit.Test
    public void applyTemperatureUsesBuiltInTableForKimiK3WhenUnset() throws Exception {
        // kimi-k3 未设温度时,内置硬性温度表直接返回 1.0,首次即零试错
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                        "https://opencode.ai/zen/go/v1", "k", "kimi-k3").build();

        JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

        org.junit.Assert.assertEquals(1.0, body.optDouble("temperature", Double.NaN), 0.0);
    }

    @org.junit.Test
    public void applyTemperatureUsesBuiltInTableForOpenAiO3WhenUnset() throws Exception {
        // o3 未设温度时,内置硬性温度表返回 1.0
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                        "https://api.openai.com/v1", "k", "o3-mini").build();

        JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

        org.junit.Assert.assertEquals(1.0, body.optDouble("temperature", Double.NaN), 0.0);
    }

    @org.junit.Test
    public void applyTemperatureUsesRuntimeCacheWhenUnset() throws Exception {
        // 不在内置表的模型,从运行时缓存命中(模拟上游报错后学到的硬性温度)
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.put("some-future-model", true, 0.5);
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.x.com/v1", "k", "some-future-model").build();

            JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

            org.junit.Assert.assertEquals(0.5, body.optDouble("temperature", Double.NaN), 0.0);
        } finally {
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @org.junit.Test
    public void hardTemperatureOverridesUserValueForKnownHardModel() throws Exception {
        // 硬性温度模型只接受固定温度,内置表/缓存命中时直接覆盖用户填的温度,避免每次请求都试错一次。
        // kimi-k3 只接受 1.0,用户填 0.3 必然失败,故内置表命中时覆盖为 1.0(零试错)。
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://opencode.ai/zen/go/v1", "k", "kimi-k3")
                    .temperature(0.3)
                    .build();

            JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

            org.junit.Assert.assertEquals(1.0, body.optDouble("temperature", Double.NaN), 0.0);
        } finally {
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @org.junit.Test
    public void cachedHardTemperatureOverridesUserValueForLearnedModel() throws Exception {
        // 缓存命中(从上游错误中学到的硬性温度)同样覆盖用户填的温度,零试错。
        // 模拟此前未知的硬性模型 some-future-model:上游报错后缓存学到 0.5,
        // 用户填 0.7 也会被覆盖为 0.5,避免每次请求都失败一次。
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.put("some-future-model", true, 0.5);
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.x.com/v1", "k", "some-future-model")
                    .temperature(0.7)
                    .build();

            JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

            org.junit.Assert.assertEquals(0.5, body.optDouble("temperature", Double.NaN), 0.0);
        } finally {
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @org.junit.Test
    public void userTemperatureFullyEffectiveForNonHardModel() throws Exception {
        // 非硬性温度模型(绝大多数)用户填的值完全生效,不被覆盖
        ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                        "https://api.x.com/v1", "k", "qwen3-coder")
                .temperature(0.7)
                .build();

        JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

        org.junit.Assert.assertEquals(0.7, body.optDouble("temperature", Double.NaN), 0.0);
    }

    @org.junit.Test
    public void searchVariantOmitsTemperatureEvenWhenUserConfigured() throws Exception {
        // search 变体必须省略 temperature 字段,即使用户填了温度也不发送
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.openai.com/v1", "k", "gpt-5-search-api")
                    .temperature(0.7)
                    .build();

            JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config);

            org.junit.Assert.assertFalse(body.has("temperature"));
        } finally {
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @org.junit.Test
    public void gpt52NonReasoningModeAllowsUserTemperature() throws Exception {
        // gpt-5.2 非思考模式(reasoning_effort=none)允许 temperature/top_p,用户填的值完全生效
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.openai.com/v1", "k", "gpt-5.2")
                    .temperature(0.5)
                    .build();

            // 非思考模式:用户温度生效
            JSONObject body = new OpenAiCompatibleProtocol().temperatureBodyForTest(config, false);
            org.junit.Assert.assertEquals(0.5, body.optDouble("temperature", Double.NaN), 0.0);

            // 思考模式:内置表返回 1.0,覆盖用户温度
            JSONObject bodyReasoning = new OpenAiCompatibleProtocol().temperatureBodyForTest(config, true);
            org.junit.Assert.assertEquals(1.0, bodyReasoning.optDouble("temperature", Double.NaN), 0.0);
        } finally {
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @org.junit.Test
    public void gpt52MaxEffortMapsToXhigh() throws Exception {
        // gpt-5.2 支持 xhigh,项目档位 max 应映射到 xhigh(而非降级到 high)
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.openai.com/v1", "k", "gpt-5.2").build();
            JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                    config, new ModelRequestOptions(AiBehaviorSettings.REASONING_MAX, false));
            org.junit.Assert.assertEquals("xhigh", body.getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        }
    }

    @org.junit.Test
    public void gpt5MaxEffortDowngradesToHigh() throws Exception {
        // gpt-5 初代不支持 xhigh,max 应降级到 high
        OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.openai.com/v1", "k", "gpt-5").build();
            JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                    config, new ModelRequestOptions(AiBehaviorSettings.REASONING_MAX, false));
            org.junit.Assert.assertEquals("high", body.getString("reasoning_effort"));
        } finally {
            OpenAiCompatibleProtocol.ReasoningEffortCache.clearForTest();
        }
    }

    @org.junit.Test
    public void applyTemperatureDiffersByThinkingModeForKimiK26() throws Exception {
        // kimi-k2.6 未设温度时,思考模式取内置表 1.0,非思考模式取 0.6
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.moonshot.ai/v1", "k", "kimi-k2.6").build();

            JSONObject thinkingBody = new OpenAiCompatibleProtocol().temperatureBodyForTest(config, true);
            JSONObject nonThinkingBody = new OpenAiCompatibleProtocol().temperatureBodyForTest(config, false);

            org.junit.Assert.assertEquals(1.0, thinkingBody.optDouble("temperature", Double.NaN), 0.0);
            org.junit.Assert.assertEquals(0.6, nonThinkingBody.optDouble("temperature", Double.NaN), 0.0);
        } finally {
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    @org.junit.Test
    public void runtimeCacheIsIsolatedByThinkingMode() throws Exception {
        // 缓存按思考模式分别记录:同一模型思考模式缓存 1.0、非思考模式缓存 0.6,互不覆盖
        OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        OpenAiCompatibleProtocol.HardTemperatureCache.put("some-model", true, 1.0);
        OpenAiCompatibleProtocol.HardTemperatureCache.put("some-model", false, 0.6);
        try {
            ModelConfig config = ModelConfig.builder("m", "M", ModelProtocolType.OPENAI_COMPATIBLE, "p",
                            "https://api.x.com/v1", "k", "some-model").build();

            JSONObject thinkingBody = new OpenAiCompatibleProtocol().temperatureBodyForTest(config, true);
            JSONObject nonThinkingBody = new OpenAiCompatibleProtocol().temperatureBodyForTest(config, false);

            org.junit.Assert.assertEquals(1.0, thinkingBody.optDouble("temperature", Double.NaN), 0.0);
            org.junit.Assert.assertEquals(0.6, nonThinkingBody.optDouble("temperature", Double.NaN), 0.0);
        } finally {
            OpenAiCompatibleProtocol.HardTemperatureCache.clearForTest();
        }
    }

    private static final class LocalSseServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final String responseBody;
        private Thread thread;
        private String requestBody = "";

        LocalSseServer(String responseBody) throws Exception {
            serverSocket = new ServerSocket(0);
            this.responseBody = responseBody == null ? "" : responseBody;
        }

        void start() {
            thread = new Thread(() -> {
                try {
                    handle(serverSocket.accept());
                } catch (Exception ignored) {
                }
            }, "openai-compatible-test-sse-server");
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String requestBody() {
            return requestBody;
        }

        private void handle(Socket socket) throws Exception {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                reader.readLine();
                int contentLength = 0;
                String line;
                while ((line = reader.readLine()) != null && line.length() > 0) {
                    String lower = line.toLowerCase(java.util.Locale.US);
                    if (lower.startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                    }
                }
                char[] body = new char[contentLength];
                int offset = 0;
                while (offset < contentLength) {
                    int read = reader.read(body, offset, contentLength - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                requestBody = new String(body, 0, offset);
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                OutputStream output = socket.getOutputStream();
                String headers = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Content-Length: " + response.length + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";
                output.write(headers.getBytes(StandardCharsets.UTF_8));
                output.write(response);
                output.flush();
            } finally {
                socket.close();
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            if (thread != null) {
                thread.join(1000);
            }
        }
    }

    private static final class NoopCallback implements ModelStreamCallback {
        @Override
        public void onTextDelta(String delta) {
        }

        @Override
        public void onReasoningDelta(String delta) {
        }
    }

    private static final class DummyShellTool extends BaseTool {
        @Override
        public String getName() {
            return "shell_execute";
        }

        @Override
        public String getDescription() {
            return "Execute shell command.";
        }

        @Override
        public ToolCategory getCategory() {
            return ToolCategory.SYSTEM;
        }

        @Override
        public JSONObject getParameters() throws org.json.JSONException {
            return new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject()
                            .put("command", new JSONObject().put("type", "string")))
                    .put("required", new JSONArray().put("command"));
        }

        @Override
        public ToolResult execute(JSONObject input, ToolContext context) {
            return ToolResult.withReview("", getName(), "", false, "", "", "");
        }
    }
}
