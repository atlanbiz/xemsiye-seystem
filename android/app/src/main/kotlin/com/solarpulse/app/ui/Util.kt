package com.solarpulse.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Percentage change, 0 when there is no previous value (web `pctChange`). */
fun pctChange(cur: Double, prev: Double): Double = if (prev == 0.0) 0.0 else ((cur - prev) / prev) * 100

/** Initials for the avatar (web `initials`). */
fun initials(name: String): String =
    name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()

/** Copies text; returns false if the clipboard is unavailable. */
fun copyToClipboard(context: Context, label: String, text: String): Boolean = runCatching {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    true
}.getOrDefault(false)
