package cr.alsamatours.digitalid.net

import cr.alsamatours.digitalid.model.Employee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, message: String) : Exception(message)

/** No credential is persisted to disk. App restart requires re-authentication. */
class ApiClient(private val urlProvider: () -> String) {
    var accessToken: String? = null
        private set

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val root = urlProvider().trim().trimEnd('/')
        require(root.startsWith("https://")) { "Configure primero la URL HTTPS de la API en Ajustes." }
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
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
    data class LoginResult(val role: String, val employee: Employee?)
    data class Invitation(val email: String, val activationCode: String, val expiresAt: String)

    suspend fun login(email: String, password: String): LoginResult {
        val response = request("/v1/auth/login", "POST", JSONObject()
            .put("email", email.trim().lowercase()).put("password", password))
        val token = response.optString("accessToken")
        val role = response.optString("role")
        if (token.isBlank() || role !in listOf("admin", "employee")) {
            throw ApiException(500, "El servidor no devolvió una sesión válida.")
        }
        accessToken = token
        return LoginResult(role, response.optJSONObject("employee")?.let(Employee::fromJson))
    }

    suspend fun myCredential(): Employee =
        Employee.fromJson(request("/v1/me", authenticated = true).getJSONObject("employee"))

    suspend fun activate(email: String, code: String, newPassword: String) {
        request("/v1/auth/activate", "POST", JSONObject().put("email", email.trim().lowercase())
            .put("code", code.trim()).put("password", newPassword))
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        request("/v1/auth/change-password", "POST",
            JSONObject().put("currentPassword", currentPassword).put("newPassword", newPassword),
            authenticated = true)
        accessToken = null // The backend revokes sessions on password change.
    }

    suspend fun invite(employee: Employee): Invitation {
        val reply = request("/v1/admin/employees/${encode(employee.id)}/invite", "POST", JSONObject(), true)
        return Invitation(
            reply.getString("email"),
            reply.getString("activationCode"),
            reply.getString("expiresAt")
        )
    }
    suspend fun logout() {
        try { request("/v1/auth/logout", "POST", JSONObject(), true) } finally { accessToken = null }
    }
    suspend fun listEmployees(): List<Employee> {
        val json = request("/v1/admin/employees", authenticated = true)
        val arr: JSONArray = json.optJSONArray("employees") ?: json.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Employee::fromJson) }
    }
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