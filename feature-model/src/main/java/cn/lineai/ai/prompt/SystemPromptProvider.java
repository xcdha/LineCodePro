package cn.lineai.ai.prompt;

import android.content.Context;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SettingsRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.resource.ResourceProvider;
import cn.lineai.workspace.WorkspacePaths;
import java.io.InputStream;
import java.util.HashMap;

public final class SystemPromptProvider {
    private final WorkspacePaths workspacePaths;
    private final PromptTemplateRepository promptTemplateRepository;

    public SystemPromptProvider(Context context, PromptTemplateRepository promptTemplateRepository) {
        Context appContext = context.getApplicationContext();
        this.workspacePaths = new WorkspacePaths(appContext);
        this.promptTemplateRepository = promptTemplateRepository;
    }

    public String build(String homePath) {
        return build(homePath, AiBehaviorSettings.TONE_CODING);
    }

    public String build(String homePath, String toneMode) {
        return build(homePath, toneMode, "");
    }

    public String build(String homePath, String toneMode, String learningContext) {
        return build(homePath, toneMode, learningContext, "");
    }

    public String build(String homePath, String toneMode, String learningContext, String toolsContext) {
        return build(homePath, toneMode, "", learningContext, toolsContext, null);
    }

    public String build(
            String homePath,
            String toneMode,
            String chatModeContext,
            String learningContext,
            String toolsContext
    ) {
        return build(homePath, toneMode, chatModeContext, learningContext, toolsContext, null, "");
    }

    public String build(
            String homePath,
            String toneMode,
            String chatModeContext,
            String learningContext,
            String toolsContext,
            ModelConfig model
    ) {
        return build(homePath, toneMode, chatModeContext, learningContext, toolsContext, model, "");
    }

    public String build(
            String homePath,
            String toneMode,
            String chatModeContext,
            String learningContext,
            String toolsContext,
            ModelConfig model,
            String todoStateContext
    ) {
        HashMap<String, String> values = new HashMap<>();
        values.put("TONE_CONTEXT", toneContext(toneMode));
        values.put("CHAT_MODE_CONTEXT", chatModeContext == null ? "" : chatModeContext.trim());
        values.put("WORK_DIRECTORY_CONTEXT", workDirectoryContext(homePath));
        values.put("LEARNING_CONTEXT", learningContext == null ? "" : learningContext.trim());
        values.put("TOOLS_CONTEXT", toolsContext == null ? "" : toolsContext.trim());
        values.put("MODEL_IDENTITY", modelIdentityContext(model));
        values.put("TODO_STATE", renderTodoStateContext(todoStateContext));
        return template().render(values);
    }

    private String renderTodoStateContext(String todoListText) {
        String safeList = todoListText == null ? "" : todoListText.trim();
        if (safeList.length() == 0) {
            return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_TODO_USAGE)).render(new HashMap<>());
        }
        HashMap<String, String> values = new HashMap<>();
        values.put("TODO_LIST", safeList);
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_TODO_STATE)).render(values);
    }

    private StringTemplate template() {
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_SYSTEM_PROMPT));
    }

    private String workDirectoryContext(String homePath) {
        if (homePath == null || homePath.trim().length() == 0) {
            return "";
        }
        HashMap<String, String> values = new HashMap<>();
        values.put("HOME_PATH", homePath.trim());
        values.put("LINECODE_ROOT", workspacePaths.getLinecodeRoot().getAbsolutePath());
        values.put("GLOBAL_SKILLS_ROOT", workspacePaths.getSkillsRoot().getAbsolutePath());
        values.put("WORKSPACE_PRIVATE_ROOT", WorkspacePaths.join(homePath.trim(), ".linecode"));
        values.put("WORKSPACE_SKILLS_ROOT", WorkspacePaths.join(homePath.trim(), ".linecode/skills"));
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_WORK_DIRECTORY)).render(values);
    }

    private String toneContext(String toneMode) {
        if (AiBehaviorSettings.TONE_CHAT.equals(toneMode)) {
            return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_TONE_CHAT)).render(new HashMap<>());
        }
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_TONE_CODING)).render(new HashMap<>());
    }

    private String modelIdentityContext(ModelConfig model) {
        if (model == null) {
            return "";
        }
        String modelId = safe(model.getModelId());
        if (modelId.length() == 0) {
            return "";
        }
        HashMap<String, String> values = new HashMap<>();
        values.put("MODEL_ID", modelId);
        // 品牌从 modelId 推断,作为权威身份来源;用户填的 name/providerLabel 仅为元数据。
        // providerLabel 实际存的是接口协议标签(OpenAI/Codex/Anthropic),不是真实提供商,
        // 第三方网关接入时极易误导模型自称"由 OpenAI 提供",故身份以推断品牌为准。
        values.put("MODEL_BRAND", resolveBrand(modelId));
        values.put("MODEL_NAME", safe(model.getName()));
        values.put("MODEL_PROVIDER", safe(model.getProviderLabel()));
        values.put("MODEL_PROTOCOL", protocolLabel(model.getProtocolType()));
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_MODEL_IDENTITY)).render(values);
    }

    /**
     * 从 modelId 推断模型真实品牌(提供商)。modelId 是请求时发送给上游的权威标识,
     * 不受用户填写或第三方网关改名影响,因此推断结果比用户配置的 name/provider 更可靠。
     * 使用品牌词锚点(contains)匹配,容忍第三方网关在品牌词前后插入任意字符(如 xkimi-k3)。
     * 推断失败返回空串,模板会回退到 MODEL_ID 本身作为身份。
     */
    private static String resolveBrand(String modelId) {
        if (modelId == null || modelId.length() == 0) {
            return "";
        }
        String lower = modelId.toLowerCase();
        if (lower.contains("kimi")) {
            return "Kimi (Moonshot AI)";
        }
        if (lower.contains("deepseek")) {
            return "DeepSeek";
        }
        if (lower.contains("claude")) {
            return "Claude (Anthropic)";
        }
        if (lower.contains("glm") || lower.contains("chatglm")) {
            return "GLM (Zhipu AI)";
        }
        if (lower.contains("qwen") || lower.contains("qwq")) {
            return "Qwen (Alibaba)";
        }
        if (lower.contains("gpt") || lower.contains("o1") || lower.contains("o3") || lower.contains("o4")) {
            return "OpenAI GPT";
        }
        if (lower.contains("gemini")) {
            return "Gemini (Google)";
        }
        if (lower.contains("doubao")) {
            return "Doubao (ByteDance)";
        }
        if (lower.contains("ernie") || lower.contains("wenxin")) {
            return "ERNIE (Baidu)";
        }
        if (lower.contains("spark")) {
            return "Spark (iFlytek)";
        }
        if (lower.contains("hunyuan")) {
            return "Hunyuan (Tencent)";
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String protocolLabel(ModelProtocolType type) {
        return type == null ? "" : type.getLabel();
    }
}
