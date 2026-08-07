package cn.lineai.mvp;

import android.content.Context;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import java.util.Collections;

final class ModelInteractionController {
    interface Host {
        boolean isStreaming();

        boolean isViewAttached();

        void showTestResult(String message);

        void render();
    }

    private final Context context;
    private final ModelStore modelRepository;
    private final ModelClient modelClient;
    private final BackgroundTaskRunner backgroundTasks;
    private final MainThreadDispatcher mainThread;
    private final Host host;

    ModelInteractionController(
            Context context,
            ModelStore modelRepository,
            ModelClient modelClient,
            BackgroundTaskRunner backgroundTasks,
            MainThreadDispatcher mainThread,
            Host host
    ) {
        this.context = context.getApplicationContext();
        this.modelRepository = modelRepository;
        this.modelClient = modelClient;
        this.backgroundTasks = backgroundTasks;
        this.mainThread = mainThread;
        this.host = host;
    }

    void quickSwitch(String modelId) {
        if (host.isStreaming()) {
            return;
        }
        modelRepository.setSelectedModelId(modelId);
        host.render();
    }

    void testModel(ModelConfig model) {
        backgroundTasks.execute("linecode-model-test", () -> {
            long startTime = System.currentTimeMillis();
            String requestHeader = buildRequestHeader(model);
            try {
                ModelCompletionResponse response = modelClient.complete(model,
                        Collections.singletonList(new UserModelMessage("Calculate 1+1 and reply with any result.")));
                long duration = System.currentTimeMillis() - startTime;
                String rawText = response.getText() == null ? "" : response.getText().trim();
                boolean hasData = rawText.length() > 0;
                String summary = context.getString(hasData
                        ? R.string.screen_model_add_test_success
                        : R.string.screen_model_add_test_success_no_data, duration);
                String message = requestHeader + "\n\n" + summary + "\n\n"
                        + context.getString(R.string.screen_model_add_test_raw_response) + "\n" + rawText;
                mainThread.post(() -> {
                    if (host.isViewAttached()) {
                        host.showTestResult(message);
                    }
                });
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mainThread.post(() -> Toast.makeText(context,
                        requestHeader + "\n" + context.getString(R.string.screen_model_add_test_error, message) + " (" + duration + "ms)",
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * 组装请求标识（模型 ID 与端点），便于确认实际请求的是选中的模型。
     */
    private String buildRequestHeader(ModelConfig model) {
        String modelId = model == null ? "" : model.getModelId();
        String baseUrl = model == null ? "" : model.getBaseUrl();
        if (modelId.length() > 0 && baseUrl.length() > 0) {
            return context.getString(R.string.screen_model_add_test_model_endpoint, modelId, baseUrl);
        }
        if (modelId.length() > 0) {
            return context.getString(R.string.screen_model_add_test_model, modelId);
        }
        if (baseUrl.length() > 0) {
            return context.getString(R.string.screen_model_add_test_endpoint, baseUrl);
        }
        return context.getString(R.string.screen_model_add_test_model_unknown);
    }
}
