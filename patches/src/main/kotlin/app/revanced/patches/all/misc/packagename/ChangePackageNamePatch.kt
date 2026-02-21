package app.revanced.patches.all.misc.packagename

import app.revanced.patcher.patch.Option
import app.revanced.patcher.patch.booleanOption
import app.revanced.patcher.patch.resourcePatch
import app.revanced.patcher.patch.stringOption
import app.revanced.util.getNode
import org.w3c.dom.Element

private lateinit var packageNameOption: Option<String>

/**
 * Set the package name to use. If this is the first call, the value is set.
 * Any subsequent call will not change the value.
 *
 * @return The package name option value.
 */
fun setOrGetFallbackPackageName(newPackageName: String): String {
    val option = packageNameOption
    if (option.value == option.default) {
        option.value = newPackageName
    }
    return option.value!!
}

val changePackageNamePatch = resourcePatch(
    name = "Change package name",
    description = "Appends \".revanced\" to the package name by default.",
    use = false,
) {
    packageNameOption = stringOption(
        key = "packageName",
        default = "Default",
        values = mapOf("Default" to "Default"),
        title = "Package name",
        description = "The name of the package to rename the app to.",
        required = true,
    ) {
        it == "Default" || it!!.matches(Regex("^[a-z]\\w*(\\.[a-z]\\w*)+$"))
    }

    val updatePermissions by booleanOption(
        key = "updatePermissions",
        default = false,
        title = "Update permissions",
        description = "Updates DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION references to the new package name.",
        required = true,
    )

    val updateProviders by booleanOption(
        key = "updateProviders",
        default = false,
        title = "Update providers",
        description = "Rewrites android:authorities on <provider> elements to the new package name.",
        required = true,
    )

    finalize {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.getNode("manifest") as Element
            val packageName = manifest.getAttribute("package")

            val newPackageName = if (packageNameOption.value == "Default") {
                "$packageName.revanced"
            } else {
                packageNameOption.value!!
            }

            manifest.setAttribute("package", newPackageName)

            if (updatePermissions == true) {
                val permissionName = "$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                val newPermissionName = "$newPackageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

                // Update <permission> and <uses-permission> elements.
                for (tagName in listOf("permission", "uses-permission")) {
                    val nodes = document.getElementsByTagName(tagName)
                    for (i in 0 until nodes.length) {
                        val element = nodes.item(i) as Element
                        if (element.getAttribute("android:name") == permissionName) {
                            element.setAttribute("android:name", newPermissionName)
                        }
                    }
                }
            }

            if (updateProviders == true) {
                val providers = document.getElementsByTagName("provider")
                for (i in 0 until providers.length) {
                    val provider = providers.item(i) as Element
                    val authorities = provider.getAttribute("android:authorities")
                    if (authorities.contains(packageName)) {
                        provider.setAttribute(
                            "android:authorities",
                            authorities.replace(packageName, newPackageName),
                        )
                    }
                }
            }
        }
    }
}
