/*
 * 依赖解析统一走这里：优先国内镜像，回退官方仓库。
 * 镜像顺序很关键——google 仓库必须在前，AGP 与 Compose 都在那里。
 */
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google") { name = "aliyun-google" }
        maven("https://maven.aliyun.com/repository/gradle-plugin") { name = "aliyun-gradle-plugin" }
        maven("https://maven.aliyun.com/repository/central") { name = "aliyun-central" }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google") { name = "aliyun-google" }
        maven("https://maven.aliyun.com/repository/central") { name = "aliyun-central" }
        google()
        mavenCentral()
    }
}

rootProject.name = "DeepSeekNative"
include(":app")
