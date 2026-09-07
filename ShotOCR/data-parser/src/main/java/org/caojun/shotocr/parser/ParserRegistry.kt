package org.caojun.shotocr.parser

object ParserRegistry {

    private val parsers = mutableMapOf<String, DataParser<*>>()

    init {
        register("receipt", ReceiptParser())
    }

    fun <T> register(name: String, parser: DataParser<T>) {
        parsers[name] = parser
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getParser(name: String): DataParser<T>? {
        return parsers[name] as? DataParser<T>
    }

    fun listParsers(): List<String> = parsers.keys.toList()
}
