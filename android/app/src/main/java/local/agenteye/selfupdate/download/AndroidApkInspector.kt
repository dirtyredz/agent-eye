package local.agenteye.selfupdate.download

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import local.agenteye.selfupdate.release.ReleasePolicy

/**
 * PackageManager adapter for [ApkInspector]: signing-info APIs on API 28+, legacy signatures
 * on API 24-27. More than one signer (or rotation history ambiguity) yields no single signer,
 * which the verifier refuses.
 */
class AndroidApkInspector(private val context: Context) : ApkInspector {
    @Suppress("DEPRECATION")
    private val flags = if (Build.VERSION.SDK_INT >=
        28
    ) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

    override fun installed(): PackageIdentity =
        identity(context.packageManager.getPackageInfo(context.packageName, flags))
            ?: PackageIdentity(context.packageName, null, 0, 0, emptyList(), true)

    override fun archive(file: File): PackageIdentity? =
        context.packageManager.getPackageArchiveInfo(file.path, flags)?.let { info ->
            // Archive info has no source path set; minSdk/flags still come from the manifest.
            info.applicationInfo?.let {
                it.sourceDir = file.path
                it.publicSourceDir = file.path
            }
            identity(info)
        }

    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): PackageIdentity? {
        val app = info.applicationInfo ?: return null
        val code = if (Build.VERSION.SDK_INT >=
            28
        ) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        val certificates = if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo
            if (signing == null ||
                signing.hasMultipleSigners()
            ) {
                emptyList()
            } else {
                signing.apkContentsSigners.toList()
            }
        } else {
            info.signatures?.toList().orEmpty()
        }
        return PackageIdentity(
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = code,
            minSdk = app.minSdkVersion,
            signers = certificates.map {
                ReleasePolicy.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
            },
            debuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        )
    }
}
