package dev.revtools.updater

import dev.revtools.updater.asm.ClassEntry
import dev.revtools.updater.asm.ClassEnv
import dev.revtools.updater.asm.FieldEntry
import dev.revtools.updater.asm.MethodEntry
import dev.revtools.updater.classifier.ClassClassifier
import dev.revtools.updater.classifier.ClassifierUtil
import dev.revtools.updater.classifier.MethodClassifier
import dev.revtools.updater.classifier.RankResult
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

object Updater {

    val env = ClassEnv()

    fun init(jarA: File, jarB: File) {
        env.init(jarA, jarB)
        matchUnobfuscated()

        // Init all classifiers
        ClassClassifier.init()
        MethodClassifier.init()
    }

    fun run() {
        println("Starting matching.")

        /*
         * Recursively run matching until we no longer match anything new.
         */
        if(autoMatchClasses()) {
            autoMatchClasses()
        }

        var matchedAny: Boolean
        var matchedClassesBefore = true

        do {
            matchedAny = autoMatchMemberMethods()

            if(!matchedAny && !matchedClassesBefore) {
                break
            }

            matchedClassesBefore =  autoMatchClasses()
            matchedAny = matchedAny or matchedClassesBefore
        } while(matchedAny)

        println("Completed matching.")
    }

    private fun matchUnobfuscated() {
        env.groupA.classes.forEach { clsA ->
            if(!clsA.name.isObfuscatedName()) {
                val clsB = env.groupB.getClass(clsA.name)
                if(clsB != null && !clsB.name.isObfuscatedName()) {
                    match(clsA, clsB)
                }
            }
        }
    }

    fun match(a: ClassEntry, b: ClassEntry) {
        if(a.match == b) return
        if(a.hasMatch()) {
            a.match!!.match = null
            unmatchMembers(a)
        }
        if(b.hasMatch()) {
            b.match!!.match = null
            unmatchMembers(b)
        }
        a.match = b
        b.match = a

        if(a.isArray()) {
            if(!a.elementClass!!.hasMatch()) match(a.elementClass!!, b.elementClass!!)
        } else {
            a.arrayClasses.forEach { arrayA ->
                for(arrayB in b.arrayClasses) {
                    if(arrayB.hasMatch() || arrayB.dims != arrayA.dims) continue
                    match(arrayA, arrayB)
                    break
                }
            }
        }

        a.methods.forEach { methodA ->
            if(!methodA.name.isObfuscatedName()) {
                val methodB = b.getMethod(methodA.name, methodA.desc)
                if(methodB != null && !methodB.name.isObfuscatedName()) {
                    match(methodA, methodB)
                }
            }
        }

        a.fields.forEach { fieldA ->
            if(!fieldA.name.isObfuscatedName()) {
                val fieldB = b.getField(fieldA.name, fieldA.desc)
                if(fieldB != null && !fieldB.name.isObfuscatedName()) {
                    match(fieldA, fieldB)
                }
            }
        }

        println("Matched Class: $a -> $b")
    }

    fun match(a: MethodEntry, b: MethodEntry) {
        if(!a.isStatic() || !b.isStatic()) {
            if(a.cls.match != b.cls) throw IllegalArgumentException()
        }
        if(a.match == b) return
        if(a.hasMatch()) a.match!!.match = null
        if(b.hasMatch()) b.match!!.match = null
        a.match = b
        b.match = a

        println("Matched Method: $a -> $b")
    }

    fun match(a: FieldEntry, b: FieldEntry) {
        if(!a.isStatic() || !b.isStatic()) {
            if(a.cls.match != b.cls) throw IllegalArgumentException()
        }
        if(a.match == b) return
        if(a.hasMatch()) a.match!!.match = null
        if(b.hasMatch()) b.match!!.match = null
        a.match = b
        b.match = a

        println("Matched Field: $a -> $b")
    }

    fun unmatchMembers(cls: ClassEntry) {
        cls.memberMethods.forEach { method ->
            if(method.hasMatch()) {
                method.match!!.match = null
                method.match = null
            }
        }

        cls.memberFields.forEach { field ->
            if(field.hasMatch()) {
                field.match!!.match = null
                field.match = null
            }
        }
    }

