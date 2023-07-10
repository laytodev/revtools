package dev.revtools.updater.asm

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.AbstractInsnNode.*
import org.objectweb.asm.tree.analysis.*
import java.util.*
import java.util.ArrayDeque as InsnQueue

object FieldAnalyzer {

    fun analyzeFieldInitializer(field: FieldEntry) {
        if(field.type.isPrimitive()) return

        val method = field.writeRefs.iterator().next()
        val insns = method.instructions
        var fieldWrite: AbstractInsnNode? = null

        val insnsItr = insns.iterator()
        while(insnsItr.hasNext()) {
            val insn = insnsItr.next()

            if(insn.opcode == PUTFIELD || insn.opcode == PUTSTATIC) {
                insn as FieldInsnNode
                val cls = field.group.getClass(insn.owner)
                if(insn.name == field.name && insn.desc == field.desc
                    && (insn.owner == field.cls.name) || cls != null && cls.resolveField(insn.name, insn.desc) == field) {
                        fieldWrite = insn
                    break
                }
            }
        }

        if(fieldWrite == null) {
            throw IllegalStateException("Can't find field write insn for $field in $method.")
        }

        val interp = SourceInterpreter()
        val analyzer = Analyzer(interp)

        val frames: Array<Frame<SourceValue>?>
        try {
            frames = analyzer.analyze(method.cls.name, method.node)
            if(frames.size != method.node.instructions.size()) throw RuntimeException("Invalid frame count.")
        } catch(e: AnalyzerException) {
            throw RuntimeException()
        }

        val tracedPositions = BitSet(insns.size())
        val positionsToTrace = InsnQueue<AbstractInsnNode>()

        tracedPositions.set(insns.indexOf(fieldWrite))
        positionsToTrace.add(fieldWrite)

        var insn: AbstractInsnNode = InsnNode(NOP)
        while(positionsToTrace.poll()?.also { insn = it } != null) {
            val pos = insns.indexOf(insn)
            val frame = frames[pos]!!
            val stackConsumed = getStackDemand(insn, frame)

            for(i in 0 until stackConsumed) {
                val value = frame.getStack(frame.stackSize - i - 1)
                for(insn2 in value.insns) {
                    val pos2 = insns.indexOf(insn2)
                    if(tracedPositions.get(pos2)) continue

                    tracedPositions.set(pos2)
                    positionsToTrace.add(insn2)
                }
            }

            if(insn.type == VAR_INSN && insn.opcode >= ILOAD && insn.opcode <= ALOAD) {
                val value = frame.getLocal((insn as VarInsnNode).`var`)
                for(insn2 in value.insns) {
                    val pos2 = insns.indexOf(insn2)
                    if(tracedPositions.get(pos2)) continue

                    tracedPositions.set(pos2)
                    positionsToTrace.add(insn2)
                }
            } else if(insn.opcode == NEW) {
                insn as TypeInsnNode
                val insnsItr2 = insns.iterator(pos + 1)
                while(insnsItr2.hasNext()) {
                    val insn2 = insnsItr2.next()
                    if(insn2.opcode == INVOKESPECIAL) {
                        insn2 as MethodInsnNode
                        if(insn2.name == "<init>" && insn2.owner == (insn as TypeInsnNode).desc) {
                            val pos2 = insns.indexOf(insn2)
                            if(!tracedPositions.get(pos2)) {
                                tracedPositions.set(pos2)
                                positionsToTrace.add(insn2)
                            }
                            break
                        }
                    }
                }
            }
        }

        val initInsns = ArrayList<AbstractInsnNode>(tracedPositions.cardinality())
        var pos = 0

        while(tracedPositions.nextSetBit(pos).also { pos = it } != -1) {
            insn = insns[pos]
            initInsns.add(insn)
            pos++
        }

        field.initializer.clear()
        initInsns.forEach { field.initializer.add(it) }
    }

