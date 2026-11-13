package com.clearcapture.clearcapture.api

import androidx.annotation.FontRes
import com.clearcapture.clearcapture.api.spi.CaptureLogger
import com.clearcapture.clearcapture.api.spi.CaptureProfileProvider

/** SDK-wide configuration, passed to [ClearCapture.initialize]. */
public class ClearCaptureConfig @JvmOverloads constructor(
    /** Who asks the user for permissions. */
    public val permissionMode: PermissionMode = PermissionMode.SDK_MANAGED,
    /** The few appearance options a host may set. */
    public val appearance: Appearance = Appearance(),
    /** Host replacements for SDK components. */
    public val dependencies: CaptureDependencies = CaptureDependencies(),
    /** Optional callbacks for host analytics. */
    public val eventListener: CaptureEventListener? = null,
)

/** Who asks the user for camera and location permissions. */
public enum class PermissionMode {
    /** The SDK asks inside its flow, with its own explanation screens. */
    SDK_MANAGED,

    /** The host asks before launching; a launch with anything missing returns [CaptureResult.Failed] at once. */
    HOST_MANAGED,
}

/** Light, dark or follow the system. */
public enum class DarkMode { FOLLOW_SYSTEM, LIGHT, DARK }

/** Corner style of cards and buttons. */
public enum class CornerStyle { ROUNDED, SQUARE }

/**
 * Appearance options. Status colours, camera-overlay colours, touch-target sizes and minimum text sizes
 * cannot be changed. A brand pair with less than 4.5:1 contrast is rejected, logged, and the default used.
 */
public class Appearance @JvmOverloads constructor(
    /** ARGB, for example `0xFF0B6B53`; `null` keeps the SDK default. */
    public val brandColor: Long? = null,
    /** ARGB colour of text and icons on [brandColor]. */
    public val onBrandColor: Long? = null,
    public val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    public val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    /** A font resource of the host; `null` keeps the system font. */
    @param:FontRes public val fontFamilyResId: Int? = null,
)

/** Host replacements for SDK components. Each `null` keeps the SDK default. */
public class CaptureDependencies @JvmOverloads constructor(
    public val profileProvider: CaptureProfileProvider? = null,
    public val logger: CaptureLogger? = null,
)
