package com.surimap.feature.bootstrap

import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.feature.bootstrap.data.AuthBootstrapCoordinator
import com.surimap.feature.bootstrap.data.AuthBootstrapEnvironmentCheck
import com.surimap.feature.bootstrap.data.AuthBootstrapServerCheck
import com.surimap.feature.bootstrap.data.AndroidManagedConfigurationReader
import com.surimap.feature.bootstrap.data.DebugManagedConfigurationOverrideProvider
import com.surimap.feature.bootstrap.data.ManagedPolicePhoneConfig
import com.surimap.feature.bootstrap.data.NetworkAuthBootstrapEnvironmentCheck
import com.surimap.feature.bootstrap.data.NetworkPolicePhoneBootstrapServerCheck
import com.surimap.feature.bootstrap.ui.AuthBootstrapFailureReason
import com.surimap.feature.bootstrap.ui.AuthBootstrapOutcome
import com.surimap.feature.bootstrap.ui.AuthBootstrapUiState
import com.surimap.testing.policePhoneIdFixture
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthBootstrapContractTest {

    @Test
    fun managedConfigurationSchemaIsDeclaredForStandardMdmInjection() {
        val schemaFile = File("src/main/res/xml/app_restrictions.xml")
        val document = schemaFile.requireXml()
        val restrictions = document.getElementsByTagName("restriction")
        val keys = (0 until restrictions.length)
            .map { index -> restrictions.item(index).attributes.getNamedItem("android:key").nodeValue }
            .toSet()

        assertEquals(
            setOf(
                AndroidManagedConfigurationReader.KEY_POLICE_PHONE_ID,
                AndroidManagedConfigurationReader.KEY_API_BASE_URL,
                AndroidManagedConfigurationReader.KEY_TILE_BASE_URL,
                AndroidManagedConfigurationReader.KEY_OBJECT_STORAGE_BASE_URL,
                AndroidManagedConfigurationReader.KEY_ALLOWED_HOSTS
            ),
            keys
        )
        assertEquals(
            listOf("string", "string", "string", "string", "string"),
            (0 until restrictions.length).map { index ->
                restrictions.item(index).attributes.getNamedItem("android:restrictionType").nodeValue
            }
        )
    }

    @Test
    fun manifestLinksManagedConfigurationSchemaWithoutKnoxSpecificMetadata() {
        val document = File("src/main/AndroidManifest.xml").requireXml()
        val metadata = document.getElementsByTagName("meta-data")
        val managedConfigNodes = (0 until metadata.length)
            .map { index -> metadata.item(index).attributes }
            .filter { attributes ->
                attributes.getNamedItem("android:name")?.nodeValue == "android.content.APP_RESTRICTIONS"
            }

        assertEquals(1, managedConfigNodes.size)
        assertEquals("@xml/app_restrictions", managedConfigNodes.single().getNamedItem("android:resource").nodeValue)
        assertFalse((0 until metadata.length).any { index ->
            metadata.item(index).attributes
                .getNamedItem("android:name")
                ?.nodeValue
                ?.contains("knox", ignoreCase = true) == true
        })
    }

    @Test
    fun manifestDeclaresFirebaseMessagingServiceForTokenRefresh() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains(""".core.fcm.SuriMapFirebaseMessagingService"""))
        assertTrue(manifest.contains("""android:exported="false""""))
        assertTrue(manifest.contains("""com.google.firebase.MESSAGING_EVENT"""))
    }

    @Test
    fun firebaseGradleSetupIsOptionalAndUsesMessagingSdk() {
        val appBuild = File("build.gradle.kts").readText()
        val versionCatalog = File("../gradle/libs.versions.toml").readText()

        assertTrue(appBuild.contains("alias(libs.plugins.google.services) apply false"))
        assertTrue(appBuild.contains("hasGoogleServicesJson"))
        assertTrue(appBuild.contains("SURI_MAP_FIREBASE_MESSAGING_ENABLED"))
        assertTrue(appBuild.contains("implementation(platform(libs.firebase.bom))"))
        assertTrue(appBuild.contains("implementation(libs.firebase.messaging)"))
        assertTrue(versionCatalog.contains("firebase-bom"))
        assertTrue(versionCatalog.contains("firebase-messaging"))
        assertTrue(versionCatalog.contains("google-services"))
    }

    @Test
    fun androidOidcSetupUsesAppAuthAndDeepLinkCallbackWithoutPasswordGrantBuildFields() {
        val appBuild = File("build.gradle.kts").readText()
        val versionCatalog = File("../gradle/libs.versions.toml").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val oidcClient = File("src/main/java/com/surimap/feature/bootstrap/data/AndroidOidcLoginClient.kt").readText()

        assertTrue(versionCatalog.contains("appauth"))
        assertTrue(appBuild.contains("implementation(libs.appauth)"))
        assertTrue(appBuild.contains("SURI_MAP_KEYCLOAK_ISSUER_URL"))
        assertTrue(appBuild.contains("SURI_MAP_KEYCLOAK_CLIENT_ID"))
        assertTrue(appBuild.contains("SURI_MAP_KEYCLOAK_REDIRECT_URI"))
        assertTrue(manifest.contains("""android:scheme="com.surimap""""))
        assertTrue(manifest.contains("""android:host="auth""""))
        assertTrue(manifest.contains("""android:path="/callback""""))
        assertTrue(oidcClient.contains("createCustomTabsIntentBuilder"))
        assertTrue(oidcClient.contains("setDefaultColorSchemeParams"))
        assertTrue(oidcClient.contains("setToolbarColor"))
        assertTrue(oidcClient.contains("getAuthorizationRequestIntent(request, customTabsIntent)"))
        assertFalse(appBuild.contains("SURI_MAP_DEBUG_BOOTSTRAP_PASSWORD"))
        assertFalse(appBuild.contains("SURI_MAP_DEBUG_BOOTSTRAP_ACCOUNT_CODE"))
        assertFalse(oidcClient.contains("WebView"))
    }

    @Test
    fun managedPolicePhoneConfigCarriesFutureTileAndStorageSettingsWithoutVendorCoupling() {
        val config =
            ManagedPolicePhoneConfig(
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal/api",
                tileBaseUrl = "https://suri-map.internal/tiles",
                objectStorageBaseUrl = "https://suri-map.internal/objects",
                allowedHosts = setOf("suri-map.internal", "objects.suri-map.internal")
            )

        assertEquals("https://suri-map.internal/tiles", config.tileBaseUrl)
        assertEquals("https://suri-map.internal/objects", config.objectStorageBaseUrl)
        assertEquals(setOf("suri-map.internal", "objects.suri-map.internal"), config.allowedHosts)
    }

    @Test
    fun failureVariantsBlockIncidentNavigationAndUseFieldFacingCopy() {
        val expectedMessages =
            mapOf(
                AuthBootstrapFailureReason.NotManagedPhone to "관리 단말이 아닙니다.\nIT 부서로 문의 바랍니다.",
                AuthBootstrapFailureReason.InternalNetworkUnavailable to "내부망 연결을 확인하세요.",
                AuthBootstrapFailureReason.ServerRejectedPhone to "해당 폴리폰으로 접속할 수 없습니다.\n단말 등록 또는 사건 배정 상태를 확인하세요.\n계속되면 IT 부서로 문의 바랍니다.",
                AuthBootstrapFailureReason.ManagedConfigMissing to "관리 설정이 없습니다.\nIT 부서로 문의 바랍니다."
            )

        expectedMessages.forEach { (reason, message) ->
            val state = AuthBootstrapUiState.fromOutcome(
                outcome = AuthBootstrapOutcome.Blocked(reason),
                apiBaseUrl = "https://suri-map.internal"
            )

            assertFalse(state.shouldEnterIncidentList)
            assertEquals(message, state.failureMessage)
            if (reason == AuthBootstrapFailureReason.ServerRejectedPhone) {
                assertEquals("단말 확인 필요", state.title)
            }
            assertFalse(state.visibleText().any { it.contains("로그인") })
            assertFalse(state.visibleText().any { it.contains("비밀번호") })
            assertFalse(state.visibleText().any { it.contains("바인딩") })
            assertFalse(state.visibleText().any { it.contains("Knox") })
            assertFalse(state.visibleText().any { it.contains("MDM") })
        }
    }

    @Test
    fun serverRejectedPhoneOffersExitAction() {
        val state = AuthBootstrapUiState.fromOutcome(
            outcome = AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone),
            apiBaseUrl = "https://suri-map.internal"
        )

        assertTrue(state.exitEnabled)
        assertEquals("앱 종료", state.primaryActionLabel)
        assertNull(state.actionGuideText)
    }

    @Test
    fun readyOutcomeAllowsAutomaticIncidentListNavigationWithoutIncidentDetails() {
        val state = AuthBootstrapUiState.fromOutcome(
            outcome = AuthBootstrapOutcome.Ready(
                policePhoneId = POLICE_PHONE_ID
            ),
            apiBaseUrl = "https://suri-map.internal"
        )

        assertTrue(state.shouldEnterIncidentList)
        assertFalse(state.visibleText().any { it.contains("실종") })
        assertFalse(state.visibleText().any { it.contains("OP") })
        assertFalse(state.visibleText().any { it.contains("배정 사건") })
    }

    @Test
    fun authenticationRequiredStatePromptsFieldFacingSsoAfterManagedChecks() {
        val state = AuthBootstrapUiState.fromOutcome(
            outcome = AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.AuthenticationRequired),
            apiBaseUrl = "https://suri-map.internal"
        )

        assertFalse(state.shouldEnterIncidentList)
        assertTrue(state.requiresAuthentication)
        assertNull(state.failureMessage)
        assertEquals("폴리폰 인증", state.title)
        assertEquals("SSO로 계속", state.primaryActionLabel)
        assertTrue(state.visibleText().any { it.contains("SSO 인증") })
        assertTrue(state.visibleText().any { it.contains("자동으로 앱으로 돌아옵니다") })
        assertFalse(state.visibleText().any { it.contains("Keycloak") })
        assertFalse(state.visibleText().any { it.contains("인증 서버") })
        assertFalse(state.visibleText().any { it.contains("비밀번호") })
    }

    @Test
    fun keycloakLoginThemeUsesAndroidSpecificFieldAppCopy() {
        val loginTemplate = File("../../infra/docker/keycloak/themes/suri-map/login/login.ftl").readText()
        val koreanMessages = File("../../infra/docker/keycloak/themes/suri-map/login/messages/messages_ko.properties").readText()
        val englishMessages = File("../../infra/docker/keycloak/themes/suri-map/login/messages/messages_en.properties").readText()

        assertTrue(loginTemplate.contains("suri-map-android"))
        assertTrue(loginTemplate.contains("loginAccountTitleAndroid"))
        assertTrue(loginTemplate.contains("suriLoginHelpAndroid"))
        assertTrue(koreanMessages.contains("loginAccountTitleAndroid=수리맵 계정으로 로그인"))
        assertTrue(koreanMessages.contains("suriLoginHelpAndroid=등록된 수리맵 계정으로 현장 앱에 접속합니다."))
        assertTrue(englishMessages.contains("loginAccountTitleAndroid=수리맵 계정으로 로그인"))
        assertTrue(englishMessages.contains("suriLoginHelpAndroid=등록된 수리맵 계정으로 현장 앱에 접속합니다."))
        assertFalse(
            koreanMessages.lineSequence()
                .filter { line -> line.startsWith("loginAccountTitleAndroid=") || line.startsWith("suriLoginHelpAndroid=") }
                .any { line -> line.contains("지휘 상황판") || line.contains("지휘 계정") }
        )
    }

    @Test
    fun coordinatorStopsBeforeServerCheckWhenManagedConfigIsMissing() = runBlocking {
        var serverCalled = false
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = {
                    ManagedPolicePhoneConfig(
                        policePhoneId = null,
                        apiBaseUrl = "https://suri-map.internal"
                    )
                },
                serverCheck =
                AuthBootstrapServerCheck {
                    serverCalled = true
                    AuthBootstrapOutcome.Ready(policePhoneId = it.policePhoneId.orEmpty())
                }
            )

        val outcome = coordinator.check()

        assertEquals(AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ManagedConfigMissing), outcome)
        assertFalse(serverCalled)
    }

    @Test
    fun coordinatorStopsBeforeServerCheckWhenPhoneIsNotManaged() = runBlocking {
        var serverCalled = false
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = {
                    ManagedPolicePhoneConfig(
                        policePhoneId = POLICE_PHONE_ID,
                        apiBaseUrl = "https://suri-map.internal",
                        isManagedPhone = false
                    )
                },
                serverCheck =
                AuthBootstrapServerCheck {
                    serverCalled = true
                    AuthBootstrapOutcome.Ready(policePhoneId = it.policePhoneId.orEmpty())
                }
            )

        val outcome = coordinator.check()

        assertEquals(AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.NotManagedPhone), outcome)
        assertFalse(serverCalled)
    }

    @Test
    fun unmanagedDebugBootstrapConfigActsAsManagedForUsbSmoke() = runBlocking {
        val reader =
            AndroidManagedConfigurationReader(
                context = RuntimeEnvironment.getApplication(),
                localOverrideProvider =
                DebugManagedConfigurationOverrideProvider(
                    isDebugBuild = true,
                    apiBaseUrl = "http://127.0.0.1:8080",
                    debugBootstrapPolicePhoneId = POLICE_PHONE_ID
                )
            )
        var checkedConfig: ManagedPolicePhoneConfig? = null
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = reader,
                serverCheck =
                AuthBootstrapServerCheck { config ->
                    checkedConfig = config
                    AuthBootstrapOutcome.Ready(policePhoneId = config.policePhoneId.orEmpty())
                }
            )

        val outcome = coordinator.check()

        assertEquals(AuthBootstrapOutcome.Ready(policePhoneId = POLICE_PHONE_ID), outcome)
        assertEquals(POLICE_PHONE_ID, checkedConfig?.policePhoneId)
        assertEquals("http://127.0.0.1:8080", checkedConfig?.apiBaseUrl)
        assertEquals(true, checkedConfig?.isManagedPhone)
    }

    @Test
    fun unmanagedDebugWithoutBootstrapConfigStaysNotManaged() = runBlocking {
        var serverCalled = false
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader =
                AndroidManagedConfigurationReader(
                    context = RuntimeEnvironment.getApplication(),
                    localOverrideProvider =
                    DebugManagedConfigurationOverrideProvider(
                        isDebugBuild = true,
                        apiBaseUrl = "http://127.0.0.1:8080",
                        debugBootstrapPolicePhoneId = ""
                    )
                ),
                serverCheck =
                AuthBootstrapServerCheck {
                    serverCalled = true
                    AuthBootstrapOutcome.Ready(policePhoneId = it.policePhoneId.orEmpty())
                }
            )

        val outcome = coordinator.check()

        assertEquals(AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.NotManagedPhone), outcome)
        assertFalse(serverCalled)
    }

    @Test
    fun nonDebugBuildDoesNotUseLocalDebugBootstrapManagedConfigOverride() {
        val override =
            DebugManagedConfigurationOverrideProvider(
                isDebugBuild = false,
                apiBaseUrl = "http://127.0.0.1:8080",
                debugBootstrapPolicePhoneId = POLICE_PHONE_ID
            )

        assertNull(override.read())
    }

    @Test
    fun coordinatorMapsServerFailuresToBootstrapOutcomes() = runBlocking {
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = {
                    ManagedPolicePhoneConfig(
                        policePhoneId = POLICE_PHONE_ID,
                        apiBaseUrl = "https://suri-map.internal"
                    )
                },
                serverCheck =
                AuthBootstrapServerCheck {
                    AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone)
                }
            )

        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone),
            coordinator.check()
        )
    }

    @Test
    fun networkServerCheckUsesHeartbeatWithPolicePhoneHeader() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200, """{"status":"ACTIVE"}"""))
        val serverCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = callFactory
                ),
                accessTokenProvider = AccessTokenProvider { "token-1" },
                clock = Clock.fixed(Instant.parse("2026-05-11T07:00:00Z"), ZoneOffset.UTC)
            )

        val outcome =
            serverCheck.verify(
                ManagedPolicePhoneConfig(
                    policePhoneId = POLICE_PHONE_ID,
                    apiBaseUrl = "https://suri-map.internal"
                )
            )

        val request = callFactory.lastRequest!!
        assertEquals(
            AuthBootstrapOutcome.Ready(
                policePhoneId = POLICE_PHONE_ID,
                accessToken = "token-1"
            ),
            outcome
        )
        assertEquals("POST", request.method)
        assertEquals(
            "https://suri-map.internal/api/police-phones/$POLICE_PHONE_ID/heartbeat",
            request.url.toString()
        )
        assertEquals("APP", request.header("X-Client-Channel"))
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals(POLICE_PHONE_ID, request.header("X-PolicePhone-Id"))
        assertNull(request.header("X-Device-Id"))
        assertEquals("""{"clientTs":"2026-05-11T07:00:00Z","sequence":1}""", readRequestBody(request))
    }

    @Test
    fun networkServerCheckRequiresOidcAccessTokenWithoutCallingLegacyLogin() = runBlocking {
        val callFactory = CapturingCallFactory(response = response(200, """{"status":"ACTIVE"}"""))
        val serverCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = callFactory
                ),
                clock = Clock.fixed(Instant.parse("2026-05-11T07:00:00Z"), ZoneOffset.UTC)
            )

        val outcome =
            serverCheck.verify(
                ManagedPolicePhoneConfig(
                    policePhoneId = POLICE_PHONE_ID,
                    apiBaseUrl = "https://suri-map.internal"
                )
            )

        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.AuthenticationRequired),
            outcome
        )
        assertTrue(callFactory.requests.isEmpty())
    }

    @Test
    fun networkServerCheckMapsGuardErrorAndIoFailure() = runBlocking {
        val rejectedCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = CapturingCallFactory(
                        response = response(403, """{"error":"police_phone_not_assigned"}""")
                    )
                ),
                accessTokenProvider = AccessTokenProvider { "token-1" }
            )
        val offlineCheck =
            NetworkPolicePhoneBootstrapServerCheck(
                apiClient =
                SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = FailingCallFactory()
                ),
                accessTokenProvider = AccessTokenProvider { "token-1" }
            )
        val config =
            ManagedPolicePhoneConfig(
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal"
            )

        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.ServerRejectedPhone),
            rejectedCheck.verify(config)
        )
        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable),
            offlineCheck.verify(config)
        )
    }

    @Test
    fun coordinatorRunsEnvironmentCheckBeforeAuthenticationRequired() = runBlocking {
        var serverCalled = false
        val coordinator =
            AuthBootstrapCoordinator(
                managedConfigurationReader = {
                    ManagedPolicePhoneConfig(
                        policePhoneId = POLICE_PHONE_ID,
                        apiBaseUrl = "http://127.0.0.1:8080"
                    )
                },
                environmentCheck =
                AuthBootstrapEnvironmentCheck {
                    AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable)
                },
                serverCheck =
                AuthBootstrapServerCheck {
                    serverCalled = true
                    AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.AuthenticationRequired)
                }
            )

        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable),
            coordinator.check()
        )
        assertFalse(serverCalled)
    }

    @Test
    fun networkEnvironmentCheckUsesHealthBeforeLoginAndMapsNetworkFailure() = runBlocking {
        val successFactory = CapturingCallFactory(response = response(200, """{"status":"ok"}"""))
        val successCheck =
            NetworkAuthBootstrapEnvironmentCheck(
                apiClient = SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = successFactory
                )
            )
        val failingCheck =
            NetworkAuthBootstrapEnvironmentCheck(
                apiClient = SuriMapApiClient(
                    baseUrl = "https://suri-map.internal",
                    callFactory = FailingCallFactory()
                )
            )
        val config =
            ManagedPolicePhoneConfig(
                policePhoneId = POLICE_PHONE_ID,
                apiBaseUrl = "https://suri-map.internal"
            )

        assertNull(successCheck.verify(config))
        assertEquals("GET", successFactory.lastRequest!!.method)
        assertEquals("https://suri-map.internal/api/health", successFactory.lastRequest!!.url.toString())
        assertEquals(
            AuthBootstrapOutcome.Blocked(AuthBootstrapFailureReason.InternalNetworkUnavailable),
            failingCheck.verify(config)
        )
    }

    @Test
    fun debugManifestAllowsCleartextForUsbSmokeAndKeepsQaActivity() {
        val document = File("src/debug/AndroidManifest.xml").requireXml()
        val applications = document.getElementsByTagName("application")
        val activities = document.getElementsByTagName("activity")

        assertEquals(
            "true",
            applications.item(0).attributes.getNamedItem("android:usesCleartextTraffic").nodeValue
        )
        assertTrue((0 until activities.length).any { index ->
            activities.item(index).attributes.getNamedItem("android:name")?.nodeValue ==
                ".ui.qa.DeviceQaActivity"
        })
    }

    private companion object {
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-car-01")
    }

    private class CapturingCallFactory(
        private val responses: List<Response>
    ) : Call.Factory {
        constructor(response: Response) : this(listOf(response))

        var lastRequest: Request? = null
        val requests = mutableListOf<Request>()
        private var index = 0

        override fun newCall(request: Request): Call {
            lastRequest = request
            requests += request
            val response = responses[minOf(index, responses.lastIndex)]
            index += 1
            return CapturingCall(request, response)
        }
    }

    private class FailingCallFactory : Call.Factory {
        override fun newCall(request: Request): Call = FailingCall(request)
    }

    private class CapturingCall(
        private val request: Request,
        private val response: Response
    ) : Call {
        override fun request(): Request = request
        override fun execute(): Response = response.newBuilder().request(request).build()
        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = CapturingCall(request, response)
    }

    private class FailingCall(
        private val request: Request
    ) : Call {
        override fun request(): Request = request
        override fun execute(): Response = throw IOException("network unavailable")
        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = FailingCall(request)
    }
}

private fun response(statusCode: Int, body: String): Response {
    return Response.Builder()
        .request(Request.Builder().url("https://suri-map.internal/placeholder").build())
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message("test")
        .body(body.toResponseBody())
        .build()
}

private fun readRequestBody(request: Request): String {
    val buffer = okio.Buffer()
    request.body!!.writeTo(buffer)
    return buffer.readUtf8()
}

private fun File.requireXml() =
    DocumentBuilderFactory
        .newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(this)
