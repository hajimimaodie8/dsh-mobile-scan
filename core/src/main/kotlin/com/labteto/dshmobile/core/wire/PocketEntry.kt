package com.labteto.dshmobile.core.wire

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * An entry address read from a QR: an origin that answers with a harness session.
 *
 * This is the second kind of code this app understands, and it is deliberately not a second
 * protocol. A `dsh-relay` pairing payload carries a single-use code that becomes a bearer token;
 * this carries nothing but an address, and the credential comes back as a cookie from the address
 * itself. Pasting the harness's own startup URL has always worked that way — [HarnessSession]
 * exists for it — and `dsh-pocket` publishes exactly the same kind of thing as its QR: the LAN
 * origin of its proxy, as a bare URL with no token, because the proxy issues the browser session
 * on a plain `GET /`.
 *
 * Keeping it a *separate* type from [RelayPairingPayload] is the point. The two lead to different
 * credentials, different transports and different failure copy, and a single "address" type would
 * have to guess which of the two it was at every use site.
 */
data class PocketEntry(
    /** Origin to talk to, scheme and port included, with no trailing slash. */
    val baseUrl: String,
    val host: String,
    val port: Int,
    val useTls: Boolean,
    /**
     * A token the QR carried, when it carried one.
     *
     * Absent for a Pocket LAN code: that proxy hands out its session to whoever asks, so there is
     * nothing to present. Present when someone scans a code that does name one, and passed through
     * unchanged in that case.
     */
    val token: String?,
) {
    /** What the operator sees, and what a failure names. */
    val authority: String get() = "$host:$port"

    /** A proxy on this handset is reached at loopback and is not a host across the network. */
    val isLoopback: Boolean get() = host == "127.0.0.1" || host == "::1" || host.equals("localhost", ignoreCase = true)
}

/** What a scanned code turned out to be, when it was not a relay pairing payload. */
sealed interface PocketEntryResult {
    data class Valid(val entry: PocketEntry) : PocketEntryResult

    /** Not an entry address: some other QR, a bare word, or a URL that points at a page. */
    data object NotAnEntry : PocketEntryResult
}

/**
 * Read an entry address out of a scanned code.
 *
 * The accepted shape is narrow on purpose — an origin, optionally carrying `?token=` — because the
 * scanner sees every code the user happens to point it at. A URL with a path is somebody's web
 * page, and treating it as an entry would turn "wrong code" into "connected to something".
 *
 * Ports are read as `HttpUrl` reports them, which is the scheme's default when the URL omits one,
 * so `http://host` and `http://host:80` produce the same entry.
 */
object PocketEntryParser {

    fun parse(text: String): PocketEntryResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return PocketEntryResult.NotAnEntry
        // A relay payload is JSON, and is handled before this parser is ever consulted; rejecting
        // the shape here keeps the two from being confused if the order ever changes.
        if (trimmed.startsWith("{")) return PocketEntryResult.NotAnEntry

        val url = trimmed.toHttpUrlOrNull() ?: return PocketEntryResult.NotAnEntry
        if (url.encodedPath.isNotEmpty() && url.encodedPath != "/") return PocketEntryResult.NotAnEntry

        val token = url.queryParameter("token")?.trim()?.takeIf { it.isNotEmpty() }
        return PocketEntryResult.Valid(
            PocketEntry(
                baseUrl = "${url.scheme}://${url.host}:${url.port}",
                host = url.host,
                port = url.port,
                useTls = url.isHttps,
                token = token,
            ),
        )
    }
}
