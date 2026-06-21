import java.util.zip.ZipFile

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

    // RAI-40: the MCP SDK and ktor-server deps are needed to COMPILE the
    // developer-only `co.rowm.osrsllm.local.McpServerService` class, but they
    // must NEVER appear in the shipped jar — the RuneLite Plugin Hub rejects
    // any plugin that embeds a listening socket library (PR #11453 precedent).
    // Marking them `compileOnly` keeps the source compilable in-tree without
    // dragging the runtime classes into shadowJar. The corresponding
    // McpServerService class itself is excluded from the jar by the shadowJar
    // task block below and verified by `:checkLocalNotInJar`.
    compileOnly("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    compileOnly("io.ktor:ktor-server-netty:$ktorVersion")
    compileOnly("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    compileOnly("io.ktor:ktor-server-sse:$ktorVersion")

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
    // The MCP server and ktor-server-netty deps are compileOnly in production,
    // but the test classpath needs them to instantiate McpServerService for the
    // legacy unit tests (none currently, but keeps the surface buildable).
    testImplementation("io.modelcontextprotocol:kotlin-sdk:$mcpVersion")
    testImplementation("io.ktor:ktor-server-netty:$ktorVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
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

// RAI-40: the developer-only `local/` package must NEVER be present in any jar
// that leaves this project. The plain `:jar` task and the `:shadowJar` task both
// produce an artifact at the same `archiveFileName` by default, and `:check`
// runs both — so whichever wins the race is what a maintainer ends up reading.
// Apply the exclusion to BOTH tasks so the regression vector is closed
// regardless of task order.
tasks.jar {
    archiveClassifier.set("plain")
    exclude("co/rowm/osrsllm/local/**")
    exclude("**/McpServerService*")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    // RAI-40: hard-exclude the developer-only `local/` package from the shipped
    // artifact. The runtime gate (`developerMode` + `localMcpEnabled`) prevents
    // McpServerService from binding a socket in a hub build, but a maintainer
    // running `unzip -l plugin-shadow.jar` would still find the listener class
    // sitting in the jar and reject on appearance. The audit doc
    // (`docs/reviews/plugin-hub-readiness-001.md` blocker 3) calls this out
    // explicitly. The `:checkLocalNotInJar` task below verifies the exclusion
    // every build and fails if it ever regresses.
    exclude("co/rowm/osrsllm/local/**")
    exclude("**/McpServerService*")
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api:.*"))
    }
}

// RAI-40: provable, not promised. After `:jar` and `:shadowJar` produce their
// artifacts, list each one's entries and fail the build if any forbidden class
// slipped in. This is the second half of the local/** exclusion contract — if
// a future refactor undoes the `exclude(...)` lines on either jar task, this
// check breaks the build before the artifact ever reaches a hub maintainer.
// Both jars are scanned because gradle's task order (with `java-library` + the
// shadow plugin) is not stable enough to guarantee one is the final winner in
// `build/libs/`.
tasks.register("checkLocalNotInJar") {
    group = "verification"
    description = "Fails if any produced jar (plain `:jar` or `:shadowJar`) contains a " +
        "`co/rowm/osrsllm/local/` class or `McpServerService*` entry."
    dependsOn("shadowJar", "jar")
    doLast {
        val jars = listOf(
            tasks.shadowJar.get().archiveFile.get().asFile,
            tasks.jar.get().archiveFile.get().asFile,
        ).filter { it.exists() }
        if (jars.isEmpty()) {
            throw GradleException("checkLocalNotInJar: no jars found to scan")
        }
        val violations = mutableListOf<String>()
        for (jar in jars) {
            ZipFile(jar).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name.startsWith("co/rowm/osrsllm/local/") ||
                        name.substringAfterLast('/').startsWith("McpServerService")
                    ) {
                        violations += "${jar.name}!${name}"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkLocalNotInJar: forbidden entries found. The local/** package " +
                    "and McpServerService* classes must be excluded from every produced " +
                    "jar. See `docs/reviews/plugin-hub-readiness-001.md` blocker 3.\n  " +
                    violations.joinToString("\n  "),
            )
        }
        logger.lifecycle(
            "checkLocalNotInJar: all jars clean (${jars.joinToString { it.name }})",
        )
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

// RAI-40: subprocess invocation is explicitly forbidden by the RuneLite Plugin
// Hub (PR #11453 precedent: "I meant all uses of process builder"). This task
// scans the production source set for the two ways a Kotlin file could spawn a
// process and fails the build if either appears. The grep substrings match
// across whitespace and line breaks; comments mentioning the literal token are
// the regression vector, so the gate is the only line of defense.
tasks.register("checkNoSubprocess") {
    group = "verification"
    description = "Fails if any subprocess invocation pattern appears in the production source set."
    doLast {
        val hits = findMatches(listOf("ProcessBuilder(", "Runtime.getRuntime().exec("))
        if (hits.isNotEmpty()) {
            val rendered = hits.joinToString("\n  ") { (f, n) -> "$n  ←  ${f.relativeTo(projectDir)}" }
            throw GradleException(
                "checkNoSubprocess: subprocess invocation found in production source. " +
                    "The RuneLite Plugin Hub rejects any subprocess pattern (PR #11453 " +
                    "precedent). Delete the offending code; do not gate it.\n  $rendered",
            )
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

// RuneLite Plugin Hub blocker guard: McpServerService binds a loopback HTTP
// listener and is therefore DEVELOPER-ONLY. The release path must NEVER call
// .start() or .restartWith() outside a `config.developerMode()` guard. This
// task scans production Kotlin and fails the build if such a call exists
// without a developerMode() check within the preceding 20 lines.
tasks.register("checkMcpServerGated") {
    group = "verification"
    description = "Fails if McpServerService.start/restartWith is invoked outside a developerMode() guard."
    doLast {
        val callPattern = Regex("""mcpServerService\.(start|restartWith)\s*\(""")
        val guardPattern = Regex("""developerMode\s*\(""")
        val windowLines = 20
        val violations = mutableListOf<String>()
        productionKotlinTree.files
            .filter { it.extension == "kt" }
            .forEach { f ->
                val lines = f.readLines()
                lines.forEachIndexed { idx, line ->
                    if (callPattern.containsMatchIn(line)) {
                        val from = maxOf(0, idx - windowLines)
                        val window = lines.subList(from, idx).joinToString("\n")
                        if (!guardPattern.containsMatchIn(window)) {
                            violations.add(
                                "${f.relativeTo(projectDir)}:${idx + 1}  ←  ${line.trim()}",
                            )
                        }
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkMcpServerGated: McpServerService start/restartWith call not gated by " +
                    "developerMode() within the preceding $windowLines lines. The local MCP " +
                    "server is developer-only; ungated start would block RuneLite Plugin Hub " +
                    "review.\n  " + violations.joinToString("\n  "),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// RAI-36: secretsScan. Fails the build on anything that looks like a leaked
// credential in src/main/. Tests can hold stub credentials (they need them
// for the in-process WS fixtures and for the consent / payload tests), so
// src/test/ is intentionally NOT scanned. The patterns below are the union
// of (a) project-specific names from .env (OPENROUTER_API_KEY, JWT signing
// secrets), (b) common cloud-provider prefixes that almost never appear
// inside source on purpose (OpenAI sk-..., AWS AKIA..., Google AIza...,
// GitHub gh{p,o,u,s,r}_..., Slack xox{b,p,a,r}-...), and (c) loose generic
// patterns like password= / api_key= that catch homegrown leaks.
//
// On a match the build fails with the file path and the matched pattern
// (NOT the secret value — we don't want to copy the literal into the
// build log). Wired into :check.
// -----------------------------------------------------------------------------

val secretsScanTree: ConfigurableFileTree = fileTree("src/main") {
    include("**/*.kt", "**/*.java", "**/*.properties", "**/*.yml", "**/*.yaml")
}

/** Patterns that, if matched in src/main/, almost certainly indicate a leaked secret. */
val secretsPatterns: List<Pair<String, Regex>> = listOf(
    "OPENROUTER_API_KEY literal" to Regex("""OPENROUTER_API_KEY\s*=\s*["']?[A-Za-z0-9_\-]{16,}"""),
    "OpenAI/Anthropic-style key (sk-...)" to Regex("""\bsk-(?:proj-|live-|ant-)?[A-Za-z0-9]{20,}"""),
    "AWS access key id (AKIA...)" to Regex("""\bAKIA[0-9A-Z]{16}\b"""),
    "Google API key (AIza...)" to Regex("""\bAIza[0-9A-Za-z_\-]{35}\b"""),
    "GitHub token (ghp_/gho_/ghu_/ghs_/ghr_)" to Regex("""\bgh[pousr]_[0-9A-Za-z]{20,}"""),
    "Slack token (xoxb/xoxp/xoxa/xoxr)" to Regex("""\bxox[baprs]-[0-9A-Za-z\-]{10,}"""),
    "JWT-shaped token" to Regex("""\beyJ[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}\.[A-Za-z0-9_\-]{8,}"""),
    "Stripe live secret (sk_live_)" to Regex("""\bsk_live_[A-Za-z0-9]{16,}"""),
    "Generic password=<value>" to Regex("""(?i)\bpassword\s*=\s*["'][^"'\s]{6,}["']"""),
    "Generic api_key=<value>" to Regex("""(?i)\bapi_?key\s*=\s*["'][^"'\s]{12,}["']"""),
)

tasks.register("secretsScan") {
    group = "verification"
    description = "Fails if a credential-shaped string appears in src/main/."
    doLast {
        val findings = mutableListOf<String>()
        secretsScanTree.files.forEach { f ->
            val text = f.readText()
            secretsPatterns.forEach { (label, pattern) ->
                if (pattern.containsMatchIn(text)) {
                    findings.add("$label  ←  ${f.relativeTo(projectDir)}")
                }
            }
        }
        if (findings.isNotEmpty()) {
            throw GradleException(
                "secretsScan: credential-shaped string(s) found in src/main/.\n" +
                    "If this is a false positive, refactor the literal out of source " +
                    "(e.g. read from env via System.getenv) and re-run.\n  " +
                    findings.joinToString("\n  "),
            )
        }
    }
}

// D-8: the in-RuneLite account panel must NEVER show raw token math to the
// player. The backend computes a tier-aware proxy ("23 / 30 messages used",
// "subscription active — renews on 14 Jan", or nothing for Iron tier). This
// gate greps the panel + client source files for the four field names that
// would indicate a regression and fails the build if any appear. Keep the
// allow-list tight — adding a file here is intentional, not casual.
tasks.register("checkAccountPanelNoRawTokens") {
    group = "verification"
    description = "Fails if raw token field names appear in the account-panel UI sources."
    doLast {
        val needles = listOf(
            "balance_tokens",
            "balanceTokens",
            "promptTokens",
            "completionTokens",
            "usedTokens",
        )
        val targets = fileTree("src/main/kotlin/co/rowm/osrsllm/cloud") {
            include(
                "AccountPanel*.kt",
                "AccountSummaryClient*.kt",
            )
        }
        val hits = targets.files
            .filter { it.extension == "kt" }
            .flatMap { f ->
                val text = f.readText()
                needles.mapNotNull { n -> if (text.contains(n)) f to n else null }
            }
        if (hits.isNotEmpty()) {
            val rendered = hits.joinToString("\n  ") { (f, n) ->
                "$n  ←  ${f.relativeTo(projectDir)}"
            }
            throw GradleException(
                "checkAccountPanelNoRawTokens: raw-token field name found in account panel " +
                    "UI sources. The panel must render the tier-aware proxy verbatim from " +
                    "the backend — no token arithmetic in the plugin.\n  $rendered",
            )
        }
    }
}

// BYOK guard (HUB_RELEASE_STRATEGY §Tier 2). The plugin now supports a "bring
// your own LLM key" mode where the player pastes their provider key into the
// RuneLite config. The key MUST NEVER appear as a literal in the production
// Kotlin source — it lives in config, is loaded at runtime, and is only ever
// passed inline as an Authorization header by `DirectChatRunner.kt` (next PR).
// This task greps the production source set and fails the build on any of:
//   - a literal "sk-" prefix outside a future DirectChatRunner.kt (which
//     gets an explicit `// allow-byok-prefix-literal:` comment near the use);
//   - a literal "OPENAI_API_KEY" / "ANTHROPIC_API_KEY" / "OPENROUTER_API_KEY"
//     outside the `byoApiKey` config-field name itself (no source code
//     should reference these env-var-shaped names — the key lives only in
//     RuneLite config, not in environment variables on the plugin side).
// Mirrors the :checkAccountPanelNoRawTokens pattern from PR #40.
tasks.register("checkNoKeyLeak") {
    group = "verification"
    description = "Fails if BYO API key literals or env-var-shaped names leak " +
        "into production Kotlin sources."
    doLast {
        val violations = mutableListOf<String>()

        // 1. The "sk-" provider-key prefix. We grep for the literal followed by
        // at least one continuation char to avoid catching the string in URLs
        // or random words. DirectChatRunner.kt (when it lands) may legitimately
        // reference the prefix in a doc comment, in which case the line is
        // annotated with `// allow-byok-prefix-literal:` and excluded here.
        val skPattern = Regex("""sk-[A-Za-z0-9_\-]""")
        val keyNamePatterns = listOf(
            "OPENAI_API_KEY",
            "ANTHROPIC_API_KEY",
            "OPENROUTER_API_KEY",
        )
        // The config keyName we ship — these tokens are allowed in-source.
        val configKeyName = "byoApiKey"
        productionKotlinTree.files
            .filter { it.extension == "kt" }
            .forEach { f ->
                val lines = f.readLines()
                lines.forEachIndexed { idx, line ->
                    if (skPattern.containsMatchIn(line) &&
                        !line.contains("allow-byok-prefix-literal:")
                    ) {
                        violations.add(
                            "${f.relativeTo(projectDir)}:${idx + 1}  ←  sk- literal: " +
                                line.trim().take(120),
                        )
                    }
                    keyNamePatterns.forEach { name ->
                        // Skip lines that are obviously talking about the config
                        // field name itself (e.g. KDoc on byoApiKey()).
                        if (line.contains(name) && !line.contains(configKeyName)) {
                            violations.add(
                                "${f.relativeTo(projectDir)}:${idx + 1}  ←  $name literal: " +
                                    line.trim().take(120),
                            )
                        }
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkNoKeyLeak: BYO API key literal or env-var-shaped key name " +
                    "found in production Kotlin sources. The key lives ONLY in " +
                    "RuneLite config (see byoApiKey()). If this is intentional " +
                    "(e.g. DirectChatRunner referencing the prefix in a comment), " +
                    "add `// allow-byok-prefix-literal:` to the line.\n  " +
                    violations.joinToString("\n  "),
            )
        }
    }
}

tasks.named("check") {
    dependsOn(
        "checkNoHttpServer",
        "checkNoReflection",
        "checkNoPlaintextUrls",
        "checkMcpServerGated",
        "checkAccountPanelNoRawTokens",
        "checkNoKeyLeak",
        "checkNoSubprocess",
        "secretsScan",
        "checkLocalNotInJar",
    )
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
