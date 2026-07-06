package cn.lineai.mvp;

import cn.lineai.log.ErrorLogEntry;
import cn.lineai.log.ErrorLogRepository;
import java.util.List;

final class ErrorLogController {
    private final ErrorLogRepository repository;

    ErrorLogController(ErrorLogRepository repository) {
        this.repository = repository;
    }

    List<ErrorLogEntry> list() {
        return repository.list();
    }

    void clear() {
        repository.clear();
    }
}
