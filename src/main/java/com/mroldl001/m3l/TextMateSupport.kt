package com.mroldl001.m3l

import android.content.Context
import android.util.JsonReader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.textmate.grammar.Grammar
import dev.textmate.grammar.raw.GrammarReader
import dev.textmate.grammar.raw.RawGrammar
import dev.textmate.grammar.tokenize.StateStack
import dev.textmate.regex.JoniOnigLib
import dev.textmate.registry.GrammarSource
import dev.textmate.registry.Registry
import dev.textmate.theme.ResolvedStyle
import dev.textmate.theme.Theme
import dev.textmate.theme.ThemeReader
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val GRAMMAR_ASSET_DIR = "grammars"
private const val MAX_CACHED_GRAMMARS = 24
private const val MAX_CACHED_RAW = 48
private const val MAX_CACHED_THEMES = 4

internal data class CodeToken(val start: Int, val end: Int, val scopes: List<String>)

private object TextMateEngine {
    private val lock = ReentrantLock()
    private val onigLib = JoniOnigLib()
    private val rawCache = LinkedHashMap<String, RawGrammar?>(16, 0.75f, true)
    private val grammars = LinkedHashMap<String, Grammar?>(16, 0.75f, true)
    private var scopeLanguages: Map<String, String>? = null

    fun grammar(
        context: Context,
        language: String,
        provider: ((Context, String) -> String?)?
    ): Grammar? = lock.withLock {
        if (provider == null && grammars.containsKey(language)) return@withLock grammars[language]
        val appContext = context.applicationContext
        val raw = provider?.invoke(appContext, language)?.let(::parseGrammar)
            ?: rawGrammarLocked(appContext, language)
        val scope = raw?.scopeName ?: "source.$language"
        val loaded = if (raw == null) {
            null
        } else {
            try {
                Registry(grammarSource(appContext, raw, provider), onigLib).loadGrammar(scope)
            } catch (error: Throwable) {
                null
            }
        }
        if (provider != null) return@withLock loaded
        grammars[language] = loaded
        while (grammars.size > MAX_CACHED_GRAMMARS) {
            val oldest = grammars.keys.firstOrNull() ?: break
            grammars.remove(oldest)
        }
        loaded
    }

    fun tokenize(grammar: Grammar, lines: List<String>): List<List<CodeToken>> = lock.withLock {
        val perLine = ArrayList<List<CodeToken>>(lines.size)
        var state: StateStack? = null
        lines.forEach { line ->
            val result = grammar.tokenizeLine(line, state)
            state = result.ruleStack
            perLine += result.tokens.map { token ->
                CodeToken(token.startIndex, token.endIndex, token.scopes)
            }
        }
        perLine
    }

    private fun grammarSource(
        context: Context,
        raw: RawGrammar,
        provider: ((Context, String) -> String?)?
    ) = object : GrammarSource {
        private val customGrammars = mutableMapOf<String, RawGrammar?>()

        override fun loadRawGrammar(scopeName: String): RawGrammar? {
            if (scopeName == raw.scopeName) return raw
            if (customGrammars.containsKey(scopeName)) return customGrammars[scopeName]
            provider?.invoke(context, scopeName)?.let(::parseGrammar)?.let {
                customGrammars[scopeName] = it
                return it
            }
            val language = grammarLanguagesByScopeLocked(context)[scopeName]
            if (language == null) {
                customGrammars[scopeName] = null
                return null
            }
            provider?.invoke(context, language)?.let(::parseGrammar)?.let {
                customGrammars[scopeName] = it
                return it
            }
            return rawGrammarLocked(context, language)
        }
    }

    private fun parseGrammar(json: String): RawGrammar = GrammarReader.readGrammar(json)

    private fun grammarLanguagesByScopeLocked(context: Context): Map<String, String> {
        scopeLanguages?.let { return it }
        val indexed = linkedMapOf<String, String>()
        context.assets.list(GRAMMAR_ASSET_DIR).orEmpty().sorted().forEach { file ->
            if (!file.endsWith(".tmLanguage.json")) return@forEach
            try {
                JsonReader(context.assets.open("$GRAMMAR_ASSET_DIR/$file").reader(Charsets.UTF_8)).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "scopeName") {
                            indexed.putIfAbsent(reader.nextString(), file.removeSuffix(".tmLanguage.json"))
                            break
                        }
                        reader.skipValue()
                    }
                }
            } catch (error: Exception) {
                Unit
            }
        }
        return indexed.also { scopeLanguages = it }
    }

    private fun rawGrammarLocked(context: Context, language: String): RawGrammar? {
        if (rawCache.containsKey(language)) return rawCache[language]
        val parsed = try {
            context.assets.open("$GRAMMAR_ASSET_DIR/$language.tmLanguage.json").use { stream ->
                GrammarReader.readGrammar(stream)
            }
        } catch (error: Throwable) {
            null
        }
        rawCache[language] = parsed
        while (rawCache.size > MAX_CACHED_RAW) {
            val oldest = rawCache.keys.firstOrNull() ?: break
            rawCache.remove(oldest)
        }
        return parsed
    }
}

