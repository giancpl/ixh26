package com.example.photoanchor

import org.json.JSONObject

/** Collection targets only. This state never authorizes a PRNU match. */
data class CalibrationGuide(
    val id: String,
    val state: String,
    val guided: Boolean,
    val roles: Map<String, String>,
    val camera: String,
    val mode: String,
    val demo: Boolean = false,
) {
    fun count(role: String) = roles.values.count { it == role }
    val nextRole: String? get() = if (!guided || state != "COLLECTING") null
        else targets.keys.firstOrNull { count(it) < targets.getValue(it) }
    val ready: Boolean get() = guided && state == "COLLECTING" && targets.all { count(it.key) >= it.value }
    val summary: String get() = targets.entries.joinToString(" · ") { "${labels.getValue(it.key)} ${count(it.key)}/${it.value}" }

    // Recover a successful upload whose reply/draft cleanup was lost. In
    // particular, never reassign frame 30 to development on a boundary retry.
    fun roleFor(digest: String): String {
        require(digest.matches(Regex("[0-9a-f]{64}")))
        require(guided)
        return roles[digest] ?: requireNotNull(nextRole) { "Collection is complete or unavailable" }
    }

    companion object {
        const val PLAN = "photo/prnu-single-pixel/1"
        val targets: Map<String, Int> = linkedMapOf("enrollment" to 30, "development" to 10, "genuine" to 10)
        val labels = mapOf("enrollment" to "Reference", "development" to "Development", "genuine" to "Held-out")
        fun fromJson(value: JSONObject, guided: Boolean): CalibrationGuide {
            val id = value.getString("id")
            require(id.matches(Regex("[0-9a-f]{32}")))
            val frames = value.getJSONArray("frames")
            require(frames.length() <= 128)
            val roles = linkedMapOf<String, String>()
            for (i in 0 until frames.length()) {
                val frame = frames.getJSONObject(i)
                val digest = frame.getString("digest"); val role = frame.getString("role")
                require(digest.matches(Regex("[0-9a-f]{64}")) && role in targets.keys + "impostor")
                require(roles.put(digest, role) == null) { "Duplicated calibration frame" }
            }
            return CalibrationGuide(id, value.getString("state"), guided, roles.toMap(), "", "")
        }
    }
}
