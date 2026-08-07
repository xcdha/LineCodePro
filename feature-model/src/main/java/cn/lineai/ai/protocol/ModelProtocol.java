package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.ModelConfig;
import java.util.List;

public interface ModelProtocol {
    ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException;

    ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException;

    default boolean supportsNativeTools(ModelConfig model) {
        return false;
    }

    default boolean supportsDedicatedCompression() {
        return false;
    }

    default boolean supportsImageGeneration() {
        return false;
    }

    default boolean supportsImageUnderstanding() {
        return true;
    }

    /**
     * 探测模型对 temperature 的硬性要求；返回 {@code null} 表示无硬性限制（或不支持探测）。
     * 实现应当优先使用静态推断，避免发起无谓请求。
     */
    default Double probeRequiredTemperature(ModelConfig config) throws ModelCompletionException {
        return null;
    }
}