private object ThemeCache {
    private val lock = ReentrantLock()
    private val cache = LinkedHashMap<CodeBlockPalette, Theme>(4, 0.75f, true)

    fun get(palette: CodeBlockPalette): Theme = lock.withLock {
        cache[palette]?.let { return@withLock it }
        val built = try {
            buildTheme("M3L_CODE", palette)
        } catch (error: Throwable) {
            null
        } ?: return@withLock fallbackTheme(palette)
        cache[palette] = built
        while (cache.size > MAX_CACHED_THEMES) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
        built
    }

    private fun fallbackTheme(palette: CodeBlockPalette): Theme {
        val fg = themeColorHex(palette.text)
        val bg = themeColorHex(palette.background)
        val json = """{"name":"MIMO_FALLBACK","type":"dark",""" +
            """"colors":{"editor.foreground":"#$fg","editor.background":"#$bg"},""" +
            """"tokenColors":[]}"""
        return ThemeReader.readTheme(
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
        )
    }

    private fun buildTheme(key: String, palette: CodeBlockPalette): Theme {
        val rules = ArrayList<String>()
        fun rule(scope: String, color: Color, italic: Boolean) {
            val hex = themeColorHex(color)
            val style = if (italic) {
                ""","settings":{"foreground":"#$hex","fontStyle":"italic"}"""
            } else {
                ""","settings":{"foreground":"#$hex"}"""
            }
            rules += """{"scope":"$scope"$style}"""
        }
        rule("comment", palette.comment, italic = true)
        rule("string", palette.string, italic = false)
        rule("constant.numeric", palette.number, italic = false)
        rule("constant.language", palette.keyword, italic = false)
        rule("keyword", palette.keyword, italic = false)
        rule("keyword.control", palette.keyword, italic = false)
        rule("keyword.operator", palette.operator, italic = false)
        rule("storage", palette.type, italic = false)
        rule("storage.type", palette.type, italic = false)
        rule("storage.modifier", palette.keyword, italic = false)
        rule("entity.name.function", palette.function, italic = false)
        rule("entity.name.type", palette.type, italic = false)
        rule("entity.name.class", palette.type, italic = false)
        rule("entity.name.tag", palette.function, italic = false)
        rule("entity.other.attribute-name", palette.annotation, italic = false)
        rule("support.function", palette.function, italic = false)
        rule("support.type", palette.type, italic = false)
        rule("support.class", palette.type, italic = false)
        rule("variable", palette.text, italic = false)
        rule("variable.other.property", palette.property, italic = false)
        rule("variable.other.object.property", palette.property, italic = false)
        rule("support.variable.property", palette.property, italic = false)
        rule("entity.name.property", palette.property, italic = false)
        rule("meta.object-literal.key", palette.property, italic = false)
        rule("variable.language", palette.keyword, italic = false)
        rule("meta.annotation", palette.annotation, italic = false)
        rule("punctuation", palette.operator, italic = false)
        rule("invalid", palette.annotation, italic = false)
        listOf(
            "markup.bold" to "bold",
            "markup.italic" to "italic",
            "markup.underline" to "underline",
            "markup.strikethrough" to "strikethrough"
        ).forEach { (scope, fontStyle) ->
            rules += """{"scope":"$scope","settings":{"fontStyle":"$fontStyle"}}"""
        }
        val fg = themeColorHex(palette.text)
        val bg = themeColorHex(palette.background)
        val json = """{"name":"$key","type":"dark",""" +
            """"colors":{"editor.foreground":"#$fg","editor.background":"#$bg"},""" +
            """"tokenColors":[${rules.joinToString(",")}]}"""
        val bytes = ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))
        return ThemeReader.readTheme(bytes)
    }
}

internal fun codeGrammar(
    context: Context,
    language: String,
    provider: ((Context, String) -> String?)? = null
): Grammar? = TextMateEngine.grammar(context, language, provider)

internal fun codeTokens(grammar: Grammar, lines: List<String>): List<List<CodeToken>> =
    TextMateEngine.tokenize(grammar, lines)

internal fun codeTheme(palette: CodeBlockPalette): Theme = ThemeCache.get(palette)

internal fun resolveStyle(theme: Theme, scopes: List<String>): ResolvedStyle? = theme.match(scopes)

private fun themeColorHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("%06X%02X", argb and 0xFFFFFF, argb ushr 24)
}

internal fun textMateForegroundColor(foreground: Long): Int = foreground.toInt()
