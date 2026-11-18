/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.KType

/**
 * Contract test enforcing zero signature drift between `docs/architecture/interfaces.md`
 * (`IF-SWE-01`..`IF-SWE-08`) and compiled production bytecode via pure JVM reflection
 * (`Class.forName`, `KClass.members`, `declaredMethods`) — strictly zero source-text reading
 * of production code (`REQ-PRO-049`, `REQ-PRO-115`, `TST-PRO-115`, `ATT-8160`).
 */
// Linked test specs: TST-PRO-115
class InterfaceSignatureDriftContractTest {

    private data class DocParam(
        val name: String,
        val rawType: String,
        val isNullable: Boolean
    )

    private data class DocSignature(
        val interfaceId: String,
        val ownerName: String,
        val kind: Kind,
        val isSuspend: Boolean,
        val memberName: String,
        val params: List<DocParam>,
        val returnType: String,
        val isReturnNullable: Boolean,
        val rawDeclaration: String
    ) {
        enum class Kind { FUN, VAL }
    }

    companion object {
        private val OWNER_FQCNS: Map<String, String> = mapOf(
            "TrackingViewModel" to "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModel",
            "ControlTrackingViewModel" to "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModel",
            "TrackOnMapAftermathViewModel" to "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel",
            "WorkoutRepository" to "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository",
            "BANALServiceRepository" to "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository",
            "BANALServiceComm" to "com.atrainingtracker.banalservice.BANALService\$BANALServiceComm",
            "AutoPauseManager" to "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager",
            "PowerPhysiologyCalculator" to "com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator",
            "PowerHrDynamicsCalculator" to "com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator",
            "WorkoutClusterEngine" to "com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine",
            "ClusterWorkoutDataPort" to "com.atrainingtracker.trainingtracker.database.ClusterWorkoutDataPort",
            "RadarLightController" to "com.atrainingtracker.banalservice.devices.radar.RadarLightController",
            "IndirectTpmsEstimator" to "com.atrainingtracker.banalservice.tpms.IndirectTpmsEstimator",
            "DualWheelTpmsEstimator" to "com.atrainingtracker.banalservice.tpms.DualWheelTpmsEstimator",
            "PolarH10OfflineController" to "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarH10OfflineController",
            "PolarVeritySenseOfflineController" to "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineController",
            "PolarOnlineStreamingBridge" to "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridge",
            "ClimbDetector" to "com.atrainingtracker.trainingtracker.climbs.ClimbDetector",
            "WorkoutSamplesDatabaseManager" to "com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager",
            "WorkoutSummariesDatabaseManager" to "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager",
            "WorkoutClusterDatabaseManager" to "com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager",
            "BaseExporter" to "com.atrainingtracker.trainingtracker.exporter.BaseExporter",
            "StravaUploader" to "com.atrainingtracker.trainingtracker.exporter.uploader.StravaUploader",
            "DropboxUploader" to "com.atrainingtracker.trainingtracker.exporter.uploader.DropboxUploader",
            "HealthConnectManager" to "com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectManager",
            "HealthConnectHrvImporter" to "com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectHrvImporter"
        )

        private val TYPE_FQCNS: Map<String, String> = mapOf(
            "Context" to "android.content.Context",
            "ContentValues" to "android.content.ContentValues",
            "Boolean" to "java.lang.Boolean",
            "Int" to "java.lang.Integer",
            "Long" to "java.lang.Long",
            "Double" to "java.lang.Double",
            "Float" to "java.lang.Float",
            "String" to "java.lang.String",
            "List" to "java.util.List",
            "Set" to "java.util.Set",
            "Map" to "java.util.Map",
            "StateFlow" to "kotlinx.coroutines.flow.StateFlow",
            "Flow" to "kotlinx.coroutines.flow.Flow",
            "Result" to "kotlin.Result",
            "LatLng" to "com.google.android.gms.maps.model.LatLng",
            "HealthConnectClient" to "androidx.health.connect.client.HealthConnectClient",
            "ScreenMode" to "com.atrainingtracker.trainingtracker.ui.tracking.ScreenMode",
            "TrackingScreenState" to "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingScreenState",
            "TrackingMapState" to "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingMapState",
            "AftermathMapUIState" to "com.atrainingtracker.trainingtracker.ui.map.AftermathMapUIState",
            "WorkoutData" to "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData",
            "SensorType" to "com.atrainingtracker.banalservice.sensor.SensorType",
            "SensorData" to "com.atrainingtracker.banalservice.sensor.SensorData",
            "SensorValueType" to "com.atrainingtracker.banalservice.sensor.SensorValueType",
            "FilteredSensorData" to "com.atrainingtracker.banalservice.filters.FilteredSensorData",
            "ExtremaType" to "com.atrainingtracker.trainingtracker.database.ExtremaType",
            "BSportType" to "com.atrainingtracker.banalservice.BSportType",
            "AutoPauseConfig" to "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig",
            "AutoPauseTransition" to "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseTransition",
            "CardioPowerDynamicsRecord" to "com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord",
            "WorkoutCluster" to "com.atrainingtracker.trainingtracker.database.WorkoutCluster",
            "PathPoint" to "com.atrainingtracker.trainingtracker.ui.map.PathPoint",
            "Climb" to "com.atrainingtracker.trainingtracker.climbs.Climb",
            "TrackerService.ExtremaUpdate" to "com.atrainingtracker.trainingtracker.tracker.TrackerService\$ExtremaUpdate",
            "LiveWorkoutSession.StreamIncrement" to "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSession\$StreamIncrement",
            "LiveSampleFailureCallback" to "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository\$LiveSampleFailureCallback",
            "ExportInfo" to "com.atrainingtracker.trainingtracker.exporter.ExportInfo",
            "ExportResult" to "com.atrainingtracker.trainingtracker.exporter.BaseExporter\$ExportResult",
            "FileFormat" to "com.atrainingtracker.trainingtracker.exporter.FileFormat",
            "HrvReadinessRepository" to "com.atrainingtracker.trainingtracker.database.hrv.HrvReadinessRepository",
            "RadarLightMode" to "com.atrainingtracker.banalservice.devices.radar.RadarLightMode"
        )
    }

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    private fun loadClass(fqcn: String): Class<*> {
        return try {
            Class.forName(fqcn, false, javaClass.classLoader)
        } catch (e: ClassNotFoundException) {
            throw AssertionError("Compiled production class '$fqcn' must exist on classpath", e)
        }
    }

    private fun stripGenerics(typeExpr: String): String {
        val lt = typeExpr.indexOf('<')
        if (lt < 0) return typeExpr
        val gt = typeExpr.lastIndexOf('>')
        if (gt <= lt) return typeExpr
        return typeExpr.substring(0, lt) + typeExpr.substring(gt + 1)
    }

    private fun extractTopLevelGenericArgs(typeExpr: String): List<String> {
        val clean = typeExpr.trim().removeSuffix("?").trim()
        val lt = clean.indexOf('<')
        if (lt < 0) return emptyList()
        val gt = clean.lastIndexOf('>')
        if (gt <= lt) return emptyList()
        return splitTopLevel(clean.substring(lt + 1, gt), ',')
    }

    private fun splitTopLevel(text: String, delimiter: Char): List<String> {
        val parts = mutableListOf<String>()
        var depthAngle = 0
        var depthParen = 0
        val current = StringBuilder()
        for (ch in text) {
            when (ch) {
                '<' -> depthAngle++
                '>' -> if (depthAngle > 0) depthAngle--
                '(' -> depthParen++
                ')' -> if (depthParen > 0) depthParen--
            }
            if (ch == delimiter && depthAngle == 0 && depthParen == 0) {
                val item = current.toString().trim()
                if (item.isNotEmpty()) parts.add(item)
                current.clear()
            } else {
                current.append(ch)
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) parts.add(tail)
        return parts
    }

    private fun findMatchingParen(text: String, openIndex: Int): Int {
        var depth = 0
        for (i in openIndex until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        throw IllegalArgumentException("Unmatched '(' in signature: $text")
    }

    private fun parseDocSignatures(interfacesText: String): List<DocSignature> {
        val result = mutableListOf<DocSignature>()
        var currentIf = ""
        val sectionRegex = Regex("""^###\s+\d+\.\d+\s+(IF-SWE-\d+):""")
        val sigLineRegex = Regex("""^\*\s+`(suspend\s+fun|fun|val)\s+([^`]+)`:\s*(.+)$""")
        val ownerRegex = Regex("""\(`([A-Za-z0-9_]+)`\)""")

        interfacesText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            sectionRegex.find(line)?.let { currentIf = it.groupValues[1] }
            val sigMatch = sigLineRegex.find(line) ?: return@forEach
            val keyword = sigMatch.groupValues[1]
            val decl = sigMatch.groupValues[2].trim()
            val desc = sigMatch.groupValues[3]
            val owners = ownerRegex.findAll(desc).map { it.groupValues[1] }.toList()
            if (owners.isEmpty()) return@forEach

            for (owner in owners) {
                if (keyword == "val") {
                    val colonIdx = decl.indexOf(':')
                    val propName = decl.substring(0, colonIdx).trim()
                    val retRaw = decl.substring(colonIdx + 1).trim()
                    result.add(
                        DocSignature(
                            interfaceId = currentIf,
                            ownerName = owner,
                            kind = DocSignature.Kind.VAL,
                            isSuspend = false,
                            memberName = propName,
                            params = emptyList(),
                            returnType = retRaw,
                            isReturnNullable = retRaw.endsWith("?"),
                            rawDeclaration = "$keyword $decl"
                        )
                    )
                } else {
                    val isSuspend = keyword.startsWith("suspend")
                    val openParen = decl.indexOf('(')
                    val closeParen = findMatchingParen(decl, openParen)
                    val methodName = decl.substring(0, openParen).trim()
                    val paramListRaw = decl.substring(openParen + 1, closeParen).trim()
                    val remainder = decl.substring(closeParen + 1).trim()
                    val returnRaw = if (remainder.startsWith(":")) remainder.substring(1).trim() else "Unit"

                    val params = if (paramListRaw.isEmpty()) {
                        emptyList()
                    } else {
                        splitTopLevel(paramListRaw, ',').map { paramDecl ->
                            val withoutDefault = splitTopLevel(paramDecl, '=')[0].trim()
                            val colonIdx = withoutDefault.indexOf(':')
                            val pName = withoutDefault.substring(0, colonIdx).trim()
                            val pType = withoutDefault.substring(colonIdx + 1).trim()
                            DocParam(
                                name = pName,
                                rawType = pType,
                                isNullable = pType.endsWith("?")
                            )
                        }
                    }

                    result.add(
                        DocSignature(
                            interfaceId = currentIf,
                            ownerName = owner,
                            kind = DocSignature.Kind.FUN,
                            isSuspend = isSuspend,
                            memberName = methodName,
                            params = params,
                            returnType = returnRaw,
                            isReturnNullable = returnRaw.endsWith("?"),
                            rawDeclaration = "$keyword $decl"
                        )
                    )
                }
            }
        }
        return result
    }

    private fun isKotlinClass(clazz: Class<*>): Boolean {
        return clazz.declaredAnnotations.any { it.annotationClass.java.name == "kotlin.Metadata" }
    }

    private fun resolveExpectedClassifierName(docType: String): String {
        val rawBase = stripGenerics(docType.trim()).removeSuffix("?").trim()
        return rawBase.substringAfterLast('.')
    }

    private fun kTypeMatchesDocType(kType: KType, docType: String, expectedNullable: Boolean): Boolean {
        if (kType.isMarkedNullable != expectedNullable) return false
        val classifier = kType.classifier as? KClass<*> ?: return false
        val expectedBase = resolveExpectedClassifierName(docType)
        val actualBase = classifier.simpleName ?: return false
        if (actualBase != expectedBase) return false

        val docArgs = extractTopLevelGenericArgs(docType)
        if (docArgs.isEmpty()) return true
        if (kType.arguments.size != docArgs.size) return false

        for (i in docArgs.indices) {
            val docArg = docArgs[i].trim()
            if (docArg == "*") continue
            val kArgType = kType.arguments[i].type ?: return false
            val argNullable = docArg.endsWith("?")
            if (!kTypeMatchesDocType(kArgType, docArg, argNullable)) return false
        }
        return true
    }

    private fun verifyKotlinMemberViaKClass(sig: DocSignature, ownerClass: Class<*>): String? {
        val kClass = ownerClass.kotlin
        val members = kClass.members

        if (sig.kind == DocSignature.Kind.VAL) {
            val prop = members.filterIsInstance<KProperty<*>>().firstOrNull { it.name == sig.memberName }
                ?: return "${sig.interfaceId}: Property '${sig.memberName}' not found on ${sig.ownerName}"
            if (!kTypeMatchesDocType(prop.returnType, sig.returnType, sig.isReturnNullable)) {
                return "${sig.interfaceId}: Property type mismatch on ${sig.ownerName}.${sig.memberName}: " +
                    "expected '${sig.returnType}', actual '${prop.returnType}'"
            }
            return null
        }

        val fnCandidates = members.filterIsInstance<KFunction<*>>().filter { it.name == sig.memberName }
        if (fnCandidates.isEmpty()) {
            return "${sig.interfaceId}: Method '${sig.memberName}' not found on ${sig.ownerName}"
        }

        val matched = fnCandidates.firstOrNull { fn ->
            val valueParams = fn.parameters.filter { it.kind == KParameter.Kind.VALUE }
            if (fn.isSuspend != sig.isSuspend || valueParams.size != sig.params.size) {
                return@firstOrNull false
            }
            valueParams.indices.all { idx ->
                val docParam = sig.params[idx]
                kTypeMatchesDocType(valueParams[idx].type, docParam.rawType, docParam.isNullable)
            }
        }
        if (matched == null) {
            val actualSignatures = fnCandidates.joinToString(" | ") { fn ->
                val ps = fn.parameters.filter { it.kind == KParameter.Kind.VALUE }
                    .joinToString(", ") { "${it.name}: ${it.type}" }
                "${if (fn.isSuspend) "suspend " else ""}fun ${fn.name}($ps): ${fn.returnType}"
            }
            return "${sig.interfaceId}: Signature/arity/nullability mismatch for ${sig.ownerName}.${sig.memberName} " +
                "(expected `${sig.rawDeclaration}`, actual: $actualSignatures)"
        }

        if (!kTypeMatchesDocType(matched.returnType, sig.returnType, sig.isReturnNullable)) {
            return "${sig.interfaceId}: Return type/nullability mismatch on ${sig.ownerName}.${sig.memberName}: " +
                "expected '${sig.returnType}', actual '${matched.returnType}'"
        }
        return null
    }

    private fun resolveJavaJvmType(docType: String, isNullable: Boolean): Class<*> {
        val stripped = stripGenerics(docType.trim()).removeSuffix("?").trim()
        if (!isNullable) {
            when (stripped) {
                "Boolean" -> return java.lang.Boolean.TYPE
                "Int" -> return Integer.TYPE
                "Long" -> return java.lang.Long.TYPE
                "Double" -> return java.lang.Double.TYPE
                "Float" -> return java.lang.Float.TYPE
                "Unit", "Void" -> return Void.TYPE
            }
        } else if (stripped == "Unit" || stripped == "Void") {
            return Void.TYPE
        }
        val fqcn = TYPE_FQCNS[stripped]
            ?: throw AssertionError("Unmapped Java type '$stripped' (from '$docType') in InterfaceSignatureDriftContractTest")
        return loadClass(fqcn)
    }

    private fun findAllJavaMethods(clazz: Class<*>): List<Method> {
        val methods = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            methods.addAll(current.declaredMethods.filterNot { it.isSynthetic || it.isBridge })
            current = current.superclass
        }
        return methods
    }

    private fun verifyJavaMemberViaReflection(sig: DocSignature, ownerClass: Class<*>): String? {
        val allMethods = findAllJavaMethods(ownerClass)
        val nameMatches = allMethods.filter { it.name == sig.memberName }
        if (nameMatches.isEmpty()) {
            return "${sig.interfaceId}: Java method '${sig.memberName}' not found on ${sig.ownerName}"
        }

        val expectedParamTypes = sig.params.map { resolveJavaJvmType(it.rawType, it.isNullable) }
        val exactMatch = nameMatches.firstOrNull { method ->
            method.parameterCount == expectedParamTypes.size &&
                method.parameterTypes.toList() == expectedParamTypes
        } ?: return "${sig.interfaceId}: Java signature/arity mismatch for ${sig.ownerName}.${sig.memberName} " +
            "(expected ${expectedParamTypes.size} params [${expectedParamTypes.joinToString(", ") { it.simpleName }}])"

        val expectedReturn = resolveJavaJvmType(sig.returnType, sig.isReturnNullable)
        if (exactMatch.returnType != expectedReturn) {
            return "${sig.interfaceId}: Java return type mismatch on ${sig.ownerName}.${sig.memberName}: " +
                "expected ${expectedReturn.name}, actual ${exactMatch.returnType.name}"
        }

        // Distinguish explicit generic wildcard return (e.g. List<FilteredSensorData<?>>) vs raw return (SensorData).
        val docArgs = extractTopLevelGenericArgs(sig.returnType)
        if (docArgs.isEmpty() && exactMatch.genericReturnType is ParameterizedType) {
            return "${sig.interfaceId}: Java return type on ${sig.ownerName}.${sig.memberName} is parameterized " +
                "(${exactMatch.genericReturnType}), but docs specify non-generic '${sig.returnType}'"
        }
        if (docArgs.isNotEmpty() && exactMatch.genericReturnType !is ParameterizedType) {
            return "${sig.interfaceId}: Java return type on ${sig.ownerName}.${sig.memberName} is raw " +
                "(${exactMatch.returnType.simpleName}), but docs specify generic '${sig.returnType}'"
        }

        return null
    }

    private fun verifySignatureMatchesReflection(sig: DocSignature): String? {
        val fqcn = OWNER_FQCNS[sig.ownerName]
            ?: return "${sig.interfaceId}: Unknown owner class '${sig.ownerName}' for `${sig.rawDeclaration}`"
        val ownerClass = loadClass(fqcn)
        return if (isKotlinClass(ownerClass)) {
            verifyKotlinMemberViaKClass(sig, ownerClass)
        } else {
            verifyJavaMemberViaReflection(sig, ownerClass)
        }
    }

    /**
     * TST-PRO-115.1: Verifies that every documented operation/property signature in
     * `docs/architecture/interfaces.md` matches the compiled production bytecode in name,
     * parameter count (arity), parameter types, return type, and nullability via JVM reflection.
     */
    @Test
    fun testInterfacesDoc_allDocumentedSignaturesMatchCompiledBytecodeViaReflection() {
        val interfacesFile = findRepoRootFile("docs/architecture/interfaces.md")
        val signatures = parseDocSignatures(interfacesFile.readText())
        assertTrue("Must parse at least 20 documented signatures from interfaces.md (parsed=${signatures.size})", signatures.size >= 20)

        val mismatches = signatures.mapNotNull { verifySignatureMatchesReflection(it) }
        assertTrue(
            "Interface signature drift detected against compiled production classes (REQ-PRO-115.1, REQ-PRO-115.3):\n  " +
                mismatches.joinToString("\n  "),
            mismatches.isEmpty()
        )
    }

    /**
     * TST-PRO-115.2: Verifies via JVM reflection that `TrackerService` declares
     * `acquireWakeLock` and `releaseWakeLock` referenced as symbol anchors in `docs/architecture.md` §4.9.
     */
    @Test
    fun testArchitectureDoc_wakeLockSymbolAnchorsExistInTrackerServiceViaReflection() {
        val trackerClass = loadClass("com.atrainingtracker.trainingtracker.tracker.TrackerService")
        val declaredNames = trackerClass.declaredMethods.map { it.name }.toSet()
        assertTrue("TrackerService must declare acquireWakeLock()", "acquireWakeLock" in declaredNames)
        assertTrue("TrackerService must declare releaseWakeLock()", "releaseWakeLock" in declaredNames)

        val archText = findRepoRootFile("docs/architecture.md").readText()
        assertTrue("architecture.md must reference TrackerService#acquireWakeLock", archText.contains("TrackerService#acquireWakeLock"))
        assertTrue("architecture.md must reference TrackerService#releaseWakeLock", archText.contains("TrackerService#releaseWakeLock"))
    }

    /**
     * TST-PRO-115.1 (Negative Tripwire): Verifies that the reflection signature checker catches
     * arity drift (2-param evaluateTick) and return-nullability drift (non-null calculateNormalizedPower).
     */
    @Test
    fun testSignatureDriftChecker_catchesArityAndNullabilityDrift() {
        val driftedArity = DocSignature(
            interfaceId = "IF-SWE-05",
            ownerName = "AutoPauseManager",
            kind = DocSignature.Kind.FUN,
            isSuspend = false,
            memberName = "evaluateTick",
            params = listOf(
                DocParam("currentSpeedMps", "Double?", isNullable = true),
                DocParam("currentTimestampMs", "Long", isNullable = false)
            ),
            returnType = "AutoPauseTransition",
            isReturnNullable = false,
            rawDeclaration = "fun evaluateTick(currentSpeedMps: Double?, currentTimestampMs: Long): AutoPauseTransition"
        )
        val arityError = verifySignatureMatchesReflection(driftedArity)
        assertTrue("Must reject drifted 2-param evaluateTick signature (got: $arityError)", arityError != null && arityError.contains("mismatch"))

        val driftedNullability = DocSignature(
            interfaceId = "IF-SWE-05",
            ownerName = "PowerPhysiologyCalculator",
            kind = DocSignature.Kind.FUN,
            isSuspend = false,
            memberName = "calculateNormalizedPower",
            params = listOf(
                DocParam("powerSamplesWatts", "List<Double?>", isNullable = false),
                DocParam("sampleIntervalSec", "Double", isNullable = false)
            ),
            returnType = "Double",
            isReturnNullable = false,
            rawDeclaration = "fun calculateNormalizedPower(powerSamplesWatts: List<Double?>, sampleIntervalSec: Double): Double"
        )
        val nullabilityError = verifySignatureMatchesReflection(driftedNullability)
        assertTrue(
            "Must reject non-null Double return on nullable calculateNormalizedPower (got: $nullabilityError)",
            nullabilityError != null && nullabilityError.contains("Return type/nullability mismatch")
        )
    }
}
