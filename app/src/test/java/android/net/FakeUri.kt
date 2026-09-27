package android.net

import java.io.File

class FakeUri(
    private val uriString: String,
    private val explicitPath: String? = null
) : Uri() {
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = if (uriString.startsWith("file://")) "file" else "content"
    override fun getSchemeSpecificPart(): String = uriString.substringAfter(":")
    override fun getEncodedSchemeSpecificPart(): String = schemeSpecificPart
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getPath(): String? = explicitPath ?: if (uriString.startsWith("file://")) uriString.removePrefix("file://") else uriString
    override fun getEncodedPath(): String? = path
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = path?.split('/')?.filter { it.isNotEmpty() } ?: emptyList()
    override fun getLastPathSegment(): String? = pathSegments.lastOrNull()
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun toString(): String = uriString
    override fun compareTo(other: Uri?): Int = uriString.compareTo(other?.toString() ?: "")
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: android.os.Parcel, flags: Int) {}
    override fun equals(other: Any?): Boolean = other is Uri && other.toString() == this.toString()
    override fun hashCode(): Int = uriString.hashCode()

    companion object {
        fun fromFile(file: File): FakeUri = FakeUri("file://${file.absolutePath}", file.absolutePath)
        fun parse(uriString: String): FakeUri = FakeUri(uriString)
    }
}
