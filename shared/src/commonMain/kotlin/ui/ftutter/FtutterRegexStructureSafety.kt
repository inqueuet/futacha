package com.valoser.futacha.shared.ui.ftutter

/**
 * Reject a repeated variable-length unit before timed probing reaches an uninterruptible engine.
 * This is a structural guard, not a proof that arbitrary regex syntax runs in linear time; the
 * existing measured checks still cover other expensive expressions. Classes/escapes are isolated
 * atoms; overlap checks execute only those single atoms against one code point, never the full regex.
 */
internal fun ftutterRegexHasAmbiguousNestedRepetition(pattern: String): Boolean {
    val parser = RegexStructureParser(pattern)
    val structure = parser.parse()
    // Bound our own stack as well as the regex engine's. Deep wrappers must not
    // become a way to bypass this guard or exhaust the validation worker.
    return parser.depthExceeded || structure?.hasDanger(failureAfter = false) == true
}

private sealed interface RegexStructure {
    /** A following required token/assertion can make a previously matched repetition backtrack. */
    val canFail: Boolean
    /** Saturating upper bound for a fully finite expression's branching and repetitions. */
    val finiteWork: Int
    fun unit(): RegexUnit?
    fun hasDanger(failureAfter: Boolean): Boolean
}

private const val REGEX_SMALL_FINITE_WORK = 1024
private const val REGEX_UNKNOWN_WORK = REGEX_SMALL_FINITE_WORK + 1
private fun workProduct(a: Int, b: Int): Int = (a.toLong() * b).coerceAtMost(REGEX_UNKNOWN_WORK.toLong()).toInt()
private fun workSum(a: Int, b: Int): Int = (a + b).coerceAtMost(REGEX_UNKNOWN_WORK)
private fun lengthProduct(a: Int, b: Int): Int = (a.toLong() * b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
private fun lengthSum(a: Int, b: Int): Int = (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
private fun repeatWork(body: Int, minimum: Int, maximum: Int?): Int {
    if (maximum == null || maximum > REGEX_SMALL_FINITE_WORK) return REGEX_UNKNOWN_WORK
    var power = 1
    var alternatives = if (minimum == 0) 1 else 0
    for (count in 1..maximum) {
        power = workProduct(power, body)
        if (count >= minimum) alternatives = workSum(alternatives, power)
        if (alternatives == REGEX_UNKNOWN_WORK) return alternatives
    }
    return workProduct(alternatives, maximum.coerceAtLeast(1))
}

/** A run of the same base unit; finite alternatives must preserve their different run lengths. */
private data class RegexUnit(
    val key: String, val minimum: Int, val maximum: Int?, val domain: CharacterDomain? = null,
    val hasVariableLength: Boolean = false
) {
    val variable: Boolean get() = hasVariableLength || maximum == null || minimum != maximum
}

private sealed interface CharacterDomain {
    val candidates: Set<String>
    fun accepts(text: String): Boolean
}

private fun codePointText(value: Int): String? = when {
    value !in 0..0x10FFFF -> null
    value <= 0xFFFF -> value.toChar().toString()
    else -> "${((value - 0x10000) / 1024 + 0xD800).toChar()}${((value - 0x10000) % 1024 + 0xDC00).toChar()}"
}

private val atomicEscapeCandidates = Regex("""\\(?:u([0-9a-fA-F]{4})|x\{([0-9a-fA-F]+)\}|x([0-9a-fA-F]{2}))""")
private data class AtomicCharacterDomain(val source: String, val flags: String) : CharacterDomain {
    // Only a single literal/class/character escape is compiled here, never a group or
    // quantified expression. Each input is one code point, so native ICU cannot enter
    // the unbounded whole-expression backtracking that this preflight is preventing.
    private val atom by lazy { try { Regex(if (flags.isEmpty()) source else "(?$flags:$source)") } catch (_: Exception) { null } }
    override fun accepts(text: String): Boolean = atom?.matches(text) == true
    override val candidates: Set<String> by lazy {
        buildSet {
            (0..127).forEach { add(it.toChar().toString()) }
            listOf("！", "？", "あ", "é", "β", "Ж", "中", "😀", "\u2003").forEach { add(it) }
            var at = 0
            while (at < source.length && size < 640) {
                val start = at++
                if (source[start] in '\uD800'..'\uDBFF' && source.getOrNull(at)?.let { it in '\uDC00'..'\uDFFF' } == true) at++
                add(source.substring(start, at))
            }
            val escapes = atomicEscapeCandidates.findAll(source).take(256).map { match ->
                match to match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull(16)
            }.toList()
            escapes.forEachIndexed { index, (match, value) ->
                value?.let(::codePointText)?.let(::add)
                val next = escapes.getOrNull(index + 1)
                if (next != null && value != null && value in 0xD800..0xDBFF && next.second?.let { it in 0xDC00..0xDFFF } == true && match.range.last + 1 == next.first.range.first) {
                    add("${value.toChar()}${next.second!!.toChar()}")
                }
            }
        }
    }
}
private data class LiteralCharacterDomain(val literal: String, val flags: String) : CharacterDomain {
    // The parser already established that this is one literal code point. Keep
    // that fact rather than recover it from a \Q...\E atom in another engine.
    // Numeric escapes also keep COMMENTS whitespace/# literal without quoting.
    private val atom = AtomicCharacterDomain(literal.map { "\\u${it.code.toString(16).padStart(4, '0')}" }.joinToString(""), flags)
    override val candidates: Set<String> get() = atom.candidates
    override fun accepts(text: String): Boolean {
        if (text == literal) return true
        // ASCII case folding is shared by every engine. Unicode folding still
        // uses that engine's flags (JVM inline i alone is ASCII-only).
        if ('i' in flags && literal.length == 1 && text.length == 1 &&
            literal[0].code < 128 && text[0].code < 128 && literal.equals(text, ignoreCase = true)) return true
        return atom.accepts(text)
    }
}
private class SharedCharacterDomain(private val domains: List<CharacterDomain>) : CharacterDomain {
    override val candidates: Set<String> by lazy { domains.flatMap { it.candidates }.toSet() }
    override fun accepts(text: String): Boolean = domains.all { it.accepts(text) }
}
/** Whether any two single-character alternatives accept a common candidate (each atom is tested alone). */
private fun alternativesOverlap(units: List<RegexUnit>): Boolean {
    val domains = units.mapNotNull { it.domain }.distinct()
    if (domains.size < 2) return false
    val pool = domains.flatMapTo(LinkedHashSet()) { it.candidates }
    val accepted = HashSet<String>()
    return domains.any { domain -> pool.filter(domain::accepts).any { !accepted.add(it) } }
}
private fun sharedCharacterDomain(units: List<RegexUnit>): CharacterDomain? {
    val domains = units.map { it.domain ?: return null }.distinct()
    if (domains.size == 1) return domains.single()
    val shared = SharedCharacterDomain(domains)
    return shared.takeIf { it.candidates.any(it::accepts) }
}

private data class RegexAtom(val key: String?, override val canFail: Boolean = true, val domain: CharacterDomain? = null) : RegexStructure {
    override val finiteWork: Int get() = 1
    override fun unit(): RegexUnit? = key?.let { RegexUnit(it, 1, 1, domain) }
    override fun hasDanger(failureAfter: Boolean): Boolean = false
}
private data class RegexSequence(val nodes: List<RegexStructure>) : RegexStructure {
    override val canFail: Boolean get() = nodes.any { it.canFail }
    override val finiteWork: Int = nodes.fold(1) { work, node -> workProduct(work, node.finiteWork) }
    override fun unit(): RegexUnit? {
        val units = nodes.map { it.unit() ?: return null }.filterNot { it.maximum == 0 }
        if (units.isEmpty()) return RegexUnit("empty", 0, 0)
        if (units.size == 1) return units.single()
        // Adjacent copies, overlapping classes and equivalent escaped/case-folded atoms
        // can all split the same run. Different mandatory delimiters remain outside it.
        val sameKey = units.all { it.key == units.first().key }
        val shared = if (sameKey) units.first().domain else sharedCharacterDomain(units)
        if (sameKey || shared != null) {
            val minimum = units.fold(0) { length, unit -> lengthSum(length, unit.minimum) }
            val maximum = if (units.any { it.maximum == null }) null else units.fold(0) { length, unit -> lengthSum(length, unit.maximum!!) }
            return RegexUnit(if (sameKey) units.first().key else units.joinToString(prefix = "run(", postfix = ")") { it.key }, minimum, maximum, shared, units.any { it.variable })
        }
        // No shared run was established. Compound/unknown languages still go through
        // the measured checks; absence of a witness is not a proof of disjointness.
        if (units.any { it.variable }) return null
        return RegexUnit(units.joinToString(prefix = "seq(", postfix = ")") { "${it.key}{${it.minimum}}" }, 1, 1)
    }
    override fun hasDanger(failureAfter: Boolean): Boolean {
        var followingCanFail = failureAfter
        for (node in nodes.asReversed()) {
            if (node.hasDanger(followingCanFail)) return true
            followingCanFail = followingCanFail || node.canFail
        }
        return false
    }
}
private data class RegexChoice(val branches: List<RegexStructure>) : RegexStructure {
    override val canFail: Boolean get() = branches.all { it.canFail }
    override val finiteWork: Int = branches.fold(0) { work, branch -> workSum(work, branch.finiteWork) }
    override fun unit(): RegexUnit? {
        val units = branches.map { it.unit() ?: return null }
        val nonempty = units.filterNot { it.maximum == 0 }
        if (nonempty.isEmpty()) return RegexUnit("empty", 0, 0)
        val keys = nonempty.map { it.key }.distinct().sorted()
        val key = if (keys.size == 1) keys.single() else keys.joinToString(prefix = "choice(", postfix = ")")
        val shared = sharedCharacterDomain(nonempty)
        // Two alternatives that can match the same text split one run in several ways, so
        // repeating the choice backtracks like a variable length unit (M77: (!|!)+$, ([^a-z]|\W)+$).
        val ambiguous = keys.size < nonempty.size || alternativesOverlap(nonempty)
        if (keys.size == 1 || shared != null) {
            return RegexUnit(key, units.minOf { it.minimum }, if (units.any { it.maximum == null }) null else units.maxOf { it.maximum!! }, shared, units.any { it.variable } || ambiguous)
        }
        // Different alternatives still form one base unit; their own variable runs
        // remain variable when that choice is repeated by an outer group.
        return RegexUnit(key, 1, if (units.any { it.variable }) null else 1, hasVariableLength = ambiguous)
    }
    override fun hasDanger(failureAfter: Boolean): Boolean = branches.any { it.hasDanger(failureAfter) }
}
private data class RegexGroup(
    val body: RegexStructure,
    val atomic: Boolean = false,
    val assertion: Boolean = false,
    val negativeAssertion: Boolean = false
) : RegexStructure {
    override val canFail: Boolean get() = negativeAssertion || body.canFail
    override val finiteWork: Int get() = body.finiteWork
    override fun unit(): RegexUnit? = if (atomic || assertion) null else body.unit()
    override fun hasDanger(failureAfter: Boolean): Boolean =
        body.hasDanger(if (atomic || assertion) false else failureAfter)
}
private data class RegexRepeat(
    val body: RegexStructure,
    val minimum: Int,
    val maximum: Int?,
    val possessive: Boolean
) : RegexStructure {
    override val canFail: Boolean get() = minimum > 0 && body.canFail
    override val finiteWork: Int = repeatWork(body.finiteWork, minimum, maximum)
    override fun unit(): RegexUnit? {
        if (maximum == 0) return RegexUnit("empty", 0, 0)
        if (possessive) return null
        val unit = body.unit() ?: return null
        if (unit.maximum == 0) return unit
        return RegexUnit(unit.key, lengthProduct(unit.minimum, minimum),
            if (maximum == null || unit.maximum == null) null else lengthProduct(unit.maximum, maximum), unit.domain,
            unit.variable || maximum == null || minimum != maximum)
    }
    override fun hasDanger(failureAfter: Boolean): Boolean {
        if (maximum == 0) return false // The quantified body is never evaluated.
        val repeats = maximum == null || maximum > 1
        // A later required iteration that fails backtracks the earlier ones like a following token (M77: (.*！){20}).
        val failsLater = failureAfter || (minimum > 1 && body.canFail)
        if (!possessive && repeats && failsLater && finiteWork > REGEX_SMALL_FINITE_WORK && body.unit()?.variable == true) return true
        // Possessive quantifiers cut backtracking after a successful body, but an
        // ambiguous expression followed by a failure *inside* that body is still unsafe.
        return body.hasDanger(if (possessive) false else failureAfter)
    }
}

/** Syntax is compiled by the caller first. Unknown extensions are left to the existing probes. */
private class RegexStructureParser(private val pattern: String) {
    private var at = 0
    private var extended = false
    private var flags = emptySet<Char>()
    private var valid = true
    private var depth = 0
    var depthExceeded = false
        private set

    fun parse(): RegexStructure? {
        val parsed = choice()
        return parsed.takeIf { valid && at == pattern.length }
    }

    private fun choice(): RegexStructure {
        if (++depth > 256) { depthExceeded = true; valid = false; at = pattern.length; return RegexSequence(emptyList()) }
        val branches = mutableListOf<RegexStructure>()
        while (true) {
            val nodes = mutableListOf<RegexStructure>()
            while (true) {
                skipIgnored()
                val next = pattern.getOrNull(at) ?: break
                if (next == ')' || next == '|') break
                val atoms = atom()
                if (atoms.isNotEmpty()) {
                    // Quoted text is literal, but a quantifier after \E applies to its last atom.
                    skipIgnored()
                    val last = atoms.last()
                    nodes += atoms.dropLast(1)
                    nodes += quantified(last)
                }
                if (!valid) break
            }
            branches += RegexSequence(nodes)
            if (!valid || pattern.getOrNull(at) != '|') break
            at++
        }
        depth--
        return if (branches.size == 1) branches.single() else RegexChoice(branches)
    }

    private fun atom(): List<RegexStructure> {
        val start = at
        val char = pattern[at++]
        return when (char) {
            '(' -> group()
            '[' -> {
                // 0: leading position, 1: after negation, 2: an ordinary member was seen.
                // Only one leading ^ negates; an initial ] is a literal member.
                val members = mutableListOf(0)
                while (at < pattern.length && members.isNotEmpty()) {
                    skipIgnored() // COMMENTS applies inside classes too; escaped/quoted text is handled below.
                    if (at >= pattern.length) break
                    val c = pattern[at++]
                    when {
                        c == '\\' -> {
                            escape(at - 1)
                            members[members.lastIndex] = 2
                        }
                        c == '[' -> { members[members.lastIndex] = 2; members += 0 }
                        c == ']' && members.last() == 2 -> members.removeAt(members.lastIndex)
                        c == '^' && members.last() == 0 -> members[members.lastIndex] = 1
                        else -> members[members.lastIndex] = 2
                    }
                }
                if (members.isNotEmpty()) valid = false
                listOf(characterAtom(pattern.substring(start, at)))
            }
            '\\' -> escape(start)
            '^', '$' -> listOf(RegexAtom(null))
            else -> {
                if (char in '\uD800'..'\uDBFF' && pattern.getOrNull(at)?.let { it in '\uDC00'..'\uDFFF' } == true) at++
                val text = pattern.substring(start, at)
                listOf(if (char == '.') characterAtom(text) else literalAtom(text))
            }
        }
    }

    private fun escape(start: Int): List<RegexStructure> {
        val escaped = pattern.getOrNull(at++) ?: run { valid = false; return emptyList() }
        if (escaped == 'Q') {
            val textStart = at
            val end = pattern.indexOf("\\E", at).takeIf { it >= 0 } ?: pattern.length
            at = if (end == pattern.length) end else end + 2
            val atoms = mutableListOf<RegexStructure>()
            var position = textStart
            while (position < end) {
                val startPosition = position++
                if (pattern[startPosition] in '\uD800'..'\uDBFF' && position < end && pattern[position] in '\uDC00'..'\uDFFF') position++
                atoms += literalAtom(pattern.substring(startPosition, position))
            }
            return atoms
        }
        when {
            escaped in "pPNx" && pattern.getOrNull(at) == '{' -> {
                val end = pattern.indexOf('}', at + 1)
                if (end < 0) valid = false else at = end + 1
            }
            escaped == 'u' -> {
                val first = pattern.substring(at, (at + 4).coerceAtMost(pattern.length)).toIntOrNull(16)
                at = (at + 4).coerceAtMost(pattern.length)
                // Engines treat escaped UTF-16 surrogate pairs as one regex atom too.
                if (first != null && first in 0xD800..0xDBFF && pattern.startsWith("\\u", at)) {
                    val second = pattern.substring((at + 2).coerceAtMost(pattern.length), (at + 6).coerceAtMost(pattern.length)).toIntOrNull(16)
                    if (second != null && second in 0xDC00..0xDFFF) at += 6
                }
            }
            escaped == 'U' -> at = (at + 8).coerceAtMost(pattern.length)
            escaped == 'x' -> at = (at + 2).coerceAtMost(pattern.length)
            escaped == 'c' -> at = (at + 1).coerceAtMost(pattern.length)
            escaped == '0' -> {
                val limit = if (pattern.getOrNull(at)?.let { it in '0'..'3' } == true) 3 else 2
                var digits = 0
                while (digits < limit && pattern.getOrNull(at)?.let { it in '0'..'7' } == true) { at++; digits++ }
            }
            escaped == 'k' && pattern.getOrNull(at) == '<' -> {
                val end = pattern.indexOf('>', at + 1)
                if (end < 0) valid = false else at = end + 1
            }
        }
        val variableOrAssertion = escaped in '1'..'9' || escaped in "AbBGZzk"
        return listOf(if (variableOrAssertion) RegexAtom(null) else characterAtom(pattern.substring(start, at)))
    }

    private fun characterAtom(source: String): RegexAtom {
        val active = flags.sorted().joinToString("")
        return RegexAtom("atom:$active:$source", domain = AtomicCharacterDomain(source, active))
    }
    private fun literalAtom(text: String): RegexAtom {
        val active = flags.sorted().joinToString("")
        return RegexAtom("literal:$active:$text", domain = LiteralCharacterDomain(text, active))
    }

    private fun group(): List<RegexStructure> {
        var atomic = false
        var assertion = false
        var negative = false
        val savedExtended = extended
        val savedFlags = flags
        if (pattern.getOrNull(at) == '?') {
            at++
            when (pattern.getOrNull(at)) {
                ':' -> at++
                '>' -> { atomic = true; at++ }
                '=', '!' -> { assertion = true; negative = pattern[at] == '!'; at++ }
                '<' -> {
                    at++
                    if (pattern.getOrNull(at) == '=' || pattern.getOrNull(at) == '!') {
                        assertion = true; negative = pattern[at] == '!'; at++
                    } else {
                        val end = pattern.indexOf('>', at)
                        if (end < 0) { valid = false; return emptyList() }
                        at = end + 1
                    }
                }
                else -> {
                    var removing = false
                    while (pattern.getOrNull(at)?.let { it in "idmsuxU-" } == true) {
                        val flag = pattern[at++]
                        if (flag == '-') removing = true else flags = if (removing) flags - flag else flags + flag
                        extended = 'x' in flags
                    }
                    if (pattern.getOrNull(at) == ')') { at++; return emptyList() }
                    if (pattern.getOrNull(at) != ':') { valid = false; return emptyList() }
                    at++
                }
            }
        }
        val body = choice()
        if (pattern.getOrNull(at) != ')') valid = false else at++
        extended = savedExtended
        flags = savedFlags
        return listOf(RegexGroup(body, atomic, assertion, negative))
    }

    private fun quantified(body: RegexStructure): RegexStructure {
        val next = pattern.getOrNull(at) ?: return body
        val minimum: Int
        val maximum: Int?
        when (next) {
            '+' -> { minimum = 1; maximum = null; at++ }
            '*' -> { minimum = 0; maximum = null; at++ }
            '?' -> { minimum = 0; maximum = 1; at++ }
            '{' -> {
                val start = at++
                fun number(): Int? {
                    val digits = StringBuilder()
                    while (true) {
                        skipIgnored()
                        val digit = pattern.getOrNull(at)?.takeIf { it in '0'..'9' } ?: break
                        digits.append(digit); at++
                    }
                    return digits.toString().toIntOrNull()
                }
                val lower = number()
                if (lower == null) { at = start; return body }
                minimum = lower
                skipIgnored()
                maximum = if (pattern.getOrNull(at) == ',') {
                    at++; skipIgnored()
                    if (pattern.getOrNull(at) == '}') null else number() ?: run { at = start; return body }
                } else minimum
                skipIgnored()
                if (pattern.getOrNull(at) != '}') { at = start; return body }
                at++
            }
            else -> return body
        }
        skipIgnored()
        val possessive = pattern.getOrNull(at) == '+'
        if (possessive || pattern.getOrNull(at) == '?') at++
        return RegexRepeat(body, minimum, maximum, possessive)
    }

    private fun skipIgnored() {
        if (!extended) return
        while (at < pattern.length) {
            if (pattern[at] in " \t\r\n\u000B\u000C") at++
            else if (pattern[at] == '#') { while (at < pattern.length && pattern[at] != '\n' && pattern[at] != '\r') at++ }
            else break
        }
    }
}
