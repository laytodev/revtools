package dev.revtools.updater.asm

import dev.revtools.updater.util.identityHashSetOf
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import java.util.ArrayDeque

class ClassInstance(val node: ClassNode) : Matchable<ClassInstance>() {

    lateinit var group: ClassGroup internal set

    val env get() = group.env
    val isShared get() = group.isShared

    val access = node.access
    val name = node.name

    var parent: ClassInstance? = null
    val children = identityHashSetOf<ClassInstance>()
    val interfaces = identityHashSetOf<ClassInstance>()
    val implementers = identityHashSetOf<ClassInstance>()

    val strings = hashSetOf<String>()
    val numbers = hashSetOf<Number>()

    val methodTypeRefs = identityHashSetOf<MethodInstance>()
    val fieldTypeRefs = identityHashSetOf<FieldInstance>()

    val methods = identityHashSetOf<MethodInstance>()
    val fields = identityHashSetOf<FieldInstance>()

    val memberMethods get() = methods.filter { !it.isStatic() }
    val memberFields get() = fields.filter { !it.isStatic() }
    val staticMethods get() = methods.filter { it.isStatic() }
    val staticFields get() = fields.filter { it.isStatic() }

    fun getMethod(name: String, desc: String) = methods.firstOrNull { it.name == name && it.desc == desc }
    fun getField(name: String, desc: String) = fields.firstOrNull { it.name == name && it.desc == desc }

    fun resolveMethod(name: String, desc: String, toInterface: Boolean): MethodInstance? {
        if(!toInterface) {
            var ret = getMethod(name, desc)
            if(ret != null) return ret

            var cls: ClassInstance = this
            while(cls.parent?.also { cls = it } != null) {
                ret = cls.getMethod(name, desc)
                if(ret != null) return ret
            }

            return resolveInterfaceMethod(name, desc)
        } else {
            var ret = getMethod(name, desc)
            if(ret != null) return ret
        }

        return null
    }

    private fun resolveInterfaceMethod(name: String, desc: String): MethodInstance? {
        val queue = ArrayDeque<ClassInstance>()
        val visited = identityHashSetOf<ClassInstance>()

        var cls = this
        do {
            cls.interfaces.forEach { itf ->
                if(visited.add(itf)) queue.add(itf)
            }
        } while(cls.parent?.also { cls = it } != null)

        if(queue.isEmpty()) return null

        val matches = identityHashSetOf<MethodInstance>()
        var foundNonAbstract = false

        while(queue.poll()?.also { cls = it } != null) {
            val ret = cls.getMethod(name, desc)
            if(ret != null && (ret.access and (ACC_PRIVATE or ACC_STATIC)) == 0) {
                matches.add(ret)

                if((ret.access and ACC_ABSTRACT) == 0) {
                    foundNonAbstract = true
                }
            }

            cls.interfaces.forEach { itf ->
                if(visited.add(itf)) queue.add(itf)
            }
        }

        if(matches.isEmpty()) return null
        if(matches.size == 1) return matches.iterator().next()

        if(foundNonAbstract) {
            val itr = matches.iterator()
            while(itr.hasNext()) {
                val m = itr.next()
                if((m.access and ACC_ABSTRACT) != 0) {
                    itr.remove()
                }
            }
            if(matches.size == 1) return matches.iterator().next()
        }

        val itr = matches.iterator()
        while(itr.hasNext()) {
            val m1 = itr.next()
            cmpLoop@ for(m2 in matches) {
                if(m2 == m1) continue

                if(m1.cls in m2.cls.interfaces) {
                    itr.remove()
                    break
                }

                queue.addAll(m2.cls.interfaces)

                while(queue.poll()?.also { cls = it } != null) {
                    if(m1.cls in cls.interfaces) {
                        itr.remove()
                        queue.clear()
                        break@cmpLoop
                    }

                    queue.addAll(cls.interfaces)
                }
            }
        }

        return matches.iterator().next()
    }

    fun resolveField(name: String, desc: String): FieldInstance? {
        var ret = getField(name, desc)
        if(ret != null) return ret

        if(interfaces.isNotEmpty()) {
            val queue = ArrayDeque<ClassInstance>()
            queue.addAll(interfaces)

            var cls = this
            while(queue.poll()?.also { cls = it } != null) {
                ret = cls.getField(name, desc)
                if(ret != null) return ret

                cls.interfaces.forEach { itf ->
                    queue.addFirst(itf)
                }
            }
        }

        var cls = parent
        while(cls != null) {
            ret = cls!!.getField(name, desc)
            if(ret != null) return ret
            cls = cls!!.parent
        }

        return null
    }

    fun isInterface() = (access and ACC_INTERFACE) != 0
    fun isAbstract() = (access and ACC_ABSTRACT) != 0

    override fun toString(): String {
        return name
    }

    companion object {
        fun create(bytes: ByteArray, flags: Int = ClassReader.SKIP_FRAMES): ClassInstance {
            val node = ClassNode()
            val reader = ClassReader(bytes)
            reader.accept(node, flags)
            return ClassInstance(node)
        }
    }
}