package dev.revtools.deobfuscator.transformer

import com.google.common.collect.MultimapBuilder
import dev.revtools.deobfuscator.Transformer
import dev.revtools.deobfuscator.asm.tree.ClassGroup
import dev.revtools.deobfuscator.asm.tree.owner
import dev.revtools.deobfuscator.asm.util.append
import dev.revtools.deobfuscator.asm.util.delete
import dev.revtools.deobfuscator.asm.util.prepend
import dev.revtools.deobfuscator.asm.util.replace
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.Type.INT_TYPE
import org.objectweb.asm.Type.LONG_TYPE
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.*
import org.tinylog.kotlin.Logger
import java.math.BigInteger
import kotlin.math.absoluteValue

class MultipliersRemover : Transformer {

    private var count = 0

    override fun run(group: ClassGroup) {
        Logger.info("Computing field multipliers decryption keys...")
        
        val multipliers = MultiplierSolver(group).computeDecoders()
        val decoders = multipliers.associatedFieldMultipliers.mapKeys { it.key }.mapValues { it.value.toLong() }
        
        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                method.maxStack += 2
                method.decryptMultipliers(decoders)
                method.simplifyMultiplyExpression()
                method.maxStack -= 2
            }
        }

        Logger.info("Removing field multipliers with solved decryption keys..")

        group.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val insns = method.instructions.iterator()
                while(insns.hasNext()) {
                    val insn = insns.next()
                    if(insn is LdcInsnNode) {
                        if(insn.next.opcode == IMUL && insn.next.next.opcode == LDC && insn.next.next.next.opcode == IMUL) {
                            val factorA = insn.ldcNum!!
                            val factorB = (insn.next.next as LdcInsnNode).ldcNum!!
                            val product = factorA * factorB
                            if(product == 1) {
                                insns.remove()
                                repeat(3) {
                                    insns.next()
                                    insns.remove()
                                }
                                count++
                            }
                        }
                    }
                }
            }
        }

        Logger.info("Removed total of $count field multipliers.")
    }

    private fun MethodNode.decryptMultipliers(decoders: Map<String, Long>) {
        val insnList = instructions
        for (insn in insnList.iterator()) {
            if (insn !is FieldInsnNode) continue
            if (insn.desc != INT_TYPE.descriptor && insn.desc != LONG_TYPE.descriptor) continue
            val fieldName = "${insn.owner}.${insn.name}"
            val decoder = decoders[fieldName] ?: continue
            when (insn.opcode) {
                GETFIELD, GETSTATIC -> {
                    when (insn.desc) {
                        INT_TYPE.descriptor -> {
                            when (insn.next.opcode) {
                                I2L -> insnList.append(insn.next, LdcInsnNode(ModMath.invert(decoder)), InsnNode(LMUL))
                                else -> insnList.append(insn, LdcInsnNode(ModMath.invert(decoder.toInt())), InsnNode(IMUL))
                            }
                        }
                        LONG_TYPE.descriptor -> insnList.append(insn, LdcInsnNode(ModMath.invert(decoder)), InsnNode(LMUL))
                        else -> error(insn)
                    }
                    count++
                }
                PUTFIELD -> {
                    when (insn.desc) {
                        INT_TYPE.descriptor -> {
                            when (insn.previous.opcode) {
                                DUP_X1 -> {
                                    insnList.prepend(insn.previous, LdcInsnNode(decoder.toInt()), InsnNode(IMUL))
                                    insnList.append(insn, LdcInsnNode(ModMath.invert(decoder.toInt())), InsnNode(IMUL))
                                }
                                DUP, DUP_X2, DUP2, DUP2_X1, DUP2_X2 -> error(insn)
                                else -> insnList.prepend(insn, LdcInsnNode(decoder.toInt()), InsnNode(IMUL))
                            }
                        }
                        LONG_TYPE.descriptor -> {
                            when (insn.previous.opcode) {
                                DUP2_X1 -> {
                                    insnList.prepend(insn.previous, LdcInsnNode(decoder), InsnNode(LMUL))
                                    insnList.append(insn, LdcInsnNode(ModMath.invert(decoder)), InsnNode(LMUL))
                                }
                                DUP, DUP_X1, DUP_X2, DUP2, DUP2_X2 -> error(insn)
                                else -> insnList.prepend(insn, LdcInsnNode(decoder), InsnNode(LMUL))
                            }
                        }
                        else -> error(insn)
                    }
                    count++
                }
                PUTSTATIC -> {
                    when (insn.desc) {
                        INT_TYPE.descriptor -> {
                            when (insn.previous.opcode) {
                                DUP -> {
                                    insnList.prepend(insn.previous, LdcInsnNode(decoder.toInt()), InsnNode(IMUL))
                                    insnList.append(insn, LdcInsnNode(ModMath.invert(decoder.toInt())), InsnNode(IMUL))
                                }
                                DUP_X1, DUP_X2, DUP2, DUP2_X1, DUP2_X2 -> error(insn)
                                else -> insnList.prepend(insn, LdcInsnNode(decoder.toInt()), InsnNode(IMUL))
                            }
                        }
                        LONG_TYPE.descriptor -> {
                            when (insn.previous.opcode) {
                                DUP2 -> {
                                    insnList.prepend(insn.previous, LdcInsnNode(decoder), InsnNode(LMUL))
                                    insnList.append(insn, LdcInsnNode(ModMath.invert(decoder)), InsnNode(LMUL))
                                }
                                DUP, DUP_X1, DUP_X2, DUP2_X1, DUP2_X2 -> error(insn)
                                else -> insnList.prepend(insn, LdcInsnNode(decoder), InsnNode(LMUL))
                            }
                        }
                        else -> error(insn)
                    }
                    count++
                }
            }
        }
    }

    private fun isMultiplier(n: Number) = ModMath.isInvertible(n) && ModMath.invert(n) != n
    private val LdcInsnNode.ldcNum: Int? get() = if(this.cst is Int) this.cst as Int else null

    private fun MethodNode.simplifyMultiplyExpression() {
        val insnList = instructions
        val interpreter = MultiplierExprInterpreter()
        val analyzer = Analyzer(interpreter)
        analyzer.analyze(owner.name, this)
        for (mul in interpreter.constantMultiplications) {
            when (mul.insn.opcode) {
                IMUL -> associateMultiplication(insnList, mul, 1)
                LMUL -> associateMultiplication(insnList, mul, 1L)
                else -> error(mul)
            }
        }
    }

    private fun associateMultiplication(insnList: InsnList, mul: Expr.Mul, num: Int) {
        val n = num * mul.const.n.toInt()
        val other = mul.other
        when {
            other is Expr.Mul -> {
                insnList.delete(mul.insn, mul.const.insn)
                associateMultiplication(insnList, other, n)
            }
            other is Expr.Const -> {
                insnList.delete(mul.insn, mul.const.insn)
                insnList.replace(other.insn, loadInt(n * other.n.toInt()))
            }
            other is Expr.Add -> {
                insnList.delete(mul.insn, mul.const.insn)
                distributeAddition(insnList, other.a, n)
                distributeAddition(insnList, other.b, n)
            }
            n == 1 -> insnList.delete(mul.insn, mul.const.insn)
            else -> insnList.replace(mul.const.insn, loadInt(n))
        }
    }

    private fun associateMultiplication(insnList: InsnList, mul: Expr.Mul, num: Long) {
        val n = num * mul.const.n.toLong()
        val other = mul.other
        when {
            other is Expr.Mul -> {
                insnList.delete(mul.insn, mul.const.insn)
                associateMultiplication(insnList, other, n)
            }
            other is Expr.Const -> {
                insnList.delete(mul.insn, mul.const.insn)
                insnList.replace(other.insn, loadLong(n * other.n.toLong()))
            }
            other is Expr.Add -> {
                insnList.delete(mul.insn, mul.const.insn)
                distributeAddition(insnList, other.a, n)
                distributeAddition(insnList, other.b, n)
            }
            n == 1L -> insnList.delete(mul.insn, mul.const.insn)
            else -> insnList.replace(mul.const.insn, loadLong(n))
        }
    }

    private fun distributeAddition(insnList: InsnList, expr: Expr, n: Int) {
        when (expr) {
            is Expr.Const -> insnList.replace(expr.insn, loadInt(n * expr.n.toInt()))
            is Expr.Mul -> associateMultiplication(insnList, expr, n)
            else -> error(expr)
        }
    }

    private fun distributeAddition(insnList: InsnList, expr: Expr, n: Long) {
        when (expr) {
            is Expr.Const -> insnList.replace(expr.insn, loadLong(n * expr.n.toLong()))
            is Expr.Mul -> associateMultiplication(insnList, expr, n)
            else -> error(expr)
        }
    }

    private class MultiplierExprInterpreter : Interpreter<Expr>(ASM6) {

        private val sourceInterpreter = SourceInterpreter()

        private val mults = LinkedHashMap<AbstractInsnNode, Expr.Mul>()

        override fun binaryOperation(insn: AbstractInsnNode, value1: Expr, value2: Expr): Expr? {
            val bv = sourceInterpreter.binaryOperation(insn, value1.sv, value2.sv) ?: return null
            if (value1 == value2) return Expr.Var(bv)
            return when (insn.opcode) {
                IMUL, LMUL -> {
                    if (value1 !is Expr.Const && value2 !is Expr.Const) {
                        Expr.Var(bv)
                    } else {
                        Expr.Mul(bv, value1, value2).also {
                            mults[insn] = it
                        }
                    }
                }
                IADD, ISUB, LADD, LSUB -> {
                    if ((value1 is Expr.Const || value1 is Expr.Mul) && (value2 is Expr.Const || value2 is Expr.Mul)) {
                        Expr.Add(bv, value1, value2)
                    } else {
                        Expr.Var(bv)
                    }
                }
                else -> Expr.Var(bv)
            }
        }

        override fun copyOperation(insn: AbstractInsnNode, value: Expr): Expr = Expr.Var(sourceInterpreter.copyOperation(insn, value.sv))

        override fun merge(value1: Expr, value2: Expr): Expr {
            if (value1 == value2) {
                return value1
            } else if (value1 is Expr.Mul && value2 is Expr.Mul && value1.insn == value2.insn) {
                if (value1.a == value2.a && value1.a is Expr.Const) {
                    return Expr.Mul(value1.sv, value1.a, merge(value1.b, value2.b)).also { mults[value1.insn] = it }
                } else if (value1.b == value2.b && value1.b is Expr.Const) {
                    return Expr.Mul(value1.sv, merge(value1.a, value2.a), value1.b).also { mults[value1.insn] = it }
                }
            } else if (value1 is Expr.Add && value2 is Expr.Add && value1.insn == value2.insn) {
                if (value1.a == value2.a && value1.a !is Expr.Var) {
                    val bb = merge(value1.b, value2.b)
                    if (bb is Expr.Const || bb is Expr.Mul) {
                        return Expr.Add(value1.sv, value1.a, bb)
                    }
                } else if (value1.b == value2.b && value2.b !is Expr.Var) {
                    val aa = merge(value1.a, value2.a)
                    if (aa is Expr.Const || aa is Expr.Mul) {
                        return Expr.Add(value1.sv, aa, value1.b)
                    }
                }
            }
            if (value1 is Expr.Mul) mults.remove(value1.insn)
            if (value2 is Expr.Mul) mults.remove(value2.insn)
            return Expr.Var(sourceInterpreter.merge(value1.sv, value2.sv))
        }

        override fun naryOperation(insn: AbstractInsnNode, values: MutableList<out Expr>): Expr? {
            return sourceInterpreter.naryOperation(insn, emptyList())?.let { Expr.Var(it) }
        }

        override fun newOperation(insn: AbstractInsnNode): Expr {
            val bv = sourceInterpreter.newOperation(insn)
            return when (insn.opcode) {
                LDC ->  {
                    val cst = (insn as LdcInsnNode).cst
                    when (cst) {
                        is Int, is Long -> Expr.Const(bv, cst as Number)
                        else -> Expr.Var(bv)
                    }
                }
                ICONST_1, LCONST_1 -> Expr.Const(bv, 1)
                ICONST_0, LCONST_0 -> Expr.Const(bv, 0)
                else -> Expr.Var(bv)
            }
        }

        override fun newValue(type: Type?): Expr? {
            return sourceInterpreter.newValue(type)?.let { Expr.Var(it) }
        }

        override fun returnOperation(insn: AbstractInsnNode, value: Expr, expected: Expr) {}

        override fun ternaryOperation(insn: AbstractInsnNode, value1: Expr, value2: Expr, value3: Expr): Expr? = null

        override fun unaryOperation(insn: AbstractInsnNode, value: Expr): Expr? {
            return sourceInterpreter.unaryOperation(insn, value.sv)?.let { Expr.Var(it) }
        }

        val constantMultiplications: Collection<Expr.Mul> get() {
            val ms = LinkedHashSet<Expr.Mul>()
            for (m in mults.values) {
                val other = m.other
                if (other is Expr.Mul) {
                    ms.remove(other)
                }
                if (other is Expr.Add && other.a is Expr.Mul) {
                    ms.remove(other.a)
                }
                if (other is Expr.Add && other.b is Expr.Mul) {
                    ms.remove(other.b)
                }
                ms.add(m)
            }
            return ms
        }
    }

    private sealed class Expr : Value {

        override fun getSize(): Int = sv.size

        abstract val sv: SourceValue

        val insn get() = sv.insns.single()

        data class Var(override val sv: SourceValue) : Expr() {

            override fun toString(): String = "(#${sv.hashCode().toString(16)})"
        }

        data class Const(override val sv: SourceValue, val n: Number) : Expr() {

            override fun toString(): String ="($n)"
        }

        data class Add(override val sv: SourceValue, val a: Expr, val b: Expr) : Expr() {

            override fun toString(): String {
                val c = if (insn.opcode == IADD || insn.opcode == LADD) '+' else '-'
                return "($a$c$b)"
            }
        }

        data class Mul(override val sv: SourceValue, val a: Expr, val b: Expr) : Expr() {

            val const get() = a as? Const ?: b as Const

            val other get() = if (const == a) b else a

            override fun toString(): String = "($a*$b)"
        }
    }

    private class MultiplierSolver(private val group: ClassGroup) {

        fun computeDecoders(): Multipliers {
            val multipliers = Multipliers()
            val analyzer = Analyzer(MultiplierInterpreter(multipliers))

            group.classes.forEach { cls ->
                cls.methods.forEach { method ->
                    analyzer.analyze(method.owner.name, method)
                }
            }
            
            multipliers.solve()
            
            return multipliers
        }

        private class MultiplierInterpreter(private val multipliers: Multipliers) : Interpreter<MultExpr>(ASM9) {

            private val ldcs = HashSet<MultExpr>()

            private val ldcs2 = HashSet<MultExpr>()

            private val puts = HashMap<MultExpr, MultExpr>()

            private val src = SourceInterpreter()

            override fun newValue(type: Type?) = src.newValue(type)?.let { MultExpr(it) }

            override fun copyOperation(insn: AbstractInsnNode, value: MultExpr) = when (insn.opcode) {
                DUP, DUP2, DUP2_X1, DUP_X1 -> value
                else -> MultExpr(src.copyOperation(insn, value.srcValue))
            }

            override fun merge(value1: MultExpr, value2: MultExpr) = MultExpr(src.merge(value1.srcValue, value2.srcValue))

            override fun returnOperation(insn: AbstractInsnNode, value: MultExpr, expected: MultExpr) {}

            override fun ternaryOperation(insn: AbstractInsnNode, value1: MultExpr, value2: MultExpr, value3: MultExpr) = MultExpr(src.ternaryOperation(insn, value1.srcValue, value2.srcValue, value3.srcValue))

            override fun naryOperation(insn: AbstractInsnNode, values: MutableList<out MultExpr>) = MultExpr(src.naryOperation(insn, values.map { it.srcValue }))

            override fun newOperation(insn: AbstractInsnNode) = MultExpr(src.newOperation(insn))

            override fun unaryOperation(insn: AbstractInsnNode, value: MultExpr) = MultExpr(src.unaryOperation(insn, value.srcValue)).also {
                if (insn.opcode == PUTSTATIC) setField(it, value)
            }

            override fun binaryOperation(insn: AbstractInsnNode, value1: MultExpr, value2: MultExpr) = MultExpr.Binary(src.binaryOperation(insn, value1.srcValue, value2.srcValue), value1, value2).also {
                when (insn.opcode) {
                    IMUL, LMUL -> {
                        val fieldMul = asFieldMul(it) ?: return@also
                        if (ldcs.add(fieldMul.ldc)) {
                            multipliers.unsolvedMultipliers.put(fieldMul.f.fieldName, Multiplier.decoder(fieldMul.ldc.ldcNum))
                        }
                    }
                    PUTFIELD -> setField(it, value2)
                }
            }

            private fun isMultiplier(n: Number) = ModMath.isInvertible(n) && ModMath.invert(n) != n

            private fun setField(put: MultExpr, value: MultExpr) {
                puts[value] = put
                if (value.isLdcInt) {
                    //
                } else if (value is MultExpr.Binary) {
                    distribute(put.srcValue.insn as FieldInsnNode, value)
                }
            }

            private fun distribute(put: FieldInsnNode, value: MultExpr.Binary) {
                if (value.isMul) {
                    val fm = asFieldMul(value)
                    if (fm != null && ldcs2.add(fm.ldc)) {
                        check(multipliers.unsolvedMultipliers.remove(fm.f.fieldName, Multiplier.decoder(fm.ldc.ldcNum)))
                        multipliers.solvedMultipliers.add(SolvedMultiplier(put.fieldName, fm.f.fieldName, fm.ldc.ldcNum))
                        return
                    }
                }
                if (!value.isMul && !value.isAdd) return
                val a = value.left
                val b = value.right
                var ldc: MultExpr? = null
                var other: MultExpr? = null
                if (a.isLdcInt) {
                    ldc = a
                    other = b
                } else if (b.isLdcInt) {
                    ldc = b
                    other = a
                }
                if (ldc != null && other != null) {
                    val n = ldc.ldcNum
                    if (isMultiplier(n) && ldcs.add(ldc)) {
                        val getField = puts[other]
                        if (getField == null) {
                            multipliers.unsolvedMultipliers.put(put.fieldName, Multiplier.encoder(n))
                        } else {
                            multipliers.solvedMultipliers.add(SolvedMultiplier(put.fieldName, getField.fieldName, n))
                        }
                    }
                    if (value.isMul) return
                }
                if (a is MultExpr.Binary) distribute(put, a)
                if (b is MultExpr.Binary) distribute(put, b)
            }

            private fun asFieldMul(value: MultExpr.Binary): FieldMul? {
                var ldc: MultExpr? = null
                var get: MultExpr? = null
                if (value.left.isLdcInt && value.right.isGetField) {
                    ldc = value.left
                    get = value.right
                } else if (value.right.isLdcInt && value.left.isGetField) {
                    ldc = value.right
                    get = value.left
                }
                if (ldc != null && get != null) {
                    if (isMultiplier(ldc.ldcNum)) return FieldMul(get, ldc)
                }
                return null
            }

            private val MultExpr.isLdcInt get() = srcValue.insn.let { it != null && it is LdcInsnNode && (it.cst is Int || it.cst is Long) }

            private val SourceValue.insn: AbstractInsnNode? get() = insns.singleOrNull()

            private val MultExpr.isGetField get() = srcValue.insn.let { it != null && (it.opcode == GETSTATIC || it.opcode == GETFIELD) }

            private val MultExpr.ldcNum get() = srcValue.insns.single().let { it as LdcInsnNode; it.cst as Number }

            private val FieldInsnNode.fieldName get() = "${owner}.${name}"

            private val MultExpr.fieldName get() = srcValue.insns.single().let { it as FieldInsnNode; it.fieldName }

            private val MultExpr.isMul get() = srcValue.insn.let { it != null && (it.opcode == IMUL || it.opcode == LMUL) }

            private val MultExpr.isAdd get() = srcValue.insn.let { it != null && (it.opcode == IADD || it.opcode == LADD || it.opcode == ISUB || it.opcode == LSUB) }

            private data class FieldMul(val f: MultExpr, val ldc: MultExpr)
            private data class LdcMul(val ldcA: MultExpr, val ldcB: MultExpr)
        }

        private open class MultExpr(val srcValue: SourceValue) : Value {

            override fun equals(other: Any?) = other is MultExpr && srcValue == other.srcValue

            override fun hashCode() = srcValue.hashCode()

            override fun getSize() = srcValue.size

            class Binary(value: SourceValue, val left: MultExpr, val right: MultExpr) : MultExpr(value)
        }

        private data class Multiplier(val isDecoder: Boolean, val number: Number) {

            val inverseNumber = if (isDecoder) number else ModMath.invert(number)

            companion object {
                fun decoder(n: Number) = Multiplier(true, n)
                fun encoder(n: Number) = Multiplier(false, n)
            }
        }

        private data class SolvedMultiplier(val putter: String, val getter: String, val number: Number)

        class Multipliers {
            
            val associatedFieldMultipliers = HashMap<String, Number>()
            val unsolvedMultipliers = MultimapBuilder.hashKeys().arrayListValues().build<String, Multiplier>()
            val solvedMultipliers = HashSet<SolvedMultiplier>()

            fun solve() {
                while (true) {
                    simplify()
                    if (unsolvedMultipliers.isEmpty) return
                    solveOne()
                }
            }

            private fun isMultiplier(n: Number) = ModMath.isInvertible(n) && ModMath.invert(n) != n

            private fun simplify() {
                val itr = solvedMultipliers.iterator()
                for (ma in itr) {
                    if (ma.putter in associatedFieldMultipliers) {
                        itr.remove()
                        val dec = associatedFieldMultipliers.getValue(ma.putter)
                        val decx = mul(dec, ma.number)
                        if (isMultiplier(decx)) unsolvedMultipliers.put(ma.getter, Multiplier.decoder(decx))
                    } else if (ma.getter in associatedFieldMultipliers) {
                        itr.remove()
                        val enc = ModMath.invert(associatedFieldMultipliers.getValue(ma.getter))
                        val encx = mul(enc, ma.number)
                        if (isMultiplier(encx)) unsolvedMultipliers.put(ma.putter, Multiplier.encoder(encx))
                    }
                }
            }

            private fun solveOne() {
                var e = unsolvedMultipliers.asMap().entries.firstOrNull { e -> solvedMultipliers.none { it.getter == e.key || it.putter == e.key } }
                if (e == null) e = unsolvedMultipliers.asMap().entries.first()
                val (f, ms) = e
                val unfoldedNumber = unfold(ms)
                if(unfoldedNumber == Int.MAX_VALUE) {
                    Logger.warn("Failed to calculate multiplier decoder value. Field: $f, Multipliers: ${e.value.joinToString(", ") { it.number.toString() + ":" + it.inverseNumber }}.")
                    unsolvedMultipliers.removeAll(f)
                } else {
                    associatedFieldMultipliers[f] = unfold(ms)
                    unsolvedMultipliers.removeAll(f)
                }
            }

            private fun unfold(multipliers: Collection<Multiplier>): Number {
                val mults = multipliers.distinct()
                if (mults.size == 1) return mults.single().inverseNumber
                val pairedMults = mults.filter { mul -> mul.isDecoder && mults.any { b -> !b.isDecoder && mul.inverseNumber == b.inverseNumber } }
                if (pairedMults.isNotEmpty()) return pairedMults.single().inverseNumber
                val fs = mults.filter { f -> mults.all { isFactor(it, f) } }
                if (fs.size == 1) return fs.single().inverseNumber
                if(fs.isEmpty()) {
                    mults.forEach { mul ->
                        /*
                         * Really something should be done here in the event that unfolding fails.
                         * This can happen if the multiplier has 2+ folded values and one being an even.
                         *
                         * In theory heuristics could be used to find likely factors given known constants in the client.
                         * But fuck it. it's not breaking shit right now.
                         *
                         * @author Layto <laytodev>
                         */
                    }
                }
                return fs.first { it.isDecoder }.inverseNumber
            }

            private fun isFactor(product: Multiplier, factor: Multiplier) = div(product, factor).toLong().absoluteValue <= 0xff

            private fun div(a: Multiplier, b: Multiplier): Number {
                return if (a.isDecoder == b.isDecoder) {
                    mul(ModMath.invert(b.number), a.number)
                } else {
                    mul(b.number, a.number)
                }
            }

            private fun mul(a: Number, b: Number): Number = when (a) {
                is Int -> a.toInt() * b.toInt()
                is Long -> a.toLong() * b.toLong()
                else -> error(a)
            }
        }
    }

    object ModMath {

        private val INT_MODULUS = BigInteger.ONE.shiftLeft(32)
        private val LONG_MODULUS = BigInteger.ONE.shiftLeft(64)

        fun invert(n: Int): Int = n.toBigInteger().modInverse(INT_MODULUS).toInt()
        fun invert(n: Long): Long = n.toBigInteger().modInverse(LONG_MODULUS).toLong()

        fun invert(n: Number): Number {
            return when (n) {
                is Int -> invert(n)
                is Long -> invert(n)
                else -> error(n)
            }
        }

        fun isInvertible(n: Int): Boolean = n and 1 == 1
        fun isInvertible(n: Long): Boolean = isInvertible(n.toInt())

        fun isInvertible(n: Number): Boolean {
            return when (n) {
                is Int, is Long -> isInvertible(n.toInt())
                else -> error(n)
            }
        }
    }

    fun AbstractInsnNode.isIf(): Boolean {
        return this is JumpInsnNode && opcode != GOTO
    }

    fun AbstractInsnNode.isReturn(): Boolean = when(opcode) {
        in IRETURN..RETURN -> true
        else -> false
    }

    fun AbstractInsnNode.pushesInt(): Boolean = when(opcode) {
        LDC -> (this as LdcInsnNode).cst is Int
        SIPUSH, BIPUSH -> true
        in ICONST_M1..ICONST_5 -> true
        else -> false
    }

    val AbstractInsnNode.pushedInt: Int get() = when {
        opcode in 2..8 -> opcode - 3
        opcode == BIPUSH || opcode == SIPUSH -> (this as IntInsnNode).operand
        this is LdcInsnNode && cst is Int -> cst as Int
        else -> throw IllegalStateException()
    }

    fun loadInt(n: Int): AbstractInsnNode = when (n) {
        in -1..5 -> InsnNode(n + 3)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(BIPUSH, n)
        in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(SIPUSH, n)
        else -> LdcInsnNode(n)
    }

    fun loadLong(n: Long): AbstractInsnNode = when (n) {
        0L, 1L -> InsnNode((n + 9).toInt())
        else -> LdcInsnNode(n)
    }
}