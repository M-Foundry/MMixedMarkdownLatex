package com.mroldl001.m3l

import android.os.Build
import android.widget.TextView

internal fun tintSelectionHandles(textView: TextView, color: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    textView.textSelectHandle?.mutate()?.apply { setTint(color) }?.let(textView::setTextSelectHandle)
    textView.textSelectHandleLeft?.mutate()?.apply { setTint(color) }?.let(textView::setTextSelectHandleLeft)
    textView.textSelectHandleRight?.mutate()?.apply { setTint(color) }?.let(textView::setTextSelectHandleRight)
}
