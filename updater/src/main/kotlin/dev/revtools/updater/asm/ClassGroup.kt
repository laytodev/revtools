package dev.revtools.updater.asm

import dev.revtools.updater.util.identityHashSetOf
import dev.revtools.updater.util.isObfuscatedName
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayDeque
import java.util.jar.JarFile
import kotlin.math.max

class ClassGroup(val env: ClassEnv, val isShared: Boolean) {

    val otherGroup get() = if(env.groupA == this) env.groupB else env.groupA
    val sharedGroup get() = env.sharedGroup

    fun isGroupA() = this == env.groupA
    fun isGroupB() = this == env.groupB

    private val classMap = hashMapOf<String, ClassInstance>()
    val classes get() = classMap.values

    private val arrayClassMap = hashMapOf<String, ClassInstance>()
    val arrayClasses get() = arrayClassMap.values

    fun addClass(cls: ClassInstance): Boolean {
        if(classMap.containsKey(cls.name)) return false
        cls.group = this
        cls.isObfuscated = cls.name.isObfuscatedName()
        classMap[cls.name] = cls
        return true
    }

    fun getClass(name: String) = classMap[name] ?: arrayClassMap[name]

    fun getCreateClass(id: String): ClassInstance {
        val name = if(id[0] == '[') id else if(id[0] == 'L') id.substring(1, id.length - 1) else id

        var ret = getClass(name)
        if(ret != null) return ret

        ret = sharedGroup.getClass(name)
        if(ret != null) return ret

        var path: Path? = null
        val url = ClassLoader.getSystemResource("$name.class")
        if(url != null) {
            path = try {
                val uri = url.toURI()
                var res = Paths.get(uri)
                if(uri.scheme == "jrt" && !Files.exists(res)) {
                    res = Paths.get(URI(uri.scheme, uri.userInfo, uri.host, uri.port, "/modules${uri.path}", uri.query, uri.fragment))
                }
                res
            } catch(e: Exception) {
                null
            }
        }

        if(path != null) {
            val cls = ClassInstance.create(Files.readAllBytes(path))
            if(env.sharedGroup.addClass(cls)) {
                processA(cls)
            }
            return cls
        }

        val node = ClassNode()
        node.version = V1_1
        node.access = ACC_PUBLIC or ACC_SUPER
        node.name = name
        node.superName = "java/lang/Object"

        val cls = ClassInstance(node)
        env.sharedGroup.addClass(cls)

        return cls
    }

