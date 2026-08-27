// buildSrc 是独立构建，默认拿不到根构建的版本目录；这里显式导入，使 KGP 版本仍只在
// gradle/libs.versions.toml 里出现一次。
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
