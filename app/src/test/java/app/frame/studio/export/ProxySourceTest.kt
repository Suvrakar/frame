package app.frame.studio.export

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ProxySourceTest {
    @After fun reset() { CompositionFactory.proxyResolver = null }

    @Test fun previewUsesProxyForHeavyClip() {
        CompositionFactory.proxyResolver = { "file:///p/$it" }
        assertEquals("file:///p/a", CompositionFactory.sourceFor("a", needsProxy = true, forExport = false))
    }

    @Test fun exportNeverUsesProxy() {
        CompositionFactory.proxyResolver = { "file:///p/$it" }
        assertEquals("a", CompositionFactory.sourceFor("a", needsProxy = true, forExport = true))
    }

    @Test fun lightClipAndMissingProxyKeepOriginal() {
        CompositionFactory.proxyResolver = { "file:///p/$it" }
        assertEquals("a", CompositionFactory.sourceFor("a", needsProxy = false, forExport = false))
        CompositionFactory.proxyResolver = { null }
        assertEquals("a", CompositionFactory.sourceFor("a", needsProxy = true, forExport = false))
        CompositionFactory.proxyResolver = null
        assertEquals("a", CompositionFactory.sourceFor("a", needsProxy = true, forExport = false))
    }
}
