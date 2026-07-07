package cn.lineai.mvp;

import cn.lineai.data.repository.PhoneControlRepository;

final class PhoneControlController {
    private final PhoneControlRepository repository;

    PhoneControlController(PhoneControlRepository repository) {
        this.repository = repository;
    }

    boolean isAccessibilityEnabled() {
        return repository.isAccessibilityEnabled();
    }

    boolean isDisclaimerAccepted() {
        return repository.isDisclaimerAccepted();
    }

    boolean isPermissionEnabled(String permissionId) {
        return repository.isPermissionEnabled(permissionId);
    }

    void setPermissionEnabled(String permissionId, boolean enabled) {
        repository.setPermissionEnabled(permissionId, enabled);
    }

    void setDisclaimerAccepted(boolean accepted) {
        repository.setDisclaimerAccepted(accepted);
    }
}
