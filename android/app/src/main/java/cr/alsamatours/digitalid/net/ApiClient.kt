package cr.alsamatours.digitalid.net

import cr.alsamatours.digitalid.model.Employee
import cr.alsamatours.digitalid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, message: String) : Exception(message)

data class AuthSession(val isAdmin: Boolean, val employee: Employee?)
data class InvitationCode(val email: String, val code: String, val expiresAt: String)

/** No credential is persisted to disk. App restart requires re-authentication. */
class ApiClient {
    private val apiOrigin = BuildConfig.API_BASE_URL.trimEnd('/')
    var accessToken: String? = null
        private set

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        // A pinned HTTPS origin prevents redirection of sign-in details to a custom host.
        val connection = (URL(apiOrigin + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                val token = accessToken ?: throw ApiException(401, "Debe iniciar sesión nuevamente.")
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        try {
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
            if (code !in 200..299) {
                throw ApiException(code, json.optString("error", json.optString("message", "Error HTTP $code")))
            }
            json
        } finally { connection.disconnect() }
    }

    suspend fun health() = request("/v1/health").optBoolean("ok", false)
    suspend fun publicStaff(slug: String): Employee = request("/v1/public/staff/${encode(slug)}").let {
        Employee.fromJson(it.optJSONObject("employee") ?: it)
    }
    suspend fun login(email: String, password: String): AuthSession {
        val response = request("/v1/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password))
        val token = response.optString("accessToken").ifBlank { response.optString("token") }
        if (token.isBlank() || !response.has("isAdmin")) throw ApiException(500, "El servidor no devolvió una sesión válida.")
        accessToken = token
        val person = response.optJSONObject("employee")?.let(Employee::fromJson)
        return AuthSession(response.optBoolean("isAdmin",false), person)
    }
    suspend fun myCredential(): Employee =
        request("/v1/me", authenticated = true).let { Employee.fromJson(it.getJSONObject("employee")) }

    suspend fun activate(email: String, activationCode: String, password: String) {
        request("/v1/auth/activate", "POST", JSONObject()
            .put("email", email.trim())
            .put("code", activationCode.trim())
            .put("password", password))
    }
    suspend fun inviteEmployee(employee: Employee): InvitationCode {
        val res = request("/v1/admin/employees/${encode(employee.id)}/invite", "POST", JSONObject(), true)
        return InvitationCode(res.getString("email"), res.getString("activationCode"), res.getString("expiresAt"))
    }
    suspend fun changePassword(currentPassword: String, newPassword: String) {
        request("/v1/auth/change-password", "POST", JSONObject()
            .put("currentPassword", currentPassword)
            .put("newPassword", newPassword), true)
        accessToken = null
    }
    suspend fun logout() {
        try { request("/v1/auth/logout", "POST", JSONObject(), true) } finally { accessToken = null }
    }
    suspend fun listEmployees(): List<Employee> {
        val json = request("/v1/admin/employees", authenticated = true)
        val arr: JSONArray = json.optJSONArray("employees") ?: json.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Employee::fromJson) }
    }
    suspend fun getEmployee(id: String): Employee = request("/v1/admin/employees/${encode(id)}", authenticated = true).let { Employee.fromJson(it.getJSONObject("employee")) }
    suspend fun save(employee: Employee): Employee {
        val path = if (employee.id.isBlank()) "/v1/admin/employees" else "/v1/admin/employees/${encode(employee.id)}"
        val response = request(path, if (employee.id.isBlank()) "POST" else "PUT", employee.toJson(), true)
        return Employee.fromJson(response.optJSONObject("employee") ?: response)
    }
    suspend fun changeActive(employee: Employee, active: Boolean) {
        request("/v1/admin/employees/${encode(employee.id)}/status", "PATCH", JSONObject().put("active", active), true)
    }
    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}