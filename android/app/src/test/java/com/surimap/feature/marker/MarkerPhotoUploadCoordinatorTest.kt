package com.surimap.feature.marker

import com.surimap.core.sync.EnqueueResult
import com.surimap.core.sync.HarnessSyncStatus
import com.surimap.core.sync.LocalWriteOperation
import com.surimap.core.sync.OutboxStatus
import com.surimap.core.sync.SyncClient
import com.surimap.core.network.AccessTokenProvider
import com.surimap.core.network.SuriMapApiClient
import com.surimap.feature.marker.data.HttpObjectStorageUploader
import com.surimap.feature.marker.data.MarkerPhotoUploadCoordinator
import com.surimap.feature.marker.data.MarkerPhotoUploadPayload
import com.surimap.feature.marker.data.MarkerPhotoUploadResult
import com.surimap.feature.marker.data.MarkerPhotoUploadUrlInput
import com.surimap.feature.marker.data.MarkerPhotoUploadUrlResponseInput
import com.surimap.feature.marker.data.MarkerPhotoUiUploadCoordinator
import com.surimap.feature.marker.data.MarkerWriteContext
import com.surimap.feature.marker.data.ObjectStoragePutRequest
import com.surimap.feature.marker.data.ObjectStoragePutResult
import com.surimap.feature.marker.data.ObjectStorageUploader
import com.surimap.testing.incidentIdFixture
import com.surimap.testing.markerIdFixture
import com.surimap.testing.operationIdFixture
import com.surimap.testing.opIdFixture
import com.surimap.testing.photoIdFixture
import com.surimap.testing.policePhoneIdFixture
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class MarkerPhotoUploadCoordinatorTest {

    @Test
    fun invalidPhotoUploadInputIsBlockedBeforeUploadUrlOrObjectStoragePut() = runBlocking {
        val syncClient = CapturingSyncClient()
        val uploader = CapturingObjectStorageUploader()
        val coordinator = coordinator(syncClient = syncClient, uploader = uploader)

        val result =
            coordinator.requestUploadUrl(
                context = CONTEXT,
                input =
                MarkerPhotoUploadUrlInput(
                    markerId = MARKER_ID,
                    contentType = "image/jpeg",
                    sizeBytes = 10_485_761
                )
            )

        assertEquals(MarkerPhotoUploadResult.Blocked, result)
        assertTrue(syncClient.operations.isEmpty())
        assertTrue(uploader.requests.isEmpty())
    }

    @Test
    fun validPhotoUploadInputEnqueuesUploadUrlRequest() = runBlocking {
        val syncClient = CapturingSyncClient()
        val coordinator = coordinator(syncClient = syncClient)

        val result =
            coordinator.requestUploadUrl(
                context = CONTEXT,
                input =
                MarkerPhotoUploadUrlInput(
                    markerId = MARKER_ID,
                    contentType = "image/jpeg",
                    sizeBytes = PHOTO_BYTES.size.toLong(),
                    checksumSha256 = CHECKSUM
                )
            )

        assertEquals(
            MarkerPhotoUploadResult.UploadUrlEnqueued(
                operationId = PHOTO_UPLOAD_OPERATION_ID,
                outboxId = "outbox-1"
            ),
            result
        )
        assertEquals(1, syncClient.operations.size)
        assertEquals("/api/markers/$MARKER_ID/photos/upload-url", syncClient.operations.single().endpoint)
        assertEquals("""{"contentType":"image/jpeg","sizeBytes":4,"checksumSha256":"sha256-local-photo"}""", syncClient.operations.single().payload)
    }

    @Test
    fun objectStorageFailureDoesNotEnqueueAttach() = runBlocking {
        val syncClient = CapturingSyncClient()
        val uploader = CapturingObjectStorageUploader(result = ObjectStoragePutResult.Failure("timeout"))
        val coordinator = coordinator(syncClient = syncClient, uploader = uploader)

        val result =
            coordinator.uploadObjectAndAttach(
                context = CONTEXT,
                response = UPLOAD_RESPONSE,
                payload = PHOTO_PAYLOAD,
                parentOperationId = PHOTO_UPLOAD_OPERATION_ID
            )

        assertEquals(MarkerPhotoUploadResult.UploadFailed("timeout"), result)
        assertEquals(1, uploader.requests.size)
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun successfulObjectStoragePutEnqueuesAttachWithUploadUrlOperationAsParent() = runBlocking {
        val syncClient = CapturingSyncClient()
        val uploader = CapturingObjectStorageUploader()
        val coordinator = coordinator(syncClient = syncClient, uploader = uploader)

        val result =
            coordinator.uploadObjectAndAttach(
                context = CONTEXT,
                response = UPLOAD_RESPONSE,
                payload = PHOTO_PAYLOAD,
                parentOperationId = PHOTO_UPLOAD_OPERATION_ID
            )

        assertEquals(
            MarkerPhotoUploadResult.AttachedEnqueued(
                operationId = PHOTO_ATTACH_OPERATION_ID,
                outboxId = "outbox-1"
            ),
            result
        )
        val put = uploader.requests.single()
        assertEquals(URI.create(UPLOAD_URL), put.uploadUrl)
        assertEquals("image/jpeg", put.contentType)
        assertEquals(PHOTO_BYTES.size.toLong(), put.sizeBytes)
        assertArrayEquals(PHOTO_BYTES, put.bytes)
        assertEquals(1, syncClient.operations.size)
        val attach = syncClient.operations.single()
        assertEquals("/api/markers/$MARKER_ID/photos/$PHOTO_ID/attach", attach.endpoint)
        assertEquals(PHOTO_UPLOAD_OPERATION_ID, attach.parentOperationId)
        assertEquals(
            """{"sizeBytes":4,"contentType":"image/jpeg","width":1280,"height":960,"checksumSha256":"sha256-local-photo"}""",
            attach.payload
        )
    }

    @Test
    fun uiUploadCoordinatorUsesUploadUrlResponseForObjectPutThenAttachOutbox() = runBlocking {
        val syncClient = CapturingSyncClient()
        val uploader = CapturingObjectStorageUploader()
        val callFactory =
            StaticCallFactory(
                response =
                response(
                    statusCode = 201,
                    body =
                    """
                    {
                      "photoId": "$PHOTO_ID",
                      "uploadUrl": "$UPLOAD_URL",
                      "maxSizeBytes": 10485760,
                      "version": 1
                    }
                    """.trimIndent()
                )
            )
        val coordinator =
            uiCoordinator(
                syncClient = syncClient,
                uploader = uploader,
                callFactory = callFactory
            )

        val result =
            coordinator.upload(
                context = CONTEXT,
                payload = PHOTO_PAYLOAD
            )

        assertEquals(
            MarkerPhotoUploadResult.AttachedEnqueued(
                operationId = PHOTO_ATTACH_OPERATION_ID,
                outboxId = "outbox-1"
            ),
            result
        )
        assertEquals(
            listOf(
                "/api/markers/$MARKER_ID/photos/$PHOTO_ID/attach"
            ),
            syncClient.operations.map { it.endpoint }
        )
        assertEquals(PHOTO_UPLOAD_OPERATION_ID, syncClient.operations.single().parentOperationId)
        val request = callFactory.lastRequest!!
        assertEquals("POST", request.method)
        assertEquals(
            "https://suri-map.example.com/api/markers/$MARKER_ID/photos/upload-url",
            request.url.toString()
        )
        assertEquals("Bearer token-1", request.header("Authorization"))
        assertEquals(POLICE_PHONE_ID, request.header("X-PolicePhone-Id"))
        assertEquals("idem-$PHOTO_UPLOAD_OPERATION_ID", request.header("Idempotency-Key"))
        assertEquals(1, uploader.requests.size)
        assertEquals(URI.create(UPLOAD_URL), uploader.requests.single().uploadUrl)
    }

    @Test
    fun uiUploadCoordinatorDoesNotEnqueueAttachWhenUploadUrlRequestFails() = runBlocking {
        val syncClient = CapturingSyncClient()
        val uploader = CapturingObjectStorageUploader()
        val coordinator =
            uiCoordinator(
                syncClient = syncClient,
                uploader = uploader,
                callFactory =
                StaticCallFactory(
                    response = response(statusCode = 409, body = """{"error":"incident_closed"}""")
                )
            )

        val result =
            coordinator.upload(
                context = CONTEXT,
                payload = PHOTO_PAYLOAD
            )

        assertEquals(MarkerPhotoUploadResult.UploadFailed("incident_closed"), result)
        assertTrue(syncClient.operations.isEmpty())
        assertTrue(uploader.requests.isEmpty())
    }

    @Test
    fun malformedUploadUrlResponseIsBlockedBeforeObjectStoragePut() = runBlocking {
        val syncClient = CapturingSyncClient()
        val uploader = CapturingObjectStorageUploader()
        val coordinator = coordinator(syncClient = syncClient, uploader = uploader)

        val result =
            coordinator.uploadObjectAndAttach(
                context = CONTEXT,
                response = UPLOAD_RESPONSE.copy(uploadUrl = "not-a-url"),
                payload = PHOTO_PAYLOAD,
                parentOperationId = PHOTO_UPLOAD_OPERATION_ID
            )

        assertEquals(MarkerPhotoUploadResult.Blocked, result)
        assertTrue(uploader.requests.isEmpty())
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun missingContextIsBlockedBeforeObjectStoragePut() = runBlocking {
        val syncClient = CapturingSyncClient()
        val uploader = CapturingObjectStorageUploader()
        val coordinator = coordinator(syncClient = syncClient, uploader = uploader)

        val result =
            coordinator.uploadObjectAndAttach(
                context = CONTEXT.copy(policePhoneId = null),
                response = UPLOAD_RESPONSE,
                payload = PHOTO_PAYLOAD,
                parentOperationId = PHOTO_UPLOAD_OPERATION_ID
            )

        assertEquals(MarkerPhotoUploadResult.Blocked, result)
        assertTrue(uploader.requests.isEmpty())
        assertTrue(syncClient.operations.isEmpty())
    }

    @Test
    fun uploadUrlResponseJsonParsesPhotoIdUploadUrlAndLimits() {
        val response =
            MarkerPhotoUploadUrlResponseInput.fromJson(
                markerId = MARKER_ID,
                body =
                """
                {
                  "photoId": "$PHOTO_ID",
                  "uploadUrl": "$UPLOAD_URL",
                  "expiresAt": "2026-05-11T06:15:00Z",
                  "maxSizeBytes": 10485760,
                  "version": 1
                }
                """.trimIndent()
            )

        assertEquals(
            MarkerPhotoUploadUrlResponseInput(
                markerId = MARKER_ID,
                photoId = PHOTO_ID,
                uploadUrl = UPLOAD_URL,
                maxSizeBytes = 10_485_760L,
                version = 1L
            ),
            response
        )
        assertEquals(null, MarkerPhotoUploadUrlResponseInput.fromJson(markerId = MARKER_ID, body = """{"uploadUrl":"$UPLOAD_URL"}"""))
    }

    @Test
    fun httpObjectStorageUploaderPutsBytesToPresignedUrlWithoutAppHeaders() = runBlocking {
        val callFactory = StaticCallFactory(response = response(statusCode = 200))
        val uploader = HttpObjectStorageUploader(callFactory = callFactory)

        val result =
            uploader.put(
                ObjectStoragePutRequest(
                    uploadUrl = URI.create(UPLOAD_URL),
                    contentType = "image/jpeg",
                    sizeBytes = PHOTO_BYTES.size.toLong(),
                    bytes = PHOTO_BYTES
                )
            )

        assertEquals(ObjectStoragePutResult.Success, result)
        val request = callFactory.lastRequest!!
        assertEquals("PUT", request.method)
        assertEquals(UPLOAD_URL, request.url.toString())
        assertEquals("image/jpeg", request.header("Content-Type"))
        assertEquals(null, request.header("Authorization"))
        assertEquals(null, request.header("X-Client-Channel"))
        assertArrayEquals(PHOTO_BYTES, readRequestBody(request))
    }

    @Test
    fun httpObjectStorageUploaderMapsHttpAndNetworkFailures() = runBlocking {
        val httpFailure =
            HttpObjectStorageUploader(
                callFactory = StaticCallFactory(response = response(statusCode = 500))
            ).put(
                ObjectStoragePutRequest(
                    uploadUrl = URI.create(UPLOAD_URL),
                    contentType = "image/jpeg",
                    sizeBytes = PHOTO_BYTES.size.toLong(),
                    bytes = PHOTO_BYTES
                )
            )
        val networkFailure =
            HttpObjectStorageUploader(
                callFactory = StaticCallFactory(exception = java.io.IOException("offline"))
            ).put(
                ObjectStoragePutRequest(
                    uploadUrl = URI.create(UPLOAD_URL),
                    contentType = "image/jpeg",
                    sizeBytes = PHOTO_BYTES.size.toLong(),
                    bytes = PHOTO_BYTES
                )
            )

        assertEquals(ObjectStoragePutResult.Failure("http_500"), httpFailure)
        assertEquals(ObjectStoragePutResult.Failure("network_error"), networkFailure)
    }

    private fun coordinator(
        syncClient: SyncClient,
        uploader: ObjectStorageUploader = CapturingObjectStorageUploader()
    ): MarkerPhotoUploadCoordinator =
        MarkerPhotoUploadCoordinator(
            syncClient = syncClient,
            uploader = uploader,
            now = { CLIENT_TS },
            sequenceSource = sequenceSource(1),
            idFactory = idFactory()
        )

    private fun uiCoordinator(
        syncClient: SyncClient,
        uploader: ObjectStorageUploader = CapturingObjectStorageUploader(),
        callFactory: StaticCallFactory = StaticCallFactory(response = response(statusCode = 201))
    ): MarkerPhotoUiUploadCoordinator =
        MarkerPhotoUiUploadCoordinator(
            syncClient = syncClient,
            apiClient =
            SuriMapApiClient(
                baseUrl = "https://suri-map.example.com/api",
                callFactory = callFactory
            ),
            accessTokenProvider = AccessTokenProvider { "token-1" },
            uploader = uploader,
            now = { CLIENT_TS },
            sequenceSource = sequenceSource(1),
            idFactory = idFactory()
        )

    private class CapturingSyncClient : SyncClient {
        val operations = mutableListOf<LocalWriteOperation>()

        override suspend fun enqueue(writeOperation: LocalWriteOperation): EnqueueResult {
            operations += writeOperation
            return EnqueueResult(
                outboxId = "outbox-${operations.size}",
                operationId = writeOperation.operationId,
                status = OutboxStatus.PENDING,
                harnessStatus = HarnessSyncStatus.PENDING_SEND
            )
        }
    }

    private class CapturingObjectStorageUploader(
        private val result: ObjectStoragePutResult = ObjectStoragePutResult.Success
    ) : ObjectStorageUploader {
        val requests = mutableListOf<ObjectStoragePutRequest>()

        override suspend fun put(request: ObjectStoragePutRequest): ObjectStoragePutResult {
            requests += request
            return result
        }
    }

    private class StaticCallFactory(
        private val response: Response? = null,
        private val exception: java.io.IOException? = null
    ) : Call.Factory {
        var lastRequest: Request? = null

        override fun newCall(request: Request): Call {
            lastRequest = request
            return StaticCall(request, response, exception)
        }
    }

    private class StaticCall(
        private val request: Request,
        private val response: Response?,
        private val exception: java.io.IOException?
    ) : Call {
        override fun request(): Request = request

        override fun execute(): Response {
            exception?.let { throw it }
            return response!!.newBuilder().request(request).build()
        }

        override fun enqueue(responseCallback: Callback) = error("async calls are not used")
        override fun cancel() = Unit
        override fun isExecuted(): Boolean = false
        override fun isCanceled(): Boolean = false
        override fun timeout(): Timeout = Timeout.NONE
        override fun <T : Any> tag(type: KClass<T>): T? = null
        override fun <T> tag(type: Class<out T>): T? = null
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T = computeIfAbsent()
        override fun clone(): Call = StaticCall(request, response, exception)
    }

    private fun sequenceSource(first: Long): () -> Long {
        var next = first
        return { next++ }
    }

    private fun idFactory(): (String) -> String {
        val nextByPrefix = mutableMapOf<String, Int>()
        return { prefix ->
            val next = nextByPrefix.getOrDefault(prefix, 1)
            nextByPrefix[prefix] = next + 1
            operationIdFixture("${prefix.removePrefix("op-")}-$next")
        }
    }

    private companion object {
        val INCIDENT_ID = incidentIdFixture("precinct-first-001")
        val OP_ID = opIdFixture("precinct-first-001")
        val MARKER_ID = markerIdFixture("precinct-clue-001")
        val PHOTO_ID = photoIdFixture("precinct-clue-001")
        val POLICE_PHONE_ID = policePhoneIdFixture("precinct-001")
        const val CHECKSUM = "sha256-local-photo"
        const val UPLOAD_URL = "https://object-storage.local/bucket/photo-precinct-clue-001"
        val PHOTO_UPLOAD_OPERATION_ID = operationIdFixture("photo-upload-url-1")
        val PHOTO_ATTACH_OPERATION_ID = operationIdFixture("photo-attach-1")
        val CLIENT_TS: Instant = Instant.parse("2026-05-11T06:00:00Z")
        val PHOTO_BYTES = byteArrayOf(1, 2, 3, 4)
        val CONTEXT =
            MarkerWriteContext(
                incidentId = INCIDENT_ID,
                opId = OP_ID,
                policePhoneId = POLICE_PHONE_ID
            )
        val UPLOAD_RESPONSE =
            MarkerPhotoUploadUrlResponseInput(
                markerId = MARKER_ID,
                photoId = PHOTO_ID,
                uploadUrl = UPLOAD_URL
            )
        val PHOTO_PAYLOAD =
            MarkerPhotoUploadPayload(
                markerId = MARKER_ID,
                contentType = "image/jpeg",
                bytes = PHOTO_BYTES,
                width = 1280,
                height = 960,
                checksumSha256 = CHECKSUM
            )
    }
}

private fun response(statusCode: Int, body: String = ""): Response {
    return Response.Builder()
        .request(Request.Builder().url("https://object-storage.local/placeholder").build())
        .protocol(Protocol.HTTP_1_1)
        .code(statusCode)
        .message("test")
        .body(body.toResponseBody())
        .build()
}

private fun readRequestBody(request: Request): ByteArray {
    val buffer = okio.Buffer()
    request.body!!.writeTo(buffer)
    return buffer.readByteArray()
}