    fun autoMatchClasses(): Boolean {
        val filter = { cls: ClassEntry -> !cls.hasMatch() }
        val srcClasses = env.groupA.classes.filter(filter)
        val dstClasses = env.groupB.classes.filter(filter)

        val maxScore = ClassClassifier.maxScore
        val maxMismatch = maxScore - getRawScore(classAbsThreshold * (1 - classRelThreshold), maxScore)
        val matches = ConcurrentHashMap<ClassEntry, ClassEntry>()

        val progress = ProgressBarBuilder()
            .setTaskName("Matching Classes")
            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BLOCK)
            .setUpdateIntervalMillis(10)
            .continuousUpdate()
            .setMaxRenderedLength(125)
            .setInitialMax(srcClasses.size.toLong())
            .build()

        srcClasses.forEach { src ->
            val ranking = ClassifierUtil.rank(src, dstClasses, ClassClassifier.rankers, ClassifierUtil::isMaybeEqual, maxMismatch)
            if(checkRank(ranking, classAbsThreshold, classRelThreshold, maxScore)) {
                val match = ranking[0].subject
                matches[src] = match
            }
            progress.step()
        }
        progress.close()

        reduceMatches(matches)

        matches.forEach { (src, dst) ->
            match(src, dst)
        }

        println("Matched ${matches.size} classes. (${srcClasses.size - matches.size} unmatched, ${env.groupA.classes.size} total)")

        return matches.isNotEmpty()
    }

    fun autoMatchMemberMethods(): Boolean {
        val totalUnmatched = AtomicInteger()

        fun matchMemberMethods(totalUnmatched: AtomicInteger): ConcurrentHashMap<MethodEntry, MethodEntry> {
            val classes = env.groupA.classes.filter { it.hasMatch() && it.memberMethods.isNotEmpty() }
                .filter { it.memberMethods.any { m -> !m.hasMatch() && m.isMatchable } }
                .toList()
            if(classes.isEmpty()) return ConcurrentHashMap()

            val maxScore = MethodClassifier.maxScore
            val maxMismatch = maxScore - getRawScore(methodAbsThreshold * (1 - methodRelThreshold), maxScore)
            val ret = ConcurrentHashMap<MethodEntry, MethodEntry>()

            classes.forEach { srcCls ->
                var unmatched = 0
                for(srcMethod in srcCls.memberMethods) {
                    if(srcMethod.hasMatch() || !srcMethod.isMatchable) continue
                    val ranking = ClassifierUtil.rank(srcMethod, srcCls.match!!.memberMethods, MethodClassifier.rankers, ClassifierUtil::isMaybeEqual, maxMismatch)
                    if(checkRank(ranking, methodAbsThreshold, methodRelThreshold, maxScore)) {
                        val match = ranking[0].subject
                        ret[srcMethod] = match
                    } else {
                        unmatched++
                    }
                }
                if(unmatched > 0) totalUnmatched.addAndGet(unmatched)
            }

            reduceMatches(ret)
            return ret
        }

        val matches = matchMemberMethods(totalUnmatched)
        matches.forEach { (src, dst) ->
            match(src, dst)
        }

        println("Matched ${matches.size} member methods. (${totalUnmatched.get()} unmatched)")
        return matches.isNotEmpty()
    }

    private fun checkRank(ranking: List<RankResult<*>>, absThreshold: Double, relThreshold: Double, maxScore: Double): Boolean {
        if(ranking.isEmpty()) return false

        val score = getScore(ranking[0].score, maxScore)
        if(score < absThreshold) return false

        if(ranking.size == 1) {
            return true
        } else {
            val nextScore = getScore(ranking[1].score, maxScore)
            return nextScore < score * (1 - relThreshold)
        }
    }

    private fun getScore(rawScore: Double, maxScore: Double): Double {
        val ret = rawScore / maxScore
        return ret * ret
    }

    private fun getRawScore(score: Double, maxScore: Double): Double {
        return sqrt(score) * maxScore
    }

    private fun <T> reduceMatches(matches: ConcurrentHashMap<T, T>) {
        val matched = hashSetOf<T>()
        val conflictingMatches = hashSetOf<T>()

        matches.values.forEach { match ->
            if(!matched.add(match)) {
                conflictingMatches.add(match)
            }
        }

        if(conflictingMatches.isNotEmpty()) {
            matches.values.removeAll(conflictingMatches)
        }
    }

    fun String.isObfuscatedName(): Boolean {
        return arrayOf("class", "method", "field").any { this.startsWith(it) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if(args.size != 3) error("Usage: updater.jar <old-jar> <new-jar> <output-jar>")
        val jarA = File(args[0])
        val jarB = File(args[1])
        val outputJar = File(args[2])

        init(jarA, jarB)
        run()
    }

    private const val classAbsThreshold = 0.8
    private const val classRelThreshold = 0.08
    private const val methodAbsThreshold = 0.8
    private const val methodRelThreshold = 0.08
}