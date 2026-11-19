package com.thehhr.form.data

import org.json.JSONArray

/** Built-in exercises (from www/data/exercises.js) plus the vault's custom exercises. */
class Catalog(val builtIn: List<Exercise>) {
    @Volatile private var customs: List<Exercise> = emptyList()
    @Volatile private var index: Map<String, Exercise> = builtIn.associateBy { it.id }

    val all: List<Exercise> get() = builtIn + customs
    fun byId(id: String): Exercise? = index[id]
    fun isValid(id: String) = index.containsKey(id)

    val equipmentOptions: List<String> = listOf("body weight") +
        builtIn.map { it.equipment.trim().lowercase() }.filter { it.isNotEmpty() && it != "body weight" }.distinct().sorted()

    fun setCustoms(list: List<Exercise>) {
        customs = list
        index = (builtIn + list).associateBy { it.id }
    }

    /** First free 5-digit id (the web app's nextCustomExerciseId). */
    fun nextCustomId(taken: Set<String> = emptySet()): String {
        val used = index.keys + taken
        var n = 1
        while (n.toString().padStart(5, '0') in used) n++
        return n.toString().padStart(5, '0')
    }

    /** The web app's normalizeCustomExercise. */
    fun normalizeCustom(raw: Map<String, String>, taken: Set<String> = emptySet()): Exercise? {
        val name = (raw["name"] ?: "").trim().take(80)
        val category = (raw["category"] ?: "").trim().lowercase()
        if (name.isEmpty() || category !in Exercise.CATEGORIES) return null
        val equipment = raw["equipment"]?.takeIf { it in equipmentOptions } ?: "body weight"
        val id = (raw["id"] ?: "").trim()
        // fix: anchored; the web regex accepted `c-1" onfocus=...` and injected it into HTML.
        val validId = Regex("^(\\d{5}|c-\\d+)$").matches(id) && id !in taken
        return Exercise(
            id = if (validId) id else nextCustomId(taken),
            name = name,
            category = category,
            equipment = equipment,
            target = (raw["target"] ?: "").trim().lowercase().ifEmpty { category },
            description = (raw["description"] ?: "").trim().take(300),
            custom = true,
        )
    }

    /** Registers config.md customs in order (ids are allocated one by one, so a batch can't collide). */
    fun registerCustoms(raws: List<Map<String, String>>): List<Exercise> {
        val out = mutableListOf<Exercise>()
        val taken = mutableSetOf<String>()
        for (raw in raws) {
            val e = normalizeCustom(raw, taken) ?: continue
            if (builtIn.any { it.id == e.id } || out.any { it.name.equals(e.name, true) }) continue
            out += e
            taken += e.id
        }
        setCustoms(out)
        return out
    }

    companion object {
        /** Parses `window.FORM_EXERCISES=[...];`. */
        fun parse(js: String): Catalog {
            val start = js.indexOf('[')
            val end = js.lastIndexOf(']')
            val arr = JSONArray(js.substring(start, end + 1))
            val list = ArrayList<Exercise>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Exercise(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    category = o.optString("category"),
                    equipment = o.optString("equipment"),
                    target = o.optString("target"),
                    muscleGroup = o.optString("muscle_group"),
                    secondary = o.optJSONArray("secondary_muscles").strings(),
                    instructions = o.optJSONObject("instruction_steps")?.optJSONArray("en").strings(),
                    image = o.optString("image"),
                    gif = o.optString("gif_url"),
                )
            }
            return Catalog(list)
        }

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else List(length()) { optString(it) }

    }
}
