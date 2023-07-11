package dev.revtools.updater.asm

import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.ClassNode
import java.util.ArrayDeque

class ClassEntry(val group: ClassGroup, val id: String, val node: ClassNode) : Matchable<ClassEntry>() {

    init {
        if(group.isShared) {
            match = this
        }
    }

    val env get() = group.env

    val access = node.access
    val name = node.name

    var real: Boolean = true
    var elementClass: ClassEntry? = null

    var superClass: ClassEntry? = null
    val childClasses = hashSetOf<ClassEntry>()
    val interfaces = hashSetOf<ClassEntry>()
    val implementers = hashSetOf<ClassEntry>()
    val arrayClasses = hashSetOf<ClassEntry>()

    var outerClass: ClassEntry? = null
    val innerClasses = hashSetOf<ClassEntry>()

    val methodTypeRefs = hashSetOf<MethodEntry>()
    val fieldTypeRefs = hashSetOf<FieldEntry>()

    val strings = hashSetOf<String>()

    private val methodMap = hashMapOf<String, MethodEntry>()
    val methods get() = methodMap.values

    private val fieldMap = hashMapOf<String, FieldEntry>()
    val fields get() = fieldMap.values

    val memberMethods get() = methods.filter { !it.isStatic() }
    val memberFields get() = fields.filter { !it.isStatic() }

    val staticMethods get() = methods.filter { it.isStatic() }
    val staticFields get() = fields.filter { it.isStatic() }

    fun addMethod(method: MethodEntry) {
        methodMap[method.id] = method
    }

    fun addField(field: FieldEntry) {
        fieldMap[field.id] = field
    }

    fun getMethod(name: String, desc: String) = methodMap["$name$desc"]
    fun getField(name: String, desc: String) = fieldMap["$name:$desc"]

    fun resolveMethod(name: String, desc: String, toInterface: Boolean): MethodEntry? {
        if(!toInterface) {
            var ret = getMethod(name, desc)
            if(ret != null) return ret

            var cls: ClassEntry? = this.superClass
            while(cls != null) {
                ret = cls.getMethod(name, desc)
                if(ret != null) return ret
                cls = cls.superClass
            }

            return resolveInterfaceMethod(name, desc)
        } else {
            var ret = getMethod(name, desc)
            if(ret != null) return ret

            if(superClass != null) {
                ret = superClass!!.getMethod(name, desc)
                if(ret != null && (ret.access and (ACC_PUBLIC or ACC_STATIC)) == ACC_PUBLIC) return ret
            }

            return resolveInterfaceMethod(name, desc)
        }
    }

    private fun resolveInterfaceMethod(name: String, desc: String): MethodEntry? {
        val queue = ArrayDeque<ClassEntry>()
        val visited = hashSetOf<ClassEntry>()

        var cls: ClassEntry? = this
        do {
            cls!!.interfaces.forEach { itf ->
                if(visited.add(itf)) queue.add(itf)
            }
            cls = cls.superClass
        } while(cls != null)

        if(queue.isEmpty()) return null

        val matches = hashSetOf<MethodEntry>()
        var foundNonAbstract = false

        cls = queue.poll()
        while(cls != null) {
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
            cls = queue.poll()
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
            val m = itr.next()
            cmpLoop@ for(m2 in matches) {
                if(m2 == m) continue
                if(m2.cls.interfaces.contains(m.cls)) {
                    itr.remove()
                    break
                }

                queue.addAll(m2.cls.interfaces)
                cls = queue.poll()
                while(cls != null) {
                    if(cls.interfaces.contains(m.cls)) {
                        itr.remove()
                        queue.clear()
                        break@cmpLoop
                    }
                    queue.addAll(cls.interfaces)
                    cls = queue.poll()
                }
            }
        }
        return matches.iterator().next()
    }

    fun resolveField(name: String, desc: String): FieldEntry? {
        var ret = getField(name, desc)
        if(ret != null) return ret

        if(interfaces.isNotEmpty()) {
            val queue = ArrayDeque<ClassEntry>()
            queue.addAll(interfaces)

            var cls: ClassEntry? = queue.pollFirst()
            while(cls != null) {
                ret = cls.getField(name, desc)
                if(ret != null) return ret
                cls.interfaces.forEach { itf ->
                    queue.addFirst(itf)
                }
                cls = queue.pollFirst()
            }
        }

        var cls = superClass
        while(cls != null) {
            ret = cls.getField(name, desc)
            if(ret != null) return ret
            cls = cls.superClass
        }

        return null
    }

    fun isArray() = elementClass != null

    val dims: Int get() {
        if(!isArray()) return 0
        return id.lastIndexOf('[') + 1
    }

    fun isInterface() = (access and ACC_INTERFACE) != 0
    fun isAbstract() = (access and ACC_ABSTRACT) != 0

    fun isPrimitive(): Boolean {
        val start = id[0]
        return start != 'L' && start != '['
    }

    override fun toString(): String {
        return name
    }

    companion object {

        fun getId(name: String): String {
            return if(name[0] == '[') name else "L$name;"
        }
    }
}