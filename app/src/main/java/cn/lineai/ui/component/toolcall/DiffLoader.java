package cn.lineai.ui.component.toolcall;

import cn.lineai.data.repository.DiffRecord;

/**
 * Diff记录加载回调，由Controller/Assembler实现，将数据层访问从View层解耦。
 */
public interface DiffLoader {
    DiffRecord loadDiff(String diffId);
}
