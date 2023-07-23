package dev.revtools.updater.classifier

enum class RankerLevel {
    INITIAL,
    INTERMEDIATE,
    FULL,
    EXTRA;

    companion object {
        val ALL = values()
    }
}