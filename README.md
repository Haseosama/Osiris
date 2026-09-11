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

Cette appli ne fonctionne qu'avec une instance Osiris joignable depuis ton téléphone. Deux
façons de la lancer :

**Node.js direct** (le plus simple pour tester en local) :

```bash
git clone https://github.com/simplifaisoul/osiris.git
cd osiris
npm install
npm run dev
```

Ouvre [http://localhost:3000](http://localhost:3000) sur ton PC pour vérifier que ça tourne.
Fonctionne sans aucune clé API — toutes les couches keyless marchent tout de suite, seul le
scanner RECON répond 503 tant que `SCANNER_URL`/`SCANNER_KEY` ne sont pas renseignés dans un
`.env` (`cp .env.example .env`).

**Docker** (isolation, redémarre en arrière-plan) :

```bash
cp .env.example .env
docker compose up -d
```

Voir [DOCKER.md](https://github.com/simplifaisoul/osiris/blob/master/DOCKER.md) du dépôt Osiris
pour l'auto-hébergement complet (CasaOS, clés API optionnelles FIRMS/OpenSky/N2YO, scanner RECON).

Dans les deux cas, ton téléphone doit joindre l'IP locale de ton PC (même Wi-Fi), pas
`localhost` — vois l'étape 2.

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
- [x] **Phase 3** — CCTV (`/api/cctv`, ~17k caméras, clustering MapLibre natif ; tap sur une
      caméra individuelle → snapshot JPEG ou lien externe pour les flux MP4 ; zoome pour faire
      éclater un cluster, pas encore de tap-to-zoom dédié), OSINT Telegram (`/api/news`, en fait
      le flux Telegram/RSS géoparsé — pas un flux "actu" classique malgré son nom ; tap sur un
      point → titre/description/lien)
- [x] **Phase 4** — panneau RECON (icône outils sur la carte) : scanner réseau (`/api/scanner`,
      503 tant que `SCANNER_URL`/`SCANNER_KEY` ne sont pas configurés côté backend), DNS, WHOIS,
      certificats SSL, IP intelligence, CVE, wallet crypto, sanctions OFAC, espace
      (`/api/space-weather`). Chaque outil affiche la réponse JSON brute mise en forme plutôt que
      des vues dédiées par outil — suffisant pour un premier jet, à enrichir en Phase 5 si besoin
- [x] **Phase 5** — vues RECON dédiées pour les 4 outils qui s'y prêtaient bien (CVE, DNS, IP
      Intelligence, Sanctions OFAC — CveResult/DnsResult/IpIntelResult/SanctionsResult typés,
      avec repli automatique sur le JSON brut si le décodage échoue) ; scanner/WHOIS/certificats
      SSL/wallet crypto/espace restent en JSON brut, leurs schémas étant trop profonds ou trop
      instables pour le rapport effort/valeur d'un DTO dédié ; bannière "Backend injoignable" sur
      la carte quand toutes les couches actives échouent contre un backend pourtant configuré
      (distinct du bandeau "backend non configuré")
- [x] **Cache local par couche** — `LayerCache` persiste le dernier payload reçu de chaque
      couche dans un fichier JSON privé (`filesDir/layer_cache/`), rechargé au lancement avant
      que le premier polling démarre (pour ne pas se faire écraser par une lecture disque plus
      lente qu'une réponse réseau) : la carte affiche les dernières données connues
      immédiatement plutôt que de repartir à vide à chaque ouverture de l'appli
- [ ] **Pistes restantes** — vues dédiées pour WHOIS/scanner/crypto, tap-to-zoom sur un cluster
      CCTV, animation des arcs cyberattaques, icône d'appli plus travaillée, purge du cache
      au-delà d'un certain âge (aujourd'hui il n'expire jamais)

## Licence

MIT, comme le dépôt [Osiris](https://github.com/simplifaisoul/osiris) dont ce client s'inspire.
