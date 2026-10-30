package cr.alsamatours.digitalid.net

import android.content.Context
import cr.alsamatours.digitalid.model.Employee
import cr.alsamatours.digitalid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, message: String) : Exception(message)

data class AuthSession(val isAdmin: Boolean, val employee: Employee?, val email: String)
data class InvitationCode(val email: String, val code: String, val expiresAt: String)

/** Short-lived access tokens stay in memory; encrypted refresh credentials survive restarts. */
class ApiClient(context: Context) {
    private val sessionStore = SecureSessionStore(context.applicationContext)
    private val refreshMutex = Mutex()
    private val apiOrigin = BuildConfig.API_BASE_URL.trimEnd('/')
    var accessToken: String? = null
        private set

    private suspend fun rawRequest(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        // This fixed HTTPS endpoint does not follow redirects, including for auth.
        val connection = (URL(apiOrigin + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                val token = accessToken ?: throw ApiException(401, "Debes iniciar sesión nuevamente.")
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
                val err = json.optJSONObject("error")
                throw ApiException(code, err?.optString("message") ?: "Error HTTP $code")
            }
            json
        } finally { connection.disconnect() }
    }

    private fun saveSession(response: JSONObject): AuthSession {
        val access = response.optString("accessToken")
        val refresh = response.optString("refreshToken")
        if (!Regex("[0-9a-fA-F]{64}").matches(access) ||
            !Regex("[0-9a-fA-F]{64}").matches(refresh) ||
            !response.has("isAdmin")) throw ApiException(500, "Respuesta de sesión inválida.")
        sessionStore.save(refresh)
        accessToken = access
        val person = response.optJSONObject("employee")?.let(Employee::fromJson)
        val isAdmin = response.getBoolean("isAdmin")
        val email = if (isAdmin) response.optJSONObject("admin")?.optString("email").orEmpty()
                    else person?.email.orEmpty()
        return AuthSession(isAdmin,person,email)
    }

    private suspend fun refreshSession(): AuthSession? = refreshMutex.withLock {
        val token = sessionStore.read() ?: return@withLock null
        try {
            val response = rawRequest("/v1/auth/refresh", "POST", JSONObject().put("refreshToken",token))
            return@withLock saveSession(response)
        } catch (e: ApiException) {
            if (e.status == 401) clearSession()
            throw e
        }
    }

    suspend fun restoreSession(): AuthSession? {
        if (sessionStore.read() == null) return null
        return try { refreshSession() } catch (e: ApiException) {
            if(e.status==401) null else throw e
        }
    }

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = false): JSONObject {
        if (authenticated && accessToken == null) {
            if(refreshSession()==null) throw ApiException(401,"Debes iniciar sesión.")
        }
        try {
            return rawRequest(path,method,body,authenticated)
        } catch (e: ApiException) {
            if (!authenticated || e.status != 401 || refreshSession()==null) throw e
            return rawRequest(path,method,body,authenticated)
        }
    }

    private fun clearSession() {
        sessionStore.clear()
        accessToken=null
    }

    suspend fun health() = request("/v1/health").optBoolean("ok", false)
    suspend fun publicStaff(slug: String): Employee = request("/v1/public/staff/${encode(slug)}").let {
        Employee.fromJson(it.optJSONObject("employee") ?: it)
    }
    suspend fun login(email: String, password: String): AuthSession {
        val res=request("/v1/auth/login","POST",JSONObject().put("email",email.trim()).put("password",password))
        return saveSession(res)
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
        clearSession()
    }
    suspend fun logout() {
        val refresh=sessionStore.read()
        try {
            if(refresh!=null) rawRequest("/v1/auth/logout","POST",JSONObject().put("refreshToken",refresh))
            else if(accessToken!=null) rawRequest("/v1/auth/logout","POST",JSONObject(),true)
        } finally { clearSession() }
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