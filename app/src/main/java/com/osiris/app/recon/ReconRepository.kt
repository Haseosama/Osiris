package com.osiris.app.recon

import com.osiris.app.recon.source.CveSource
import com.osiris.app.recon.source.DnsSource
import com.osiris.app.recon.source.GithubSource
import com.osiris.app.recon.source.IpIntelSource
import com.osiris.app.recon.source.LeaksSource
import com.osiris.app.recon.source.MacSource
import com.osiris.app.recon.source.NetworkScannerSource
import com.osiris.app.recon.source.PhoneSource
import com.osiris.app.recon.source.SanctionsSource
import com.osiris.app.recon.source.SpaceWeatherSource
import com.osiris.app.recon.source.SslCertsSource
import com.osiris.app.recon.source.UsernameSherlockSource
import com.osiris.app.recon.source.WalletIntelSource
import com.osiris.app.recon.source.WhoisSource

/** Every RECON tool hits its upstream source directly from the phone — see the "no backend"
 * migration plan. [ReconTool.SPACE_WEATHER] ([SpaceWeatherSource]) was the last one still
 * proxied through the self-hosted Osiris backend. */
class ReconRepository {

    suspend fun query(tool: ReconTool, value: String, secondaryValue: String?): Result<ReconResult> =
        runCatching {
            when (tool) {
                ReconTool.DNS -> ReconResult.Dns(DnsSource.lookup(value))
                ReconTool.SSL_CERTS -> ReconResult.Raw(SslCertsSource.lookup(value))
                ReconTool.CVE -> ReconResult.Cve(CveSource.lookup(value))
                ReconTool.LEAKS -> ReconResult.Leaks(LeaksSource.lookup(value))
                ReconTool.GITHUB -> ReconResult.Github(GithubSource.lookup(value))
                ReconTool.MAC -> ReconResult.Mac(MacSource.lookup(value))
                ReconTool.PHONE -> ReconResult.Phone(PhoneSource.lookup(value))
                ReconTool.WHOIS -> ReconResult.Whois(WhoisSource.lookup(value))
                ReconTool.IP_INTEL -> ReconResult.IpIntel(IpIntelSource.lookup(value))
                ReconTool.SANCTIONS -> ReconResult.Sanctions(SanctionsSource.search(value, schema = null))
                ReconTool.CRYPTO_WALLET -> ReconResult.CryptoWallet(WalletIntelSource.lookup(value, chainOverride = null))
                ReconTool.USERNAME -> ReconResult.Username(UsernameSherlockSource.lookup(value))
                ReconTool.PORT_SCAN -> ReconResult.Raw(NetworkScannerSource.scan(value, secondaryValue ?: "quick"))
                ReconTool.SPACE_WEATHER -> ReconResult.Raw(SpaceWeatherSource.fetch())
            }
        }
}
