package hooman.morphe.patches.swiftkey.privacy

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

private const val GENERIC_RECORD = "Lorg/apache/avro/generic/GenericRecord;"

// SwiftKey's first-party analytics records all pass through zh0.n.b(GenericRecord), which fans each
// Avro record out to the configured telemetry sinks. The class and method are R8-renamed, so pin the
// final (GenericRecord)V method by its call to the telemetry sink interface plus the abstract class's
// Set constructor and no-arg abstract lifecycle method.
internal object TelemetryRecordDispatchFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf(GENERIC_RECORD),
    filters = listOf(
        methodCall(
            definingClass = "Lei0/g;",
            name = "a",
        ),
    ),
    custom = { _, classDef ->
        AccessFlags.ABSTRACT.isSet(classDef.accessFlags) &&
            classDef.methods.any { method ->
                method.name == "<init>" &&
                    method.parameterTypes.map { it.toString() } == listOf("Ljava/util/Set;")
            } &&
            classDef.methods.any { method ->
                AccessFlags.ABSTRACT.isSet(method.accessFlags) &&
                    method.returnType == "V" &&
                    method.parameterTypes.isEmpty()
            }
    },
)

private val LOG_SENDER_CLASS = Fingerprint(
    strings = listOf("LogSender", "Secondary telemetry upload failed"),
)

// Primary PaperBoy upload loop (td0.a.e(Continuation) in this build). It drains queued records and
// calls the secondary uploader when configured. The LogSender strings pin its multi-purpose owner;
// this one-Continuation coroutine shape is unique inside that class.
internal object PrimaryTelemetryUploadFingerprint : Fingerprint(
    classFingerprint = LOG_SENDER_CLASS,
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Lxl0/c;"),
)

// Secondary PaperBoy uploader in the same class. Patch it independently so no direct caller can use
// the fallback endpoint after the primary loop has been disabled.
internal object SecondaryTelemetryUploadFingerprint : Fingerprint(
    classFingerprint = LOG_SENDER_CLASS,
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Lsn0/f;", "Lxl0/c;"),
)

// FullApplicationDelegate initializes Adjust from the app-specific token. Pinning the token and the
// SDK call selects the exact startup method without relying on its R8 class or method names.
internal object AdjustInitializationFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z"),
    strings = listOf("f30sdsr9mha8"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/adjust/sdk/Adjust;",
            name = "initSdk",
        ),
    ),
)
