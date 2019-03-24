package org.projectforge.rest.json

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.aspectj.weaver.tools.cache.SimpleCacheFactory.path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class JsonValidator {
    private val map: Map<String, Any?>

    private val attrPattern = """[a-z_0-9-]*"""
    private val attrReqex = attrPattern.toRegex(RegexOption.IGNORE_CASE)
    private val attrPatternWithIndex = """[a-z_0-9-]*\[([0-9]+)\]"""
    private val attrRegexWithIndex = attrPatternWithIndex.toRegex(RegexOption.IGNORE_CASE)

    constructor(json: String) {
        map = parseJson(json)
    }

    fun assert(expected: String?, path: String) {
        var currentMap: Map<*, *>? = map
        var result: String? = null
        val pathValues = path.split('.')
        pathValues.forEach {
            if (currentMap == null) {
                throw IllegalArgumentException("Can't step so deep: '${path}'. '${it}' doesn't exist.")
            }
            if (it.isNullOrBlank())
                throw IllegalArgumentException("Illegal path: '${path}' contains empty attributes such as 'a..b'.")

            var idx: Int? = null;
            var attr = it
            var value: Any? = null
            if (it.indexOf('[') > 0) {
                // Array found:
                if (!it.matches(attrRegexWithIndex)) {
                    throw IllegalArgumentException("Illegal path: '${path}' contains illegal attribute ('${attrPattern}' or '${attrPatternWithIndex}' are supported: '${it}'.")
                }
                attr = it.substring(0, it.indexOf('['))
                idx = attrRegexWithIndex.find(it)!!.groups[1]?.value?.toInt()
                if (idx == null)
                    throw IllegalArgumentException("Illegal path: '${path}' contains illegal attribute ('${attrPattern}' or '${attrPatternWithIndex}' are supported: '${it}'.")

                val arr = currentMap?.get(attr)
                if (arr == null || !(arr is List<*>)) {
                    throw IllegalArgumentException("Illegal path: '${attr}' not found as array: '${path}': '${arr}'")
                }
                value = arr[idx]
            } else if (!it.matches(attrReqex)) {
                throw IllegalArgumentException("Illegal path: '${path}' contains illegal attribute ('${attrPattern}' or '${attrPatternWithIndex}' are supported: '${it}'.")
            } else {
                value = currentMap?.get(attr)
            }
            if (value == null) {
                currentMap = null
                result = null
            } else {
                if (value is Map<*, *>) {
                    currentMap = value as Map<String, Any?>
                } else if (value is String) {
                    result = value
                    currentMap = null
                } else if (value is List<*>) {
                    if (idx == null) {

                    }
                } else {
                    throw IllegalArgumentException("Found unexpected type '${value::class.java}' in path '${path}'. Current element is '${it}'")
                }
            }
        }
        if (result == null) {
            assertNull(expected, "Expected '${expected}' but found null for path '${path}'.")
        } else if (result is String) {
            assertEquals(expected, result, "Expected '${expected}' but found '${result}' for path '${path}'.")
        } else {
            throw IllegalArgumentException("Can't step so deep: '${path}'")
        }
    }

    private fun parseJson(json: String): Map<String, Any?> {
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson<Map<String, String>>(json, mapType)
    }
}