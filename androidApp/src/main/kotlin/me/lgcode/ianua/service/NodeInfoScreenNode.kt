package me.lgcode.ianua.service

import android.view.accessibility.AccessibilityNodeInfo
import me.lgcode.ianua.rules.ScreenNode

/** Lazy [ScreenNode] over the live tree: children are fetched only if the matcher descends. */
class NodeInfoScreenNode(val info: AccessibilityNodeInfo) : ScreenNode {
    override val viewId: String? get() = info.viewIdResourceName
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val text: String? get() = info.text?.toString()
    override val isVisible: Boolean get() = info.isVisibleToUser
    override val isFocused: Boolean get() = info.isFocused
    override val childCount: Int get() = info.childCount
    override fun child(index: Int): ScreenNode? = info.getChild(index)?.let(::NodeInfoScreenNode)

    // The platform lookup: a browser's tree includes the whole web page.
    override fun findByViewId(id: String): List<ScreenNode> =
        info.findAccessibilityNodeInfosByViewId(id).map(::NodeInfoScreenNode)
}
