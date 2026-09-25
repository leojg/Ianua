package me.lgcode.ianua.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A serializable copy of an accessibility tree, as produced by the Android debug dump. */
@Serializable
data class ScreenSnapshot(val packageName: String, val root: NodeSnapshot) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }
        fun fromJson(text: String): ScreenSnapshot = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class NodeSnapshot(
    override val viewId: String? = null,
    override val contentDescription: String? = null,
    val className: String? = null,
    val visible: Boolean = true,
    val children: List<NodeSnapshot> = emptyList(),
) : ScreenNode {
    override val isVisible: Boolean get() = visible
    override val childCount: Int get() = children.size
    override fun child(index: Int): ScreenNode? = children.getOrNull(index)

    companion object {
        /** Copies a live tree, e.g. an `AccessibilityNodeInfo` wrapper, bounded like the matcher. */
        fun copyOf(node: ScreenNode, className: (ScreenNode) -> String? = { null }, depth: Int = 0): NodeSnapshot =
            NodeSnapshot(
                viewId = node.viewId,
                contentDescription = node.contentDescription,
                className = className(node),
                visible = node.isVisible,
                children = if (depth >= AndroidMatcher.MAX_DEPTH) emptyList() else
                    (0 until node.childCount).mapNotNull { i -> node.child(i)?.let { copyOf(it, className, depth + 1) } },
            )
    }
}
