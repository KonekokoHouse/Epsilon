package com.github.epsilon.settings;

import java.nio.file.Path;

/** 动态 SettingHost 需要随配置 profile 切换的额外持久状态。 */
public interface ExternalConfigState {
    void loadExternalState(Path ownerDirectory);

    void saveExternalState(Path ownerDirectory);
}
