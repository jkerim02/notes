# Roadmap: Lični organizator

Redoslijed prati SPEC.md §8. Status: ✅ urađeno u kodu, 🔶 urađeno uz ograničenje (vidi napomenu).

| Faza | Sadržaj | Status |
|---|---|---|
| 0. Temelj | Gradle projekat, tamna tema, fontovi Newsreader/DM Sans, Room baza, navigacija (5 kartica), ikona aplikacije iz `diary-journal-color-icon.svg` | ✅ |
| 1. Namaz | Vaktija.ba (api.vaktija.ba/vaktija/v1), keširanje mjeseca, sljedeći vakat, praćenje, niz, hadis dana, mjesečni pregled, stalna notifikacija + večernje pitanje (alarmi) | ✅ |
| 2. Podsjetnici i bilješke | Podsjetnici s ponavljanjem i alarmima (Gotovo / Odgodi 10 min), bilješke s oznakama, pretraga, Gemini sređivanje s vraćanjem originala, JSON backup | ✅ / 🔶 backup preko sistemskog izbornika (Drive se bira tamo), ne Drive API |
| 3. Novčanik i štednja | Unosi (potrošeno/zarađeno/odvojeno), trajne kategorije i izvori, ciljevi, scenariji pada cijene, kalkulator, tabela dnevnog odvajanja, Gemini uvid i procjena cijene | ✅ |
| 4. Knjige i rječnik | Knjige, napredak, riječi uz stranicu, Gemini prijevod/definicija/primjer, offline nakon čuvanja | ✅ |
| 5. Izvještaji | Sedmica/mjesec/godina + proizvoljan period, „Šta ti je promaklo“ / „Šta je išlo dobro“, PDF izvoz | ✅ |
| 6. Profil i postavke | Ime, nadimak, grad (promjena povlači nova vremena), otisak prsta, Gemini ključ (šifriran), izvoz podataka | ✅ |

## Napomene / šta ti treba uraditi
- **Hadisi**: `assets/hadith.json` sadrži samo mali početni skup. Prije stvarne upotrebe provjeri arapski tekst i prijevod u pouzdanoj zbirci i dopuni listu.
- **Gemini ključ**: upiši u Profil → Gemini API ključ.
- **Google Drive backup**: pravi automatski Drive backup traži OAuth klijent u Google Cloud konzoli. Zasad „Sačuvaj sad“ otvara sistemski izbornik gdje možeš izabrati Drive.
- **Alarmi**: na Androidu 12+ dozvoli „Alarmi i podsjetnici“; na Xiaomi/Samsung isključi optimizaciju baterije za aplikaciju.
