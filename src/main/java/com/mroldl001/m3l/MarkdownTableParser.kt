package com.mroldl001.m3l

internal fun markdownTableCells(line: String): List<String> {
    val trimmed = line.trim()
    val separators = mutableListOf<Int>()
    var cursor = 0
    while (cursor < trimmed.length) {
        if (trimmed[cursor] == '`' && !tableCharacterEscaped(trimmed, cursor)) {
            val ticks = trimmed.substring(cursor).takeWhile { it == '`' }.length
            var search = cursor + ticks
            var closing = -1
            while (search < trimmed.length) {
                val next = trimmed.indexOf('`', search)
                if (next < 0) break
                val length = trimmed.substring(next).takeWhile { it == '`' }.length
                if (length == ticks) {
                    closing = next + length
                    break
                }
                search = next + length
            }
            if (closing >= 0) {
                cursor = closing
                continue
            }
            cursor += ticks
            continue
        }
        if (trimmed[cursor] == '|' && !tableCharacterEscaped(trimmed, cursor)) separators += cursor
        cursor++
    }
    val cells = mutableListOf<String>()
    var start = if (separators.firstOrNull() == 0) 1 else 0
    separators.forEach { separator ->
        if (separator >= start) {
            cells += trimmed.substring(start, separator).trim()
            start = separator + 1
        }
    }
    if (start < trimmed.length || separators.lastOrNull() != trimmed.lastIndex) {
        cells += trimmed.substring(start).trim()
    }
    return cells
}

private fun tableCharacterEscaped(text: String, index: Int): Boolean {
    var cursor = index - 1
    while (cursor >= 0 && text[cursor] == '\\') cursor--
    return (index - cursor - 1) % 2 == 1
}
