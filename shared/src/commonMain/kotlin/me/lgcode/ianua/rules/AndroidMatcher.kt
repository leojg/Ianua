package me.lgcode.ianua.rules

/**
 * Read-only view of one accessibility node. Android wraps `AccessibilityNodeInfo` lazily;
 * tests use [NodeSnapshot]. Keeping the matcher on this interface keeps it in common code.
 */
interface ScreenNode {
    val viewId: String?
    val contentDescription: String?
    val isVisible: Boolean
    val childCount: Int
    fun child(index: Int): ScreenNode?
}

class AndroidMatcher(private val rules: AndroidRules) {
    private val viewIds = rules.gatedScreens.map { it.anyViewId.toSet() }
    private val descriptions = rules.gatedScreens.map { it.anyContentDescription.toSet() }

    val packages: Set<String> = rules.packages.toSet()

    fun isGatedScreen(packageName: String, root: ScreenNode): Boolean {
        if (packageName !in packages) return false
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

    private fun matches(node: ScreenNode): Boolean = viewIds.indices.any { rule ->
        node.viewId in viewIds[rule] || node.contentDescription in descriptions[rule]
    }

    companion object {
        const val MAX_NODES = 2_000
        const val MAX_DEPTH = 40
    }
}
