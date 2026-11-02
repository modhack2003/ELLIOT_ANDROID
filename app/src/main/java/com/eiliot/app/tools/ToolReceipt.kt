package com.eiliot.app.tools

import com.eiliot.app.ui.OutputFormat
import org.json.JSONObject

object ToolReceipt {
    fun success(name: String, result: String, maxChars: Int = 11500): String {
        require(maxChars >= 500)
        fun encode(size: Int) = JSONObject().put("ok", true).put("tool", name).put("result", OutputFormat.prefix(result, size))
            .put("truncated", size < result.length).toString()
        var low = 0; var high = minOf(result.length, maxChars)
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (encode(mid).length <= maxChars) low = mid else high = mid - 1
        }
        return encode(low)
    }
}
