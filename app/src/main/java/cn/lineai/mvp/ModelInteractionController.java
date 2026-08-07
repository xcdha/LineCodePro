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

        /**
         * 探测到模型硬性温度后回调，供表单自动回填。
         */
        default void onRequiredTemperatureDetected(double temperature) {
        }

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

    private void probeRequiredTemperature(ModelConfig model) {
        if (model == null || model.getRequiredTemperature() != ModelConfig.REQUIRED_TEMPERATURE_UNSET) {
            return;
        }
        backgroundTasks.execute("linecode-model-probe-temperature", () -> {
            try {
                Double detected = modelClient.probeRequiredTemperature(model);
                if (detected != null) {
                    mainThread.post(() -> host.onRequiredTemperatureDetected(detected));
                }
            } catch (Exception ignored) {
                // 探测失败不打扰用户，协议层已有错误驱动重试兜底
            }
        });
    }

    void testModel(ModelConfig model) {
        backgroundTasks.execute("linecode-model-test", () -> {
            long startTime = System.currentTimeMillis();
            try {
                ModelCompletionResponse response = modelClient.complete(model,
                        Collections.singletonList(new UserModelMessage("Calculate 1+1 and reply with any result.")));
                long duration = System.currentTimeMillis() - startTime;
                String rawText = response.getText() == null ? "" : response.getText().trim();
                boolean hasData = rawText.length() > 0;
                String summary = context.getString(hasData
                        ? R.string.screen_model_add_test_success
                        : R.string.screen_model_add_test_success_no_data, duration);
                String message = summary + "\n\n" + context.getString(R.string.screen_model_add_test_raw_response) + "\n" + rawText;
                mainThread.post(() -> {
                    if (host.isViewAttached()) {
                        host.showTestResult(message);
                    }
                });
                probeRequiredTemperature(model);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                mainThread.post(() -> Toast.makeText(context,
                        context.getString(R.string.screen_model_add_test_error, message) + " (" + duration + "ms)",
                        Toast.LENGTH_LONG).show());
            }
        });
    }
}
