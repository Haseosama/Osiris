package com.osiris.app.recon

data class SecondaryParam(val name: String, val options: List<String>, val default: String)

/**
 * The RECON toolkit is a list panel, not map pins — a CVE or a WHOIS record has no lat/lng.
 * Every tool hits a keyless GET on the self-hosted Osiris backend and the response is shown
 * as pretty-printed JSON (see [com.osiris.app.recon.ReconRepository]); [PORT_SCAN] is the one
 * exception that needs SCANNER_URL/SCANNER_KEY configured server-side and returns 503 otherwise.
 */
enum class ReconTool(
    val label: String,
    val path: String,
    val paramName: String,
    val hint: String,
    val secondaryParam: SecondaryParam? = null,
) {
    SPACE_WEATHER("Espace", "api/space-weather", "", ""),
    PORT_SCAN(
        "Scanner réseau",
        "api/scanner",
        "target",
        "IP ou domaine (ex: exemple.com)",
        secondaryParam = SecondaryParam(
            name = "type",
            options = listOf("quick", "ssl", "headers", "rdns", "subdomains", "tech", "whois", "geoloc", "vuln"),
            default = "quick",
        ),
    ),
    DNS("DNS", "api/osint/dns", "domain", "exemple.com"),
    WHOIS("WHOIS", "api/osint/whois", "domain", "exemple.com"),
    SSL_CERTS("Certificats SSL", "api/osint/certs", "domain", "exemple.com"),
    IP_INTEL("IP Intelligence", "api/osint/ip", "ip", "8.8.8.8"),
    CVE("CVE", "api/osint/cve", "cve", "CVE-2024-12345"),
    CRYPTO_WALLET("Wallet crypto", "api/osint/crypto", "address", "Adresse BTC / ETH / SOL"),
    SANCTIONS("Sanctions OFAC", "api/osint/sanctions", "query", "Nom, organisation, navire… (4+ car.)"),
}
