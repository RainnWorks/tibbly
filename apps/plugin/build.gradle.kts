plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("com.gradleup.shadow") version "8.3.5"
    `java-library`
}

group = "co.rowm.osrsllm"
version = "0.1.0"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.runelite.net") }
}

configurations.all {
    resolutionStrategy {
        force("org.slf4j:slf4j-api:1.7.36")
    }
}

val mcpVersion = "0.12.0"
val ktorVersion = "3.0.3"
val runeLiteVersion = "latest.release"

dependencies {
    compileOnly("net.runelite:client:$runeLiteVersion")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    compileOnly("ch.qos.logback:logback-classic:1.2.9")

    implementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-sse:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    // RAI-38: WSS transport for the production cloud-chat egress (see cloud/EgressGate.kt).
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.commonmark:commonmark:0.22.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.22.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.22.0")

    testImplementation("net.runelite:client:$runeLiteVersion")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    // RAI-22: in-process Ktor WS server for CloudChatRunner round-trip tests.
    // Test scope only — `:checkNoHttpServer` excludes test sources by design.
    testImplementation("io.ktor:ktor-server-websockets:$ktorVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(11)
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }
}

tasks.register<Exec>("refreshQuestData") {
    group = "data"
    description = "Re-scrape OSRS wiki quest walkthroughs into " +
        "src/main/resources/quest-data.{json,sqlite}. Takes ~2 minutes."
    workingDir = projectDir
    commandLine("python3", "scripts/build_quest_data.py")
}

tasks.register<Exec>("refreshItemCategories") {
    group = "data"
    description = "Re-scrape OSRS wiki Category:Items subcategories into " +
        "src/main/resources/item-categories.{json,sqlite}. Takes a few minutes."
    workingDir = projectDir
    commandLine("python3", "scripts/build_item_categories.py")
}

tasks.register("refreshCollisionMap") {
    group = "data"
    description = "Re-download collision-map.zip from shortest-path's GitHub master. " +
        "Updates src/main/resources/collision-map.zip (commit the result)."
    doLast {
        val urlStr = "https://raw.githubusercontent.com/Skretzo/shortest-path/master/" +
            "src/main/resources/collision-map.zip"
        val output = file("src/main/resources/collision-map.zip")
        output.parentFile.mkdirs()
        println("Downloading $urlStr")
        // Use Gradle's resources DSL — handles HTTPS + redirects without manual URLConnection.
        ant.invokeMethod("get", mapOf(
            "src" to urlStr,
            "dest" to output,
            "verbose" to true,
        ))
        println("Wrote ${output.length() / 1024} KB → $output")
    }
}

tasks.register<JavaExec>("refreshTransportDb") {
    group = "data"
    description = "Re-import shortest-path TSVs in src/main/resources/transports/ into transports.json"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("co.rowm.osrsllm.transport.TransportImporter")
    args = listOf(projectDir.absolutePath)
}

tasks.register<JavaExec>("refreshPoiDb") {
    group = "data"
    description = "Merge POI data sources in src/main/resources/poi-data/ into pois.json"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("co.rowm.osrsllm.poi.PoiImporter")
    args = listOf(projectDir.absolutePath)
}

// -----------------------------------------------------------------------------
// RAI-38: legibility-enforcement gates. Each task greps the production source
// set and fails the build on regression. The local/ package is debug-only and
// is deliberately excluded; see SECURITY_DESIGN.md.
// -----------------------------------------------------------------------------

val productionKotlinTree: ConfigurableFileTree = fileTree("src/main/kotlin") {
    exclude("co/rowm/osrsllm/local/**")
}

/** Find files whose text contains any of [needles]. */
fun findMatches(needles: List<String>): List<Pair<File, String>> =
    productionKotlinTree.files
        .filter { it.extension == "kt" }
        .flatMap { f ->
            val text = f.readText()
            needles.mapNotNull { n -> if (text.contains(n)) f to n else null }
        }

tasks.register("checkNoHttpServer") {
    group = "verification"
    description = "Fails if any HTTP/Netty server-binding pattern appears in the production source set."
    doLast {
        val hits = findMatches(listOf("ServerSocket(", "embeddedServer(", "Netty,"))
        if (hits.isNotEmpty()) {
            val rendered = hits.joinToString("\n  ") { (f, n) -> "$n  ←  ${f.relativeTo(projectDir)}" }
            throw GradleException("checkNoHttpServer: forbidden server-binding pattern found:\n  $rendered")
        }
    }
}

tasks.register("checkNoReflection") {
    group = "verification"
    description = "Fails if reflection/classloader escape hatches appear in the production source set."
    doLast {
        val hits = findMatches(listOf("Class.forName", "URLClassLoader"))
        if (hits.isNotEmpty()) {
            val rendered = hits.joinToString("\n  ") { (f, n) -> "$n  ←  ${f.relativeTo(projectDir)}" }
            throw GradleException("checkNoReflection: forbidden reflection pattern found:\n  $rendered")
        }
    }
}

tasks.register("checkNoPlaintextUrls") {
    group = "verification"
    description = "Fails if a plaintext http:// or ws:// literal appears in the production source set."
    doLast {
        val hits = findMatches(listOf("http://", "ws://"))
        if (hits.isNotEmpty()) {
            val rendered = hits.joinToString("\n  ") { (f, n) -> "$n  ←  ${f.relativeTo(projectDir)}" }
            throw GradleException("checkNoPlaintextUrls: plaintext URL literal found:\n  $rendered")
        }
    }
}

tasks.named("check") {
    dependsOn("checkNoHttpServer", "checkNoReflection", "checkNoPlaintextUrls")
}

tasks.register<JavaExec>("runRuneLite") {
    group = "application"
    description = "Run RuneLite with this plugin in developer mode (JDWP on :5005 for HotSwap)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("co.rowm.osrsllm.OsrsLlmHelperPluginTest")
    jvmArgs = listOf(
        "-ea",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
        // JBR-only flag — ignored on stock OpenJDK, enables add/remove methods & fields on HotSwap.
        "-XX:+AllowEnhancedClassRedefinition",
        "-XX:+IgnoreUnrecognizedVMOptions",
    )
    args = listOf("--developer-mode", "--debug")
}
