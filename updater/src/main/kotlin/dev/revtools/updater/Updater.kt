package dev.revtools.updater

import dev.revtools.updater.asm.*
import dev.revtools.updater.classifier.*
import me.tongfei.progressbar.ProgressBarBuilder
import me.tongfei.progressbar.ProgressBarStyle
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.math.sqrt
import kotlin.streams.toList

object Updater {

    val env = ClassEnv()

    fun init(jarA: File, jarB: File) {
        env.init(jarA, jarB)

        matchUnobfuscated()

        // Init all classifiers
        ClassClassifier.init()
        MethodClassifier.init()
        FieldClassifier.init()
    }

    private fun autoMatchLevel(level: RankerLevel) {
        var matchedAny: Boolean
        var matchedClassesBefore = true
        do {
            matchedAny = autoMatchMemberMethods(level)
            matchedAny = matchedAny or autoMatchMemberFields(level)

            if(!matchedAny && !matchedClassesBefore) {
                break
            }

            matchedAny = matchedAny or autoMatchClasses(level).also { matchedClassesBefore = it }

            matchedAny = matchedAny or autoMatchStaticMethods(level)
            matchedAny = matchedAny or autoMatchStaticFields(level)
        } while(matchedAny)
    }

    fun execute() {
        println("Starting matching.")

        /*
         * Recursively run matching until we no longer match anything new.
         */
        if(autoMatchClasses(RankerLevel.INITIAL)) {
            autoMatchClasses(RankerLevel.INITIAL)
        }

        autoMatchLevel(RankerLevel.INITIAL)
        autoMatchLevel(RankerLevel.INTERMEDIATE)
        autoMatchLevel(RankerLevel.FULL)
        autoMatchLevel(RankerLevel.EXTRA)

        ClassifierUtil.MatchCache.clear()
        println("Completed matching.")
        
        /* 
         * Print out the matching status results.
         */
        var totalClasses = 0
        var matchedClasses = 0
        var totalMemberMethods = 0
        var matchedMemberMethods = 0
        var totalStaticMethods = 0
        var matchedStaticMethods = 0
        var totalMemberFields = 0
        var matchedMemberFields = 0
        var totalStaticFields = 0
        var matchedStaticFields = 0
        
        env.groupA.classes.forEach { cls ->
            totalClasses++
            if(cls.hasMatch()) matchedClasses++
            cls.methods.forEach { method ->
                if(method.isStatic()) {
                    totalStaticMethods++
                    if(method.hasMatch()) matchedStaticMethods++
                } else {
                    totalMemberMethods++
                    if(method.hasMatch()) matchedMemberMethods++
                }
            }
            cls.fields.forEach { field ->
                if(field.isStatic()) {
                    totalStaticFields++
                    if(field.hasMatch()) matchedStaticFields++
                } else {
                    totalMemberFields++
                    if(field.hasMatch()) matchedMemberFields++
                }
            }
        }
        
        val classMatchStatus = String.format(
            "Classes: %d / %d (%.2f%%)",
            matchedClasses,
            totalClasses,
            if(totalClasses == 0) 0.0 else 100.0 * matchedClasses / totalClasses
        )

        val memberMethodMatchStatus = String.format(
            "Member-Methods: %d / %d (%.2f%%)",
            matchedMemberMethods,
            totalMemberMethods,
            if(totalMemberMethods == 0) 0.0 else 100.0 * matchedMemberMethods / totalMemberMethods
        )

        val staticMethodMatchStatus = String.format(
            "Static-Methods: %d / %d (%.2f%%)",
            matchedStaticMethods,
            totalStaticMethods,
            if(totalStaticMethods == 0) 0.0 else 100.0 * matchedStaticMethods / totalStaticMethods
        )

        val memberFieldMatchStatus = String.format(
            "Member-Fields: %d / %d (%.2f%%)",
            matchedMemberFields,
            totalMemberFields,
            if(totalMemberFields == 0) 0.0 else 100.0 * matchedMemberFields / totalMemberFields
        )

        val staticFieldMatchStatus = String.format(
            "Static-Fields: %d / %d (%.2f%%)",
            matchedStaticFields,
            totalStaticFields,
            if(totalStaticFields == 0) 0.0 else 100.0 * matchedStaticFields / totalStaticFields
        )

        println()
        println()
        println("======== MAPPING RESULTS ========")
        println(classMatchStatus)
        println(memberMethodMatchStatus)
        println(memberFieldMatchStatus)
        println(staticMethodMatchStatus)
        println(staticFieldMatchStatus)
        println("=================================")
    }

