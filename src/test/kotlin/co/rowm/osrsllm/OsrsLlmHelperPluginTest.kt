package co.rowm.osrsllm

import net.runelite.client.RuneLite
import net.runelite.client.externalplugins.ExternalPluginManager

object OsrsLlmHelperPluginTest {

    @JvmStatic
    fun main(args: Array<String>) {
        ExternalPluginManager.loadBuiltin(OsrsLlmHelperPlugin::class.java)
        RuneLite.main(args)
    }
}
