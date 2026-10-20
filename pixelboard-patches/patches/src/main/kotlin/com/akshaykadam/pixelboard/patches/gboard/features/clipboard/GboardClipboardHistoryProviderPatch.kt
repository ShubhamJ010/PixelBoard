/**
 * PixelBoard - Gboard Enhancement Mod
 *
 * Maintained and customized by Akshay Kadam (@Akshayykadam)
 * Repository: https://github.com/Akshayykadam/PixelBoard
 * Licensed under the GNU General Public License v3.0 (GPLv3)
 */
package com.akshaykadam.pixelboard.patches.gboard.features.clipboard

import com.akshaykadam.pixelboard.patches.shared.Constants.COMPATIBILITY_GBOARD
import com.akshaykadam.pixelboard.patches.shared.ResourcePatchContext
import com.akshaykadam.pixelboard.patches.shared.resourcePatch
import com.akshaykadam.pixelboard.patches.gboard.shared.childElements
import com.akshaykadam.pixelboard.patches.gboard.shared.ensureManifestComponent
import com.akshaykadam.pixelboard.patches.gboard.shared.manifestAndroidAttribute
import com.akshaykadam.pixelboard.patches.gboard.shared.setAndroidAttribute
import org.w3c.dom.Document
import org.w3c.dom.Element

internal const val GBOARD_CLIPBOARD_HISTORY_PERMISSION =
    "com.akshaykadam.pixelboard.permission.READ_CLIPBOARD_HISTORY"

internal const val GBOARD_CLIPBOARD_HISTORY_PROVIDER_CLASS =
    "com.akshaykadam.pixelboard.extension.clipboard.GboardClipboardHistoryProvider"

internal const val GBOARD_CLIPBOARD_HISTORY_PROVIDER_AUTHORITY =
    "com.akshaykadam.pixelboard.gboard_clipboard_history"

/**
 * Registers the PixelBoard clipboard history content provider.
 *
 * The provider runs inside the keyboard process, therefore it can read the clipboard history
 * database that Gboard owns in its private data directory. The authority intentionally does not
 * depend on the host package name so the provider keeps a stable contract for companion apps
 * with or without the package rename patch.
 */
internal val gboardClipboardHistoryProviderPatch = resourcePatch(
    description = "Publish the Gboard clipboard history through a dedicated read-only provider.",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        applyClipboardHistoryProvider()
    }
}

context(context: ResourcePatchContext)
private fun applyClipboardHistoryProvider() = with(context) {
    document("AndroidManifest.xml").use { manifestDocument ->
        val manifest = manifestDocument.documentElement
        ensureManifestPermission(
            manifestDocument,
            manifest,
            GBOARD_CLIPBOARD_HISTORY_PERMISSION,
        )
        val application = manifest.childElements("application").firstOrNull() ?: return@use
        val provider = ensureManifestComponent(
            manifestDocument,
            application,
            "provider",
            GBOARD_CLIPBOARD_HISTORY_PROVIDER_CLASS,
        )
        provider.setAndroidAttribute("authorities", GBOARD_CLIPBOARD_HISTORY_PROVIDER_AUTHORITY)
        provider.setAndroidAttribute("exported", "true")
        provider.setAndroidAttribute("grantUriPermissions", "false")
    }
}

private fun ensureManifestPermission(
    document: Document,
    manifest: Element,
    permissionName: String,
) {
    val permission = manifest.childElements("permission").firstOrNull {
        it.manifestAndroidAttribute("name") == permissionName
    } ?: document.createElement("permission").also { createdPermission ->
        val application = manifest.childElements("application").firstOrNull()
        if (application == null) {
            manifest.appendChild(createdPermission)
        } else {
            manifest.insertBefore(createdPermission, application)
        }
    }
    permission.setAndroidAttribute("name", permissionName)
    permission.setAndroidAttribute("protectionLevel", "signature")
}
