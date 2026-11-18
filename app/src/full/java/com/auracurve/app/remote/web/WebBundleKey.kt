package com.auracurve.app.remote.web

/**
 * The public key that signs the web remote's bundles (module 10, Q-10-2), printed by
 * `pixel-car-remote/tools/build.py --public-key`. The private key lives only on the developer's
 * computer (~/.config/pixel-car-remote/web-signing-key.pem), never in a repository or on the phone.
 * A new key needs a new app release; bundles signed with another key are refused.
 */
object WebBundleKey {
    const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEzV27cKE3+vQXd9oSNeirfsUEgCC78OgnSluZfnVDmczzsRs2IgYWqZpI/TDnDzybhGwdRMFE4bQ+eR8qg4uzmg=="
}
