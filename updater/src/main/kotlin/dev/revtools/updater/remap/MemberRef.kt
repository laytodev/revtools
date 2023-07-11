package dev.revtools.updater.remap

data class MemberRef(val owner: String, val name: String, val desc: String) : Comparable<MemberRef> {

    override fun compareTo(other: MemberRef): Int {
        var result = owner.compareTo(other.owner)
        if (result != 0) {
            return result
        }

        result = name.compareTo(other.name)
        if (result != 0) {
            return result
        }

        return desc.compareTo(other.desc)
    }

    override fun toString(): String {
        return "$owner.$name $desc"
    }
}