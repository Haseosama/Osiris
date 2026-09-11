# Osiris — client Android natif

Client Android natif (Kotlin/Jetpack Compose, **zéro WebView**) pour
[Osiris](https://github.com/simplifaisoul/osiris), le dashboard OSINT open source (vols,
séismes, incendies, CCTV, actu 24/7, météo sévère, espace, cyber, zones de conflit, crypto/OFAC,
Telegram OSINT, toolkit RECON...).

## Architecture

Osiris (le dépôt web) est une appli Next.js dont ~40 routes `/api/*` agrègent en temps réel des
dizaines de sources externes (OpenSky, USGS, NASA FIRMS, TfL/WSDOT/Caltrans, GDELT, OpenSanctions,
NVD...). Plutôt que de dupliquer toute cette logique serveur en Kotlin, cette appli **réécrit
entièrement le client** en natif et consomme l'API JSON déjà existante d'une instance Osiris que
tu auto-héberges.

```
Appli Android (Kotlin/Compose, MapLibre Native)  →  ton backend Osiris auto-hébergé (Docker)
```

### Pile technique

- Kotlin + Jetpack Compose + Material3
- **MapLibre Native Android SDK** — rendu carte vectoriel GPU, équivalent natif de MapLibre GL JS
- Retrofit + OkHttp + kotlinx.serialization pour l'API REST
- Coroutines/Flow pour le polling par couche (une couche n'interroge le backend que si elle est
  activée, à la manière du `layerFetchedRef` du frontend web)
- DataStore Preferences pour l'URL du backend

## 1. Héberger le backend Osiris

Cette appli ne fonctionne qu'avec une instance Osiris joignable depuis ton téléphone. Suis le
guide du dépôt source :

```bash
git clone https://github.com/simplifaisoul/osiris.git
cd osiris
cp .env.template .env
docker compose up -d
```

Voir [DOCKER.md](https://github.com/simplifaisoul/osiris/blob/master/DOCKER.md) du dépôt Osiris
pour l'auto-hébergement complet (CasaOS, clés API optionnelles FIRMS/OpenSky/N2YO, scanner RECON).

## 2. Ouvrir ce projet

`File > Open` dans Android Studio, sélectionne ce dossier, laisse Gradle synchroniser.

Au premier lancement, ouvre **Réglages** et renseigne l'URL de ton backend (ex.
`http://192.168.1.10:3000`), puis « Tester la connexion ».

## Roadmap

- [x] **Phase 0** — socle (carte MapLibre, réglages backend, réseau)
- [x] **Phase 1** — vols, séismes, incendies, météo sévère, zones de conflit
- [x] **Phase 2** — maritime (ports/chokepoints/AIS), satellites (SGP4 déjà calculé côté
      backend), actu en direct (tap sur un dot → WebView si `embed_allowed`, sinon ouverture
      externe — `/api/live-news` sert des flux YouTube, pas du HLS brut), cyberattaques
      (lignes statiques source→cible ; pas encore d'animation comme sur le web)
- [ ] **Phase 3** — CCTV (clustering), OSINT Telegram (`/api/news`, en fait le flux Telegram/RSS
      géoparsé — pas un flux "actu" classique malgré son nom)
- [ ] **Phase 4** — panneau RECON en liste (pas des pins carte) : scanner ports, WHOIS/DNS, SSL,
      CVE (`/api/cyber-threats` — pas de lat/lng, ne peut pas être une couche carte), wallet
      crypto, OFAC ; + espace (`/api/space-weather` — indice Kp global, pas géolocalisé)
- [ ] **Phase 5** — polish (thème, cadence de polling, icône, offline)

## Licence

MIT, comme le dépôt [Osiris](https://github.com/simplifaisoul/osiris) dont ce client s'inspire.
