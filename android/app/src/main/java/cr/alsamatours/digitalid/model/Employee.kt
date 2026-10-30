package cr.alsamatours.digitalid.model

import org.json.JSONObject

data class Employee(
    val id: String = "",
    val slug: String = "",
    val fullName: String = "",
    val jobTitle: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val active: Boolean = true,
    val publicVisible: Boolean = true
) {
    companion object {
        fun fromJson(o: JSONObject) = Employee(
            id = o.optString("id"),
            slug = o.optString("slug"),
            fullName = o.optString("fullName"),
            jobTitle = o.optString("jobTitle"),
            email = o.optString("email"),
            phone = o.optString("phone"),
            photoUrl = o.optString("photoUrl").takeUnless { it == "null" } ?: "",
            active = o.optBoolean("active", true),
            publicVisible = o.optBoolean("publicVisible", true)
        )
        val sample = Employee(
            id = "preview-only",
            slug = "sebastian-melendez-rodriguez",
            fullName = "Sebastian Melendez Rodriguez",
            jobTitle = "Ejecutivo en ventas",
            email = "Sebastian.melendez@alsamatourscr.com",
            phone = "+506 7118 0081"
        )
    }
    fun toJson() = JSONObject().apply {
        put("slug", slug.trim())
        put("fullName", fullName.trim())
        put("jobTitle", jobTitle.trim())
        put("email", email.trim())
        put("phone", phone.trim())
        put("photoUrl", photoUrl.trim().ifBlank { JSONObject.NULL })
        put("active", active)
        put("publicVisible", publicVisible)
    }
}