package dev.revtools.updater.remap

import dev.revtools.updater.asm.ClassGroup

class ClassHierarchy(group: ClassGroup) {

    private val classTrees = hashMapOf<String, ClassTree>()

    init {
        group.classes.forEach {
            val cls = it.node
            val tree = classTrees.computeIfAbsent(cls.name) { ClassTree(cls.name) }
            if(cls.superName != null) {
                val superTree = classTrees.computeIfAbsent(cls.superName) { ClassTree(cls.superName) }
                tree.addParent(superTree)
                superTree.addChild(tree)
            }
            if(cls.interfaces != null) {
                cls.interfaces.forEach { itf ->
                    val itfTree = classTrees.computeIfAbsent(itf) { ClassTree(itf) }
                    tree.addParent(itfTree)
                    itfTree.addChild(tree)
                }
            }
        }
        classTrees.values.forEach { it.computeRelationships() }
    }

    operator fun get(name: String): ClassTree? = classTrees[name]

    class ClassTree(val className: String) {

        private val parents = hashSetOf<ClassTree>()
        private val children = hashSetOf<ClassTree>()

        private val cachedAllParents = hashSetOf<ClassTree>()
        private val cachedAllChildren = hashSetOf<ClassTree>()

        fun getParents() = parents.map { it.className }
        fun getChildren() = children.map { it.className }

        fun getAllParents() = cachedAllParents.map { it.className }
        fun getAllChildren() = cachedAllChildren.map { it.className }

        fun addParent(entry: ClassTree) {
            parents.add(entry)
        }

        fun addChild(entry: ClassTree) {
            children.add(entry)
        }

        fun computeRelationships() {
            val queue = ArrayDeque<ClassTree>()

            queue.addAll(children)
            while(queue.isNotEmpty()) {
                val child = queue.removeFirst()
                cachedAllChildren.add(child)
                queue.addAll(child.children)
            }

            queue.addAll(parents)
            while(queue.isNotEmpty()) {
                val parent = queue.removeFirst()
                cachedAllParents.add(parent)
                queue.addAll(parent.parents)
            }
        }

        override fun hashCode(): Int = className.hashCode()
        override fun toString(): String = "CLASS[$className]"
        override fun equals(other: Any?): Boolean = other is ClassTree && other.className == className
    }
}