package org.caojun.shotocr.parser

interface DataParser<T> {
    fun parse(text: String): T
}
