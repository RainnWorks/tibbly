package co.rowm.osrsllm.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RAI-38 — runtime mirror of the Gradle grep audits in build.gradle.kts.
 *
 * Running these as plain JUnit makes the legibility guarantee visible inside
 * the unit test report a CI run produces, not just inside the build log.
 */
class SourceTreeAuditTest {

    private val productionTree: List<File> = run {
        val root = File("src/main/kotlin/co/rowm/osrsllm")
        if (!root.exists()) emptyList() else
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.path.contains("/local/") || it.path.contains("\\local\\") }
                .toList()
    }

    @Test
    fun `production source set has zero plaintext url literals`() {
        skipIfNoTree()
        val hits = productionTree.filter { f ->
            val t = f.readText()
            t.contains("http://") || t.contains("ws://")
        }
        assertTrue(
            "expected zero plaintext url literals; found:\n  " +
                hits.joinToString("\n  ") { it.relativeTo(File(".")).path },
            hits.isEmpty(),
        )
    }

    @Test
    fun `production source set has zero http server bindings`() {
        skipIfNoTree()
        val hits = productionTree.filter { f ->
            val t = f.readText()
            t.contains("ServerSocket(") || t.contains("embeddedServer(") || t.contains("Netty,")
        }
        assertTrue(
            "expected zero http-server bindings; found:\n  " +
                hits.joinToString("\n  ") { it.relativeTo(File(".")).path },
            hits.isEmpty(),
        )
    }

    @Test
    fun `production source set has zero reflection escape hatches`() {
        skipIfNoTree()
        val hits = productionTree.filter { f ->
            val t = f.readText()
            t.contains("Class.forName") || t.contains("URLClassLoader")
        }
        assertTrue(
            "expected zero reflection escape hatches; found:\n  " +
                hits.joinToString("\n  ") { it.relativeTo(File(".")).path },
            hits.isEmpty(),
        )
    }

    @Test
    fun `there is exactly one webSocket dot send call in the production tree`() {
        skipIfNoTree()
        val sendNeedle = "webSocket.send("
        val totalHits = productionTree.sumOf { f ->
            f.readText().split(sendNeedle).size - 1
        }
        assertEquals(
            "the egress write must live in exactly one place — EgressGate.egress()",
            1,
            totalHits,
        )
    }

    private fun skipIfNoTree() {
        // Lets the test run from any directory: gradle test runs in apps/plugin,
        // which finds src/main/kotlin. From a different cwd we skip silently.
        org.junit.Assume.assumeTrue(productionTree.isNotEmpty())
    }
}
