package cr.alsamatours.digitalid.model

import org.json.JSONObject

data class Employee(
    val id: String = "",
    val slug: String = "",
    val fullName: String = "",
    val jobTitle: String = "",
    val profileType: String = "sales",
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
            profileType = o.optString("profileType","sales"),
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
        if (profileType == "driver") {
            // A driver has only their name at creation. Corporate contacts are server-owned.
            put("fullName", fullName.trim())
            if (id.isBlank()) put("profileType", "driver")
            else {
                put("active", active)
                put("publicVisible", publicVisible)
            }
        } else {
            put("profileType", profileType)
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
}
