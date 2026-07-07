package cn.lineai.ui.component.toolcall;

import cn.lineai.model.DiffUiModel;

/**
 * Diff记录加载回调，由Controller/Assembler实现，将数据层访问从View层解耦。
 */
public interface DiffLoader {
    DiffUiModel loadDiff(String diffId);
}
