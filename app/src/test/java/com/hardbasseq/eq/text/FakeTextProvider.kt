package com.hardbasseq.eq.text

// Stands in for Android string resources in plain JVM tests: the same text id and arguments
// always give the same string, so a test can build the expected message the same way.
class FakeTextProvider : TextProvider {
    override fun get(
        id: Int,
        vararg args: Any?,
    ): String = "text$id" + if (args.isEmpty()) "" else args.joinToString(prefix = "(", postfix = ")", separator = "|")
}
