package dev.revtools.updater.asm

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.FileSystemNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayDeque
import java.util.jar.JarFile

class ClassGroup(val env: ClassEnv, val isShared: Boolean) {

    private val classMap = hashMapOf<String, ClassEntry>()
    val classes get() = classMap.values

    private val arrayClassMap = hashMapOf<String, ClassEntry>()
    val arrayClasses get() = arrayClassMap.values

    fun init(file: File) {
        JarFile(file).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                if(entry.name.endsWith(".class")) {
                    val cls = readClass(jar.getInputStream(entry).readAllBytes())
                    classMap[cls.id] = cls
                }
            }
        }
    }

    fun addClass(cls: ClassEntry) {
        if(classMap.containsKey(cls.id)) return
        classMap[cls.id] = cls
    }

    fun getClass(name: String): ClassEntry? {
        assert(name[0] != 'L')
        return if(name[0] == '[') arrayClassMap[name] else classMap[ClassEntry.getId(name)]
    }

    fun getClassById(id: String): ClassEntry? {
        assert(id[0] == '[' || id[0] == 'L')
        return if(id[0] == '[') arrayClassMap[id] else classMap[id]
    }

    fun getCreateClass(name: String): ClassEntry {
        assert(name[0] != 'L')
        return getCreateClassById(ClassEntry.getId(name))
    }

    fun getCreateClassById(id: String): ClassEntry {
        var ret: ClassEntry?

        if(id[0] == '[') {
            ret = env.getSharedClassById(id)
            if(ret != null) return ret

            ret = arrayClassMap[id]
            if (ret != null) return ret

            val elementId = id.substring(id.lastIndexOf('[') + 1)
            val elementCls = getCreateClassById(elementId)

            val node = ClassNode()
            node.visit(V1_1, ACC_PUBLIC and ACC_SUPER, getClassName(id), null, "java/lang/Object", emptyArray())

            val cls = ClassEntry(elementCls.group, id, node)
            cls.elementClass = elementCls
            elementCls.arrayClasses.add(cls)

            if(elementCls.group.isShared) {
                env.addSharedClass(cls)
                ret = cls
            } else {
                arrayClassMap[id] = cls
                ret = cls
            }
        } else {
            ret = getClassById(id)
            if(ret != null) return ret

            ret = env.getSharedClassById(id)
            if(ret != null) return ret

            ret = getMissingClass(id)
        }

        return ret
    }

    private fun getMissingClass(id: String): ClassEntry {
        var name = id
        if(name.length > 1) {
            name = if(name[0] == '[') name else name.substring(1, name.length - 1)
        }

        if(name.length == 1) {
            name = "java/lang/" + Type.getType(name).className.replaceFirstChar { it.uppercase() }
        }

        if(name.length > 1) {
            var file: Path? = null
            val url = ClassLoader.getSystemResource("$name.class")
            if(url != null) {
                file = getPath(url)
            }

            if(file != null) {
                val cls = env.sharedGroup.readClass(Files.readAllBytes(file), flags = ClassReader.SKIP_FRAMES or ClassReader.SKIP_CODE)
                if(env.addSharedClass(cls)) {
                    processA(cls)
                }
                return cls
            }
        }

        val node = ClassNode()
        node.version = V1_1
        node.access = ACC_PUBLIC and ACC_SUPER
        node.name = name
        node.superName = if(name == "java/lang/Object") null else "java/lang/Object"

        val ret = ClassEntry(env.sharedGroup, id, node)
        ret.real = false
        env.addSharedClass(ret)

        return ret
    }

    private fun getPath(url: URL): Path? {
        return try {
            val uri = url.toURI()
            var ret = Paths.get(uri)
            if(uri.scheme == "jrt" && !Files.exists(ret)) {
                ret = Paths.get(URI(uri.scheme, uri.userInfo, uri.host, uri.port, "/modules${uri.path}", uri.query, uri.fragment))
            }
            ret
        } catch(e: FileSystemNotFoundException) {
            null
        }
    }

    private fun getClassName(id: String): String {
        var name = id
        if(name.length > 1) {
            name = if(name[0] == '[') name else name.substring(1, name.length - 1)
        }

        if(name.length == 1) {
            name = "java/lang/" + Type.getType(name).className.replaceFirstChar { it.uppercase() }
        }

        return name
    }

    fun process() {
        // Create base object shared class.
        env.sharedGroup.getCreateClass("java/lang/Object")

        // Step A
        classes.forEach { cls ->
            processA(cls)
        }

        // Step B
        classes.forEach { cls ->
            processB(cls)
        }

        // Step C
        classes.forEach { cls ->
            processC(cls)
        }
    }

    private fun processA(cls: ClassEntry) {
        val cn = cls.node

        cn.methods.forEach { mn ->
            val method = MethodEntry(cls, mn)
            cls.addMethod(method)
        }

        cn.fields.forEach { fn ->
            val field = FieldEntry(cls, fn)
            cls.addField(field)
        }

        if(cls.outerClass == null) processOuterClass(cls)

        if(cls.node.superName != null && cls.superClass == null) {
            cls.superClass = getCreateClass(cls.node.superName)
            cls.superClass!!.childClasses.add(cls)
        }
        cls.node.interfaces.map { getCreateClass(it) }.forEach { itf ->
            cls.interfaces.add(itf)
            itf.implementers.add(cls)
        }
    }

    fun processOuterClass(cls: ClassEntry) {
        val cn = cls.node
        if(cn.outerClass != null) {
            addOuterClass(cls, cn.outerClass)
        } else if(cn.outerMethod != null) {
            throw UnsupportedOperationException()
        } else {
            for(innerCls in cn.innerClasses) {
                if(innerCls.name == cn.name) {
                    if(innerCls.outerName == null) break
                    else {
                        addOuterClass(cls, innerCls.outerName)
                        return
                    }
                }
            }
            val pos = cn.name.lastIndexOf('$')
            if(pos > 0 && pos < cn.name.length - 1) {
                addOuterClass(cls, cn.name.substring(0, pos))
            }
        }
    }

    private fun addOuterClass(cls: ClassEntry, name: String) {
       var outerCls = cls.group.getClass(name)
        if(outerCls == null) {
            outerCls = cls.group.getCreateClass(name)
        }
        cls.outerClass = outerCls
        outerCls.innerClasses.add(cls)
    }

    private fun processB(cls: ClassEntry) {
        cls.methods.forEach { method ->
            val queue = ArrayDeque<ClassEntry>()
            val visited = hashSetOf<ClassEntry>()

            if(method.cls.superClass != null) queue.add(method.cls.superClass!!)
            queue.addAll(method.cls.interfaces)

            var c: ClassEntry = method.cls
            while(queue.poll()?.also { c = it } != null) {
                if(!visited.add(c)) continue

                val m = c.getMethod(method.name, method.desc)
                if(m != null) {
                    method.parent = m
                    m.children.add(method)
                } else {
                    if(c.superClass != null) queue.add(c.superClass!!)
                    queue.addAll(c.interfaces)
                }
            }
        }

        cls.fields.forEach { field ->
            val queue = ArrayDeque<ClassEntry>()
            val visited = hashSetOf<ClassEntry>()

            if(field.cls.superClass != null) queue.add(field.cls.superClass!!)
            queue.addAll(field.cls.interfaces)

            var c = field.cls
            while(queue.poll()?.also { c = it } != null) {
                if(!visited.add(c)) continue

                val f = c.getField(field.name, field.desc)
                if(f != null) {
                    field.parent = f
                    f.children.add(field)
                } else {
                    if(c.superClass != null) queue.add(c.superClass!!)
                    queue.addAll(c.interfaces)
                }
            }
        }
    }

    private fun processC(cls: ClassEntry) {
        cls.methods.forEach { method ->
            val insns = method.instructions.iterator()
            while(insns.hasNext()) {
                val insn = insns.next()
                when(insn) {
                    is MethodInsnNode -> {
                        val owner = getCreateClass(insn.owner)
                        val dst = owner.resolveMethod(insn.name, insn.desc, insn.itf) ?: continue

                        dst.refsIn.add(method)
                        method.refsOut.add(dst)
                        dst.cls.methodTypeRefs.add(method)
                        method.classRefs.add(dst.cls)
                    }

                    is FieldInsnNode -> {
                        val owner = getCreateClass(insn.owner)
                        val dst = owner.resolveField(insn.name, insn.desc) ?: continue

                        if(insn.opcode in arrayOf(GETFIELD, GETSTATIC)) {
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

    private fun readClass(bytes: ByteArray, flags: Int = ClassReader.SKIP_FRAMES): ClassEntry {
        val node = ClassNode()
        val reader = ClassReader(bytes)
        reader.accept(node, flags)
        return ClassEntry(this, ClassEntry.getId(node.name), node)
    }
}