    private fun getStackDemand(insn: AbstractInsnNode, frame: Frame<*>): Int {
        return when (insn.type) {
            INSN -> when (insn.opcode) {
                NOP -> 0
                ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5, LCONST_0, LCONST_1, FCONST_0, FCONST_1, FCONST_2, DCONST_0, DCONST_1 -> 0
                IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD -> 2
                IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE -> 3
                POP -> 1
                POP2 -> if (frame.getStack(frame.stackSize - 1).size == 1) 2 else 1
                DUP -> 1
                DUP_X1 -> 2
                DUP_X2 -> if (frame.getStack(frame.stackSize - 2).size == 1) 3 else 2
                DUP2 -> if (frame.getStack(frame.stackSize - 1).size == 1) 2 else 1
                DUP2_X1 -> if (frame.getStack(frame.stackSize - 1).size == 1) 3 else 2
                DUP2_X2 -> if (frame.getStack(frame.stackSize - 1).size == 1) {
                    if (frame.getStack(frame.stackSize - 3).size == 1) {
                        4
                    } else {
                        3
                    }
                } else if (frame.getStack(frame.stackSize - 3).size == 1) {
                    3
                } else {
                    2
                }
                SWAP -> 2
                IADD, LADD, FADD, DADD, ISUB, LSUB, FSUB, DSUB, IMUL, LMUL, FMUL, DMUL, IDIV, LDIV, FDIV, DDIV, IREM, LREM, FREM, DREM -> 2
                INEG, LNEG, FNEG, DNEG -> 1
                ISHL, LSHL, ISHR, LSHR, IUSHR, LUSHR, IAND, LAND, IOR, LOR, IXOR, LXOR -> 2
                I2L, I2F, I2D, L2I, L2F, L2D, F2I, F2L, F2D, D2I, D2L, D2F, I2B, I2C, I2S -> 1
                LCMP, FCMPL, FCMPG, DCMPL, DCMPG -> 2
                IRETURN, LRETURN, FRETURN, DRETURN, ARETURN -> 1
                RETURN -> 0
                ARRAYLENGTH -> 1
                ATHROW -> 1
                MONITORENTER, MONITOREXIT -> 1
                else -> throw IllegalArgumentException("unknown insn opcode " + insn.opcode)
            }
            INT_INSN -> when (insn.opcode) {
                BIPUSH, SIPUSH -> 0
                NEWARRAY -> 1
                else -> throw IllegalArgumentException("unknown int insn opcode " + insn.opcode)
            }
            VAR_INSN -> when (insn.opcode) {
                ILOAD, LLOAD, FLOAD, DLOAD, ALOAD -> 0
                ISTORE, LSTORE, FSTORE, DSTORE, ASTORE -> 1
                RET -> 0
                else -> throw IllegalArgumentException("unknown var insn opcode " + insn.opcode)
            }
            TYPE_INSN -> when (insn.opcode) {
                NEW -> 0
                ANEWARRAY -> 1
                CHECKCAST, INSTANCEOF -> 1
                else -> throw IllegalArgumentException("unknown type insn opcode " + insn.opcode)
            }
            FIELD_INSN -> when (insn.opcode) {
                GETSTATIC -> 0
                PUTSTATIC -> 1
                GETFIELD -> 1
                PUTFIELD -> 2
                else -> throw IllegalArgumentException("unknown field insn opcode " + insn.opcode)
            }
            METHOD_INSN -> Type.getArgumentTypes((insn as MethodInsnNode).desc).size + if (insn.getOpcode() != INVOKESTATIC) 1 else 0
            INVOKE_DYNAMIC_INSN -> Type.getArgumentTypes((insn as InvokeDynamicInsnNode).desc).size
            JUMP_INSN -> when (insn.opcode) {
                IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> 1
                IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE -> 2
                GOTO -> 0
                JSR -> 0
                IFNULL, IFNONNULL -> 1
                else -> throw IllegalArgumentException("unknown jump insn opcode " + insn.opcode)
            }

            LABEL -> 0
            LDC_INSN -> 0
            IINC_INSN -> 0
            TABLESWITCH_INSN -> 1
            LOOKUPSWITCH_INSN -> 1
            MULTIANEWARRAY_INSN -> (insn as MultiANewArrayInsnNode).dims
            FRAME -> 0
            LINE -> 0
            else -> throw IllegalArgumentException("unknown insn type " + insn.type + " for opcode " + insn.opcode + ", in " + insn.javaClass.name)
        }
    }
}