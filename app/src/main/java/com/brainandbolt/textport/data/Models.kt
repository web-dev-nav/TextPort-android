package com.brainandbolt.textport.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class ActivateRequest(
    val code: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("device_brand") val deviceBrand: String? = null,
    @SerialName("device_manufacturer") val deviceManufacturer: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("sdk_int") val sdkInt: String? = null,
    @SerialName("device_hardware") val deviceHardware: String? = null,
    @SerialName("device_board") val deviceBoard: String? = null,
    @SerialName("device_product") val deviceProduct: String? = null,
    @SerialName("app_version") val appVersion: String? = null
)

@Serializable
data class RequestCodeRequest(
    val label: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("device_brand") val deviceBrand: String? = null,
    @SerialName("device_manufacturer") val deviceManufacturer: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("sdk_int") val sdkInt: String? = null,
    @SerialName("device_hardware") val deviceHardware: String? = null,
    @SerialName("device_board") val deviceBoard: String? = null,
    @SerialName("device_product") val deviceProduct: String? = null
)

@Serializable
data class AuthResponse(val token: String)

@Serializable
data class RequestCodeResponse(
    val ok: Boolean = false,
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class SmsSyncItem(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val direction: String = "inbound"
)

@Serializable
data class SyncRequest(
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("device_brand") val deviceBrand: String? = null,
    @SerialName("device_manufacturer") val deviceManufacturer: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("sdk_int") val sdkInt: String? = null,
    @SerialName("device_hardware") val deviceHardware: String? = null,
    @SerialName("device_board") val deviceBoard: String? = null,
    @SerialName("device_product") val deviceProduct: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    val messages: List<SmsSyncItem>
)

@Serializable
data class BasicResponse(val ok: Boolean, val message: String? = null)

@Serializable
data class ExportResponse(@SerialName("download_url") val downloadUrl: String)
