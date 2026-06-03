package cn.lineai.model;

import org.json.JSONException;
import org.json.JSONObject;

public final class ModelConfig {
    public static final int DEFAULT_TOOL_CALL_LIMIT = 200;
    public static final int UNLIMITED_TOOL_CALLS = -1;

    private final String id;
    private final String name;
    private final ModelProtocolType protocolType;
    private final String providerLabel;
    private final String baseUrl;
    private final String apiKey;
    private final String modelId;
    private final int toolCallLimit;

    public ModelConfig(String id, String name, ModelProtocolType protocolType, String providerLabel, String baseUrl, String apiKey, String modelId) {
        this(id, name, protocolType, providerLabel, baseUrl, apiKey, modelId, DEFAULT_TOOL_CALL_LIMIT);
    }

    public ModelConfig(String id, String name, ModelProtocolType protocolType, String providerLabel, String baseUrl, String apiKey, String modelId, int toolCallLimit) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.protocolType = protocolType == null ? ModelProtocolType.OPENAI_COMPATIBLE : protocolType;
        this.providerLabel = providerLabel == null ? this.protocolType.getLabel() : providerLabel;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.modelId = modelId == null ? "" : modelId;
        this.toolCallLimit = normalizeToolCallLimit(toolCallLimit);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public ModelProtocolType getProtocolType() {
        return protocolType;
    }

    public String getProviderLabel() {
        return providerLabel;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModelId() {
        return modelId;
    }

    public int getToolCallLimit() {
        return toolCallLimit;
    }

    public ModelConfig withId(String nextId) {
        return new ModelConfig(nextId, name, protocolType, providerLabel, baseUrl, apiKey, modelId, toolCallLimit);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("protocolType", protocolType.name());
        object.put("providerLabel", providerLabel);
        object.put("baseUrl", baseUrl);
        object.put("apiKey", apiKey);
        object.put("modelId", modelId);
        object.put("toolCallLimit", toolCallLimit);
        return object;
    }

    public static ModelConfig fromJson(JSONObject object) {
        String protocolValue = object.optString("protocolType");
        if (protocolValue.length() == 0) {
            protocolValue = object.optString("provider");
        }
        String providerLabel = object.optString("providerLabel");
        if (providerLabel.length() == 0) {
            providerLabel = ModelProtocolType.fromStorage(protocolValue).getLabel();
        }
        String modelId = object.optString("modelId");
        JSONObject localModel = object.optJSONObject("localModel");
        if (modelId.length() == 0 && localModel != null) {
            modelId = localModel.optString("fileName", localModel.optString("localPath"));
        }
        int toolCallLimit = DEFAULT_TOOL_CALL_LIMIT;
        if (object.has("toolCallLimit")) {
            toolCallLimit = object.optInt("toolCallLimit", DEFAULT_TOOL_CALL_LIMIT);
        } else if (object.has("tool_call_limit")) {
            toolCallLimit = object.optInt("tool_call_limit", DEFAULT_TOOL_CALL_LIMIT);
        }
        return new ModelConfig(
                object.optString("id"),
                object.optString("name"),
                ModelProtocolType.fromStorage(protocolValue),
                providerLabel,
                object.optString("baseUrl"),
                object.optString("apiKey"),
                modelId,
                toolCallLimit
        );
    }

    public static int normalizeToolCallLimit(int limit) {
        if (limit == UNLIMITED_TOOL_CALLS) {
            return UNLIMITED_TOOL_CALLS;
        }
        return Math.max(0, limit);
    }
}