    fun save(file: File) {
        println("Saving and remapping jarB classes to output jar: ${file.path}")

        /*
         * Apply matching names to all entries.
         */
        val group = env.groupB
        val mappings = hashMapOf<String, String>()
        val hierarchy = ClassHierarchy(group)
        
        group.classes.forEach { cls ->
            if(cls.hasMatch()) {
                var name = cls.match!!.name
                //if(name.isObfuscatedName()) { name = cls.name }
                mappings[cls.name] = name
            }
        }

        group.classes.forEach { cls ->
            cls.methods.filter { it.hasMatch() }.forEach methodLoop@ { method ->
                var key = "${method.cls.name}.${method.name}${method.desc}"
                if(mappings.containsKey(key)) return@methodLoop
                val name = method.match!!.name
                mappings[key] = name
                hierarchy[method.cls.name]!!.getAllParents().forEach { child ->
                    mappings["$child.${method.name}${method.desc}"] = name
                }
            }
        }

        val remapper = SimpleRemapper(mappings)
        val newClasses = mutableListOf<ClassNode>()
        group.classes.map { it.node }.forEach { cls ->
            val node = ClassNode()
            cls.accept(ClassRemapper(node, remapper))
            newClasses.add(node)
        }

        if(file.exists()) file.deleteRecursively()
        JarOutputStream(file.outputStream()).use { jos ->
            newClasses.forEach { cls ->
                val writer = SuperAwareClassWriter(newClasses)
                cls.accept(writer)
                jos.putNextEntry(JarEntry(cls.name+".class"))
                jos.write(writer.toByteArray())
                jos.closeEntry()
            }
        }

        println("Successfully remapped and saved ${newClasses.size} classes to output jar.")
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

        ClassifierUtil.MatchCache.clear()

        //check(a.toString() == b.toString()) { "Invalid Match: $a -> $b"}
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

        ClassifierUtil.MatchCache.clear()

        //check(a.toString() == b.toString()) { "Invalid Match: $a -> $b"}
        println("Matched ${if(a.isStatic() && b.isStatic()) "Static-" else "Member-"}Method: $a -> $b")
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

        ClassifierUtil.MatchCache.clear()

        //check(a.toString() == b.toString()) { "Invalid Match: $a -> $b"}
        println("Matched ${if(a.isStatic() && b.isStatic()) "Static-" else "Member-"}Field: $a -> $b")
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

    private fun autoMatchClasses(level: RankerLevel): Boolean {
        val filter = { cls: ClassEntry -> !cls.hasMatch() }
        val srcClasses = env.groupA.classes.filter(filter)
        val dstClasses = env.groupB.classes.filter(filter)

        val progress = ProgressBarBuilder()
            .setTaskName(" [${level.name}] Matching Classes")
            .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
            .setInitialMax(srcClasses.size.toLong())
            .setUpdateIntervalMillis(10)
            .continuousUpdate()
            .setMaxRenderedLength(120)
            .build()

        val maxScore = ClassClassifier.getMaxScore(level)
        val maxMismatch = maxScore - getRawScore(classAbsThreshold * (1 - classRelThreshold), maxScore)
        val matches = ConcurrentHashMap<ClassEntry, ClassEntry>()
        srcClasses.runParallel { src ->
            progress.step()
            val ranking = ClassifierUtil.rank(src, dstClasses, ClassClassifier.getRankers(level), ClassifierUtil::isMaybeEqual, maxMismatch)
            if(checkRank(ranking, classAbsThreshold, classRelThreshold, maxScore)) {
                val match = ranking[0].subject
                matches[src] = match
            }
        }
        progress.close()

        reduceMatches(matches)
        matches.forEach { (src, dst) ->
            match(src, dst)
        }

        println("Matched ${matches.size} classes. (${srcClasses.size - matches.size} unmatched, ${env.groupA.classes.size} total)")
        return matches.isNotEmpty()
    }

    private fun autoMatchMemberMethods(level: RankerLevel): Boolean {
        val totalUnmatched = AtomicInteger()

        fun matchMemberMethods(level: RankerLevel, totalUnmatched: AtomicInteger): ConcurrentHashMap<MethodEntry, MethodEntry> {
            val classes = env.groupA.classes.filter { it.hasMatch() && it.memberMethods.isNotEmpty() }
                .filter { it.memberMethods.any { m -> !m.hasMatch() && m.isMatchable } }
                .toList()
            if(classes.isEmpty()) return ConcurrentHashMap()

            val maxScore = MethodClassifier.getMaxScore(level)
            val maxMismatch = maxScore - getRawScore(methodAbsThreshold * (1 - methodRelThreshold), maxScore)
            val ret = ConcurrentHashMap<MethodEntry, MethodEntry>()

            val progress = ProgressBarBuilder()
                .setTaskName("[${level.name}] Matching Member-Methods")
                .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
                .setInitialMax((classes.flatMap { it.memberMethods }.count { !it.hasMatch() && it.isMatchable }).toLong())
                .setUpdateIntervalMillis(10)
                .continuousUpdate()
                .setMaxRenderedLength(120)
                .build()

            classes.runParallel { srcCls ->
                var unmatched = 0
                for(srcMethod in srcCls.memberMethods) {
                    if(srcMethod.hasMatch() || !srcMethod.isMatchable) continue
                    progress.step()
                    val ranking = ClassifierUtil.rank(srcMethod, srcCls.match!!.memberMethods.toList(), MethodClassifier.getRankers(level), ClassifierUtil::isMaybeEqual, maxMismatch)
                    if(checkRank(ranking, methodAbsThreshold, methodRelThreshold, maxScore)) {
                        val match = ranking[0].subject
                        ret[srcMethod] = match
                    } else {
                        unmatched++
                    }
                }
                if(unmatched > 0) totalUnmatched.addAndGet(unmatched)
            }
            progress.close()

            reduceMatches(ret)
            return ret
        }

        val matches = matchMemberMethods(level, totalUnmatched)
        matches.forEach { (src, dst) ->
            match(src, dst)
        }

        println("Matched ${matches.size} member-methods. (${totalUnmatched.get()} unmatched)")
        return matches.isNotEmpty()
    }

    private fun autoMatchStaticMethods(level: RankerLevel): Boolean {
        val totalUnmatched = AtomicInteger()

        fun matchStaticMethods(totalUnmatched: AtomicInteger): ConcurrentHashMap<MethodEntry, MethodEntry> {
            val srcMethods = env.groupA.classes.flatMap { it.staticMethods }.filter { !it.hasMatch() }
            val dstMethods = env.groupB.classes.flatMap { it.staticMethods }.filter { !it.hasMatch() }

            val maxScore = MethodClassifier.getMaxScore(level)
            val maxMismatch = maxScore - getRawScore(methodAbsThreshold * (1 - methodRelThreshold), maxScore)
            val ret = ConcurrentHashMap<MethodEntry, MethodEntry>()

            val progress = ProgressBarBuilder()
                .setTaskName("[${level.name}] Matching Static-Methods")
                .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
                .setInitialMax(srcMethods.size.toLong())
                .setUpdateIntervalMillis(10)
                .continuousUpdate()
                .setMaxRenderedLength(120)
                .build()

            var unmatched = 0
            srcMethods.runParallel { srcMethod ->
                progress.step()
                val ranking = ClassifierUtil.rank(srcMethod, dstMethods, MethodClassifier.getRankers(level), ClassifierUtil::isMaybeEqual, maxMismatch)
                if(checkRank(ranking, methodAbsThreshold, methodRelThreshold, maxScore)) {
                    val match = ranking[0].subject
                    ret[srcMethod] = match
                } else {
                    unmatched++
                }
            }
            if(unmatched > 0) totalUnmatched.addAndGet(unmatched)
            progress.close()

            reduceMatches(ret)
            return ret
        }

        val matches = matchStaticMethods(totalUnmatched)
        matches.forEach { (src, dst) ->
            match(src, dst)
        }

        println("Matched ${matches.size} static-methods. (${totalUnmatched.get()} unmatched)")
        return matches.isNotEmpty()
    }

    private fun autoMatchMemberFields(level: RankerLevel): Boolean {
        val totalUnmatched = AtomicInteger()

        fun matchMemberFields(totalUnmatched: AtomicInteger): ConcurrentHashMap<FieldEntry, FieldEntry> {
            val classes = env.groupA.classes.filter { it.hasMatch() && it.memberFields.isNotEmpty() }
                .filter { it.memberFields.any { f -> !f.hasMatch() && f.isMatchable } }
                .toList()
            if(classes.isEmpty()) return ConcurrentHashMap()

            val maxScore = FieldClassifier.getMaxScore(level)
            val maxMismatch = maxScore - getRawScore(fieldAbsThreshold * (1 - fieldRelThreshold), maxScore)
            val ret = ConcurrentHashMap<FieldEntry, FieldEntry>()

            val progress = ProgressBarBuilder()
                .setTaskName("[${level.name}] Matching Member-Fields")
                .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
                .setInitialMax((classes.flatMap { it.memberFields }.count { !it.hasMatch() && it.isMatchable }).toLong())
                .setUpdateIntervalMillis(10)
                .continuousUpdate()
                .setMaxRenderedLength(120)
                .build()

            classes.runParallel { srcCls ->
                var unmatched = 0
                for(srcField in srcCls.memberFields) {
                    if(srcField.hasMatch() || !srcField.isMatchable) continue
                    progress.step()
                    val ranking = ClassifierUtil.rank(srcField, srcCls.match!!.memberFields.toList(), FieldClassifier.getRankers(level), ClassifierUtil::isMaybeEqual, maxMismatch)
                    if(checkRank(ranking, fieldAbsThreshold, fieldRelThreshold, maxScore)) {
                        val match = ranking[0].subject
                        ret[srcField] = match
                    } else {
                        unmatched++
                    }
                }
                if(unmatched > 0) totalUnmatched.addAndGet(unmatched)
            }
            progress.close()

            reduceMatches(ret)
            return ret
        }

        val matches = matchMemberFields(totalUnmatched)
        matches.forEach { (src, dst) ->
            match(src, dst)
        }

        println("Matched ${matches.size} member-fields. (${totalUnmatched.get()} unmatched)")
        return matches.isNotEmpty()
    }

    private fun autoMatchStaticFields(level: RankerLevel): Boolean {
        val totalUnmatched = AtomicInteger()

        fun matchStaticFields(totalUnmatched: AtomicInteger): ConcurrentHashMap<FieldEntry, FieldEntry> {
            val srcFields = env.groupA.classes.flatMap { it.staticFields }
                .filter { !it.hasMatch() && it.isMatchable }
                .toList()

            val maxScore = FieldClassifier.getMaxScore(level)
            val maxMismatch = maxScore - getRawScore(fieldAbsThreshold * (1 - fieldRelThreshold), maxScore)
            val ret = ConcurrentHashMap<FieldEntry, FieldEntry>()

            val progress = ProgressBarBuilder()
                .setTaskName("[${level.name}] Matching Static-Fields")
                .setStyle(ProgressBarStyle.COLORFUL_UNICODE_BAR)
                .setInitialMax(srcFields.size.toLong())
                .setUpdateIntervalMillis(10)
                .continuousUpdate()
                .setMaxRenderedLength(120)
                .build()

            var unmatched = 0
            srcFields.runParallel { srcField ->
                if(srcField.hasMatch() || !srcField.isMatchable) return@runParallel
                progress.step()
                val dstFields = env.groupB.classes.flatMap { it.staticFields }.filter { !it.hasMatch() }
                val ranking = ClassifierUtil.rank(srcField, dstFields, FieldClassifier.getRankers(level), ClassifierUtil::isMaybeEqual, maxMismatch)
                if(checkRank(ranking, fieldAbsThreshold, fieldRelThreshold, maxScore)) {
                    val match = ranking[0].subject
                    ret[srcField] = match
                } else {
                    unmatched++
                }
            }
            if(unmatched > 0) totalUnmatched.addAndGet(unmatched)
            progress.close()

            reduceMatches(ret)
            return ret
        }

        val matches = matchStaticFields(totalUnmatched)
        matches.forEach { (src, dst) ->
            match(src, dst)
        }

        println("Matched ${matches.size} static-fields. (${totalUnmatched.get()} unmatched)")
        return matches.isNotEmpty()
    }

    private fun checkRank(ranking: List<RankResult<*>>, absThreshold: Double, relThreshold: Double, maxScore: Double): Boolean {
        if(ranking.isEmpty()) return false

        val score = getScore(ranking[0].score, maxScore)
        if(score < absThreshold) return false

        return if(ranking.size == 1) {
            true
        } else {
            val nextScore = getScore(ranking[1].score, maxScore)
            nextScore < score * (1 - relThreshold)
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

    fun <T> List<T>.runParallel(workBlock: (T) -> Unit) {
        if(this.isEmpty()) return
        val itemsDone = AtomicInteger()
        try {
            val futures = threadPool.invokeAll(this.stream().map<Callable<Unit>> { workItem -> Callable {
                workBlock(workItem)
                itemsDone.incrementAndGet()
                return@Callable null
            }}.toList())
            futures.forEach { future ->
                future.get()
            }
        } catch(e: Exception) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        if(args.size < 3) error("Usage: updater.jar <old-jar> <new-jar> <output-jar>")
        val jarA = File(args[0])
        val jarB = File(args[1])
        val outputJar = File(args[2])

        init(jarA, jarB)
        execute()
        save(outputJar)

        TestClient(outputJar).start()
    }

    private val threadPool = Executors.newWorkStealingPool()

    private const val classAbsThreshold = 0.8
    private const val classRelThreshold = 0.08
    private const val methodAbsThreshold = 0.8
    private const val methodRelThreshold = 0.08
    private const val fieldAbsThreshold = 0.8
    private const val fieldRelThreshold = 0.08
}