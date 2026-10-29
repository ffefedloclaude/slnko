# ☀️ Slnko

Android aplikácia, ktorá podľa polohy telefónu ukazuje **čas východu a západu slnka**
a pošle **upozornenie** zvolený počet minút pred východom a/alebo západom.

## Stiahnutie

1. Otvor sekciu **Releases** vpravo na stránke repozitára (alebo `…/releases/latest`).
2. Stiahni súbor `Slnko-1.0.X.apk` priamo v telefóne.
3. Otvor ho a povoľ inštaláciu z neznámych zdrojov (telefón sa to opýta sám).
4. Pri prvom spustení povoľ **polohu** a **upozornenia**.

## Funkcie

- Výpočet východu a západu slnka offline (presnosť cca ±1 min), podľa aktuálnej polohy a časového pásma telefónu
- Dnešné časy, dĺžka dňa a časy na zajtra
- Samostatné upozornenie pred východom aj pred západom, 0–180 minút vopred
- Upozornenia fungujú aj po reštarte telefónu
- Tlačidlo na vyskúšanie upozornenia

## Ako vzniká APK

Každý `push` do vetvy `main` spustí GitHub Actions (`.github/workflows/build.yml`),
ktoré aplikáciu zostavia a zverejnia ako nové vydanie v sekcii Releases.
Zostavenie sa dá spustiť aj ručne: **Actions → Zostavenie APK → Run workflow**.

## Tip

Ak upozornenia chodia neskoro, v nastaveniach telefónu vypni pre aplikáciu Slnko
**optimalizáciu batérie** (Nastavenia → Aplikácie → Slnko → Batéria → Bez obmedzení).
