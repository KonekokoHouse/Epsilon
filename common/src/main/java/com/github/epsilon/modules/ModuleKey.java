package com.github.epsilon.modules;

import java.util.Objects;

/** Module 的稳定身份；显示名称变化不会改变配置或注册键。 */
public record ModuleKey(String ownerId, String moduleId) {

    public ModuleKey {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(moduleId, "moduleId");
        if (ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId 不能为空");
        }
        if (moduleId.isBlank()) {
            throw new IllegalArgumentException("moduleId 不能为空");
        }
    }
}
