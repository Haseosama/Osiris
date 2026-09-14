package com.osiris.app.recon

data class SecondaryParam(val name: String, val options: List<String>, val default: String)

/**
 * The RECON toolkit is a list panel, not map pins — a CVE or a WHOIS record has no lat/lng.
 * Every tool calls its upstream source directly from the phone and the response is shown as
 * pretty-printed JSON (see [com.osiris.app.recon.ReconRepository]) — no backend involved at all
 * any more.
 */
enum class ReconTool(
    val label: String,
    val paramName: String,
    val hint: String,
    val secondaryParam: SecondaryParam? = null,
) {
    SPACE_WEATHER("Espace", "", ""),
    PORT_SCAN(
        "Scanner réseau",
        "target",
        "IP ou domaine (ex: exemple.com)",
        secondaryParam = SecondaryParam(
            name = "type",
            options = listOf("quick", "ssl", "headers", "rdns", "subdomains", "whois", "geoloc"),
            default = "quick",
        ),
    ),
    DNS("DNS", "domain", "exemple.com"),
    WHOIS("WHOIS", "domain", "exemple.com"),
    SSL_CERTS("Certificats SSL", "domain", "exemple.com"),
    IP_INTEL("IP Intelligence", "ip", "8.8.8.8"),
    CVE("CVE", "cve", "CVE-2024-12345"),
    CRYPTO_WALLET("Wallet crypto", "address", "Adresse BTC / ETH / SOL"),
    SANCTIONS("Sanctions OFAC", "query", "Nom, organisation, navire… (4+ car.)"),
    USERNAME("Pseudo", "username", "Pseudo à rechercher (ex: johndoe)"),
    LEAKS("Fuites de données", "email", "Adresse email"),
    GITHUB("GitHub", "user", "Nom d'utilisateur GitHub"),
    PHONE("Téléphone", "number", "Numéro (ex: +33612345678)"),
    MAC("Adresse MAC", "mac", "Adresse MAC (ex: AC:DE:48:00:11:22)"),
}
