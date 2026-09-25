package me.lgcode.ianua.rules

/**
 * Read-only view of one accessibility node. Android wraps `AccessibilityNodeInfo` lazily;
 * tests use [NodeSnapshot]. Keeping the matcher on this interface keeps it in common code.
 */
interface ScreenNode {
    val viewId: String?
    val contentDescription: String?
    val text: String?
    val isVisible: Boolean
    val isFocused: Boolean
    val childCount: Int
    fun child(index: Int): ScreenNode?

    /** Nodes in this subtree with [id]. Android overrides this with the platform lookup. */
    fun findByViewId(id: String): List<ScreenNode> {
        val found = mutableListOf<ScreenNode>()
        val stack = ArrayDeque<ScreenNode>().apply { addLast(this@ScreenNode) }
        var visited = 0
        while (stack.isNotEmpty() && ++visited <= AndroidMatcher.MAX_NODES) {
            val node = stack.removeLast()
            if (node.viewId == id) found += node
            for (i in node.childCount - 1 downTo 0) node.child(i)?.let(stack::addLast)
        }
        return found
    }
}

/**
 * Decides whether an Android screen is gated: a gated screen inside an app (e.g. the YouTube
 * Shorts player), or a browser whose address bar shows a gated URL (ADR-0005).
 */
class AndroidMatcher(private val rules: AndroidRules, private val web: WebMatcher? = null) {
    private val viewIds = rules.gatedScreens.map { it.anyViewId.toSet() }
    private val descriptions = rules.gatedScreens.map { it.anyContentDescription.toSet() }
    private val browsers = if (web == null) emptyMap() else rules.browsers.associate { it.packageName to it.urlBarViewIds }

    val packages: Set<String> = rules.packages.toSet() + browsers.keys

    fun isGatedScreen(packageName: String, root: ScreenNode): Boolean {
        browsers[packageName]?.let { urlBars -> return isGatedAddressBar(root, urlBars) }
        if (packageName !in rules.packages) return false
        var visited = 0
        // Iterative DFS with a node budget: accessibility trees can be large, and this runs
        // on every content change of the target app.
        val stack = ArrayDeque<Pair<ScreenNode, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            if (++visited > MAX_NODES) return false
            if (!node.isVisible) continue
            if (matches(node)) return true
            if (depth >= MAX_DEPTH) continue
            for (i in node.childCount - 1 downTo 0) {
                node.child(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
        return false
    }

    /** Only an unfocused bar counts: while focused it holds what the user is still typing. */
    private fun isGatedAddressBar(root: ScreenNode, urlBars: List<String>): Boolean {
        val bar = urlBars.asSequence()
            .flatMap { root.findByViewId(it) }
            .firstOrNull { it.isVisible } ?: return false
        if (bar.isFocused) return false
        val url = bar.text ?: return false
        return web?.gatedVideoIdInAddressBar(url) != null
    }

    private fun matches(node: ScreenNode): Boolean = viewIds.indices.any { rule ->
        node.viewId in viewIds[rule] || node.contentDescription in descriptions[rule]
    }

    companion object {
        const val MAX_NODES = 2_000
        const val MAX_DEPTH = 40
    }
}
