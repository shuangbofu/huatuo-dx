plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "top.fusb.huatuo.dx"
version = "0.1.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.jetbrains:annotations:24.1.0")
    intellijPlatform {
        create("IC", "2023.3")
        bundledPlugin("com.intellij.java")
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("")
    }
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