    fun init(file: File) {
        JarFile(file).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                if(entry.name.endsWith(".class")) {
                    val bytes = jar.getInputStream(entry).readAllBytes()
                    val cls = ClassInstance.create(bytes)
                    addClass(cls)
                }
            }
        }
    }

    fun process() {
        classes.forEach { processA(it) }
        classes.forEach { processB(it) }
        classes.forEach { processC(it) }
    }

    private fun processA(cls: ClassInstance) {
        cls.node.methods.forEach {
            val method = MethodInstance(cls, it)
            method.isObfuscated = method.name.isObfuscatedName()
            cls.methods.add(method)
        }

        cls.node.fields.forEach {
            val field = FieldInstance(cls, it)
            field.isObfuscated = field.name.isObfuscatedName()
            cls.fields.add(field)
        }

        if(cls.parent == null && cls.node.superName != null) {
            cls.parent = getCreateClass(cls.node.superName)
            cls.parent?.children?.add(cls)
        }

        cls.node.interfaces.map { getCreateClass(it) }.forEach { itf ->
            cls.interfaces.add(itf)
            itf.implementers.add(cls)
        }

        cls.methods.forEach { method ->
            method.retType = getCreateClass(method.type.returnType.descriptor)
            method.argTypes.addAll(method.type.argumentTypes.map { getCreateClass(it.descriptor) })

            method.classRefs.add(method.retType)
            method.retType.methodTypeRefs.add(method)

            method.argTypes.forEach { arg ->
                method.classRefs.add(arg)
                arg.methodTypeRefs.add(method)
            }
        }

        cls.fields.forEach { field ->
            field.typeClass = getCreateClass(field.type.descriptor)
            field.typeClass.fieldTypeRefs.add(field)
        }
    }

    private fun processB(cls: ClassInstance) {
        cls.methods.forEach methodLoop@{ method ->
            method.instructions.forEach { insn ->
                when(insn) {
                    is MethodInsnNode -> {
                        val owner = getCreateClass(insn.owner)
                        val dst = owner.resolveMethod(insn.name, insn.desc, (insn.itf || insn.opcode == INVOKEINTERFACE))
                        if(dst == null) {
                            println("Missing synthetic method: ${insn.owner}.${insn.name}${insn.desc}.")
                            return@forEach
                        }

                        dst.refsIn.add(method)
                        method.refsOut.add(dst)
                        dst.cls.methodTypeRefs.add(method)
                        method.classRefs.add(dst.cls)
                    }

                    is FieldInsnNode -> {
                        val owner = getCreateClass(insn.owner)
                        val dst = owner.resolveField(insn.name, insn.desc)
                        if(dst == null) {
                            println("Missing synthetic field: ${insn.owner}.${insn.name}.")
                            return@forEach
                        }

                        if(insn.opcode in arrayOf(GETSTATIC, GETFIELD)) {
                            dst.readRefs.add(method)
                            method.fieldReadRefs.add(dst)
                        } else {
                            dst.writeRefs.add(method)
                            method.fieldWriteRefs.add(dst)
                        }

                        dst.cls.methodTypeRefs.add(method)
                        method.classRefs.add(dst.cls)
                    }

                    is TypeInsnNode -> {
                        val dst = getCreateClass(insn.desc)

                        dst.methodTypeRefs.add(method)
                        method.classRefs.add(dst)
                    }
                }
            }
        }
    }

    private fun processC(cls: ClassInstance) {
        val queue = ArrayDeque<ClassInstance>()
        val visited = identityHashSetOf<ClassInstance>()

        cls.methods.forEach { method ->
            if(method.isConstructor() || method.isInitializer()) return@forEach
            if(method.isHierarchyBarrier()) return@forEach

            if(method.cls.parent != null) queue.add(method.cls.parent!!)
            queue.addAll(method.cls.interfaces)

            var c: ClassInstance = cls
            while(queue.poll()?.also { c = it } != null) {
                if(!visited.add(c)) continue
                val m = c.getMethod(method.name, method.desc)
                if(m != null && !m.isHierarchyBarrier()) {
                    method.parents.add(m)
                    m.children.add(method)
                }
                if(c.parent != null) queue.add(c.parent!!)
                queue.addAll(c.interfaces)
            }
            visited.clear()
        }

        queue.clear()
        visited.clear()

        cls.fields.forEach { field ->
            if(field.isHierarchyBarrier()) return@forEach

            if(field.cls.parent != null) queue.add(field.cls.parent!!)
            queue.addAll(field.cls.interfaces)

            var c: ClassInstance = cls
            while(queue.poll()?.also { c = it } != null) {
                if(!visited.add(c)) continue
                val f = c.getField(field.name, field.desc)
                if(f != null && !f.isHierarchyBarrier()) {
                    field.parents.add(f)
                    f.children.add(field)
                }
                if(c.parent != null) queue.add(c.parent!!)
                queue.addAll(c.interfaces)
            }

            visited.clear()
        }
    }

    private fun MethodInstance.isHierarchyBarrier(): Boolean {
        return (access and (ACC_PRIVATE or ACC_STATIC)) != 0
    }

    private fun FieldInstance.isHierarchyBarrier(): Boolean {
        return (access and (ACC_PRIVATE or ACC_STATIC)) != 0
    }
}