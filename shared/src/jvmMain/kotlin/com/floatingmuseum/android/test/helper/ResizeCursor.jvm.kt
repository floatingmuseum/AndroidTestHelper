package com.floatingmuseum.android.test.helper

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor

actual fun Modifier.verticalResizePointerIcon(): Modifier =
    pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
