package me.lgcode.ianua.service

import android.view.accessibility.AccessibilityNodeInfo
import me.lgcode.ianua.rules.ScreenNode

/** Lazy [ScreenNode] over the live tree: children are fetched only if the matcher descends. */
class NodeInfoScreenNode(val info: AccessibilityNodeInfo) : ScreenNode {
    override val viewId: String? get() = info.viewIdResourceName
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val isVisible: Boolean get() = info.isVisibleToUser
    override val childCount: Int get() = info.childCount
    override fun child(index: Int): ScreenNode? = info.getChild(index)?.let(::NodeInfoScreenNode)
}
