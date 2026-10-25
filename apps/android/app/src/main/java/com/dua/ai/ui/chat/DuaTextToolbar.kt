package com.dua.ai.ui.chat

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/** An extra item in the text selection menu, e.g. "Rewrite". */
class TextToolbarExtra(val label: String, val onClick: () -> Unit)

/**
 * The normal floating Cut / Copy / Paste / Select all menu for the message box, plus Dua's own items
 * ("Summarize", "Rewrite") when [extras] returns any. Uses the platform floating action mode like Compose's own toolbar.
 */
class DuaTextToolbar(private val view: View, private val extras: () -> List<TextToolbarExtra>) : TextToolbar {
    private var actionMode: ActionMode? = null
    private var rect = Rect.Zero
    private var onCopy: (() -> Unit)? = null
    private var onPaste: (() -> Unit)? = null
    private var onCut: (() -> Unit)? = null
    private var onSelectAll: (() -> Unit)? = null
    private var currentExtras: List<TextToolbarExtra> = emptyList()

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
        this.rect = rect; onCopy = onCopyRequested; onPaste = onPasteRequested; onCut = onCutRequested; onSelectAll = onSelectAllRequested
        currentExtras = extras()
        if (!view.isAttachedToWindow) return
        status = TextToolbarStatus.Shown
        val mode = actionMode
        if (mode == null) actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING) else mode.invalidate()
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    /** Fills the menu; visible for tests. */
    internal fun populate(menu: Menu) {
        menu.clear()
        var order = 0
        onCut?.let { menu.add(0, CUT, order++, android.R.string.cut).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        onCopy?.let { menu.add(0, COPY, order++, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        onPaste?.let { menu.add(0, PASTE, order++, android.R.string.paste).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS) }
        onSelectAll?.let { menu.add(0, SELECT_ALL, order++, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
        currentExtras.forEachIndexed { index, extra -> menu.add(0, EXTRA + index, order++, extra.label).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
    }

    /** Runs a menu item; visible for tests. */
    internal fun click(id: Int) {
        when (id) {
            CUT -> onCut?.invoke()
            COPY -> onCopy?.invoke()
            PASTE -> onPaste?.invoke()
            SELECT_ALL -> onSelectAll?.invoke()
            else -> currentExtras.getOrNull(id - EXTRA)?.onClick?.invoke()
        }
    }

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { populate(menu); return true }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { populate(menu); return true }
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean { click(item.itemId); mode.finish(); return true }
        override fun onDestroyActionMode(mode: ActionMode) { actionMode = null; status = TextToolbarStatus.Hidden }
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: AndroidRect) {
            outRect.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        }
    }

    companion object {
        const val CUT = 1; const val COPY = 2; const val PASTE = 3; const val SELECT_ALL = 4; const val EXTRA = 100
    }
}
