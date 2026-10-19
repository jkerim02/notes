# Lični organizator: specifikacija aplikacije

Aplikacija samo za ličnu upotrebu (Android). Dizajn je u datoteci "Lični organizator" (šest ekrana za osnovni dio, uz izvještaje, novčanik i štednju). Ovaj dokument kaže šta aplikacija radi, kako izgleda i koje podatke čuva.

Svi brojevi u dizajnu (iznosi, procenti, cijena Mercedesa, vremena vakata) su **primjeri za izgled**, ne stvarni podaci. Mjesta označena sa `[...]` popunjava korisnik.

## 1. Tehnologija (prijedlog)

- Kotlin, Jetpack Compose, Material 3 (prilagođena tema ispod)
- Room (lokalna baza), DataStore (postavke)
- WorkManager + AlarmManager (tačni alarmi), stalna notifikacija
- Offline-first: sve radi bez interneta osim Vaktija.ba osvježavanja i Gemini poziva
- Backup: izvoz cijele baze u JSON na Google Drive (prvo ručno dugme, kasnije automatski)

## 2. Vizuelni identitet

Tamna tema, zlatni akcent za "sljedeće / cilj / glavna akcija", zelenkasti za "urađeno".

| Token | Boja | Upotreba |
|---|---|---|
| bg | `#0E1513` | pozadina |
| surface | `#162320` | kartice |
| surface2 | `#1D2E2A` | istaknuta polja, čipovi |
| line | `#263733` | linije, obrub |
| text | `#E9F0ED` | glavni tekst |
| text2 | `#C9D6D1` | sekundarni tekst |
| muted | `#93A8A1` | oznake, opisi |
| gold | `#E0B95C` | akcent, aktivna kartica, dugmad |
| teal | `#62C4AB` | urađeno, zarađeno, napredak |
| danger | `#E58A7B` | potrošeno, propušteno |

Ljestvica boja za mjesečni pregled namaza (broj klanjanih od 5): 5 `#62C4AB`, 4 `#3F9380`, 3 `#2D6B5E`, 2 `#21463F`, 1 `#1B332E`, 0 `#1A2825`. Tekst na poljima 5 i 4 je taman (`#0E1513`), na ostalim svijetao.

Fontovi: **Newsreader** (naslovi i veliki brojevi, težina 500–600), **DM Sans** (sav ostali tekst).

Pravila: dodirna površina najmanje 44 dp, kartice zaobljene 14–20 dp, brojevi u tabelarnom obliku, ikone linijske (stroke 1.8). Jezik interfejsa: bosanski.

## 3. Navigacija

Donja traka s pet kartica: **Namaz, Podsjetnici, Bilješke, Knjige, Novčanik**. Aktivna kartica je zlatna. Profil se otvara iz zaglavlja ekrana Namaz.

## 4. Ekrani

### Namaz (početni)
- Zaglavlje: krug s inicijalom (otvara Profil), datum, naziv izabranog grada (zadano Visoko), dugme za mjesečni pregled.
- Kartica "sljedeći vakat": naziv, vrijeme, traka napretka između prethodnog i sljedećeg vakta, "za X h Y min".
- Lista šest vakata: Zora, Izlazak sunca, Podne, Ikindija, Akšam, Jacija. Izlazak sunca nema kružić (nije farz). Kružić je prazan, zlatni (sljedeći) ili popunjen zelenom s kvačicom (klanjano).
- Dvije male kartice: "Danas: X od Y do sada" i "Niz: N dana" (uzastopni dani u kojima su klanjani svi vakti).
- Kartica "Hadis dana" ispod ta dva taba: arapski tekst, bosanski prijevod, izvor (zbirka i broj), oznaka "Sahih", dugme "Objasni" (kratko značenje, preko Gemini) i "Sačuvaj u bilješke". Jedan hadis po danu, bira se iz lokalne kurirane liste.

### Mjesečni pregled namaza
- Naslov mjeseca sa strelicama, procenat i "X od Y namaza".
- Kalendar 7 stupaca (Pon–Ned), svako polje obojeno po ljestvici iznad, današnji dan zlatni obrub, budući dani isprekidani obrub.
- Trake po vaktu (npr. Zora 17/19).
- Dugme "Izvještaj: sedmica, mjesec, godina".

### Izvještaji (Sedmica / Mjesec / Godina)
Isti raspored za sva tri perioda, s karticama za prebacivanje:
1. Procenat i broj klanjanih namaza, uz kratku napomenu.
2. Namaz po vaktu (u godišnjem po mjesecima).
3. Knjige i riječi: pročitane stranice, nove riječi, završene knjige, dani čitanja.
4. Podsjetnici: koliko urađeno od ukupno.
5. Novčanik: zarađeno, potrošeno, odvojeno u štednju, ostalo, najveći trošak.
6. **Šta ti je promaklo** (crveni naslov): propušteni vakti, preskočeni podsjetnici, dani bez čitanja, štednja ispod plana.
7. **Šta je išlo dobro** (zeleni naslov).

### Podsjetnici
- Filteri: Sve, Dnevno, Sedmično, Mjesečno, Godišnje.
- Grupe sa stavkama: naslov, vrijeme i ponavljanje, prekidač za uključivanje.
- Dugme "+" za novi podsjetnik.

### Bilješke
- Pretraga, kartica statusa sigurnosne kopije na Google Drive ("Zadnja: danas u 02:10", dugme "Sačuvaj sad").
- Bilješke u dvije kolone s oznakom (npr. Lično, Učenje, Studij), naslovom, isječkom i datumom.
- Otvorena bilješka: traka "Sredi uz Gemini" s akcijama Sredi, Sažmi, Lista zadataka, Ispravi tekst, Prevedi. Rezultat je prijedlog s dugmadima Primijeni / Odbaci; original se čuva i može se vratiti.

### Knjige i rječnik
- Trenutna knjiga: naslov, autor, napredak (stranica X od Y), broj sačuvanih riječi.
- Polje za unos riječi i dugme "Traži".
- Kartica riječi: riječ, izgovor, vrsta riječi, **prijevod na bosanski**, **definicija na engleskom**, **upotreba u kontekstu**, dugme "Sačuvaj uz str. N" i "Ponovo generiši".
- Nedavno sačuvane riječi.
- Pri online radu Gemini popunjava prijevod, definiciju i primjer. Nakon čuvanja sve je dostupno offline.

### Novčanik
- "Ostalo ovaj mjesec" (zarađeno − potrošeno − odvojeno), uz tri iznosa.
- Potrošnja po kategorijama (trake).
- Kartica "Štednja" (broj ciljeva, ukupno ušteđeno).
- Zadnji unosi: potrošeno (crveno), zarađeno (zeleno), odvojeno (zlatno).
- Kartica "Uvid · Gemini": kratak pregled gdje ide novac i šta bi promjena značila za cilj; dugme "Pitaj o troškovima".
- Dugme "+" za novi unos.

### Novi unos
Tri kartice: **Potrošeno, Zarađeno, Odvojeno**.
- Iznos (u KM), datum, polje "gdje / od koga / napomena".
- Potrošeno: kategorije kao čipovi (Cigare, Kafa, Odjeća, Izlazak, Gorivo) i dugme "Nova kategorija". Nova kategorija ostaje sačuvana.
- Zarađeno: izvori (Plata, Honorar, Prodaja) i "Novi izvor", također trajno sačuvan.
- Odvojeno: izbor cilja štednje.

### Štednja
- Ukupno ušteđeno.
- Kartice ciljeva: naziv, napredak, ušteđeno / cilj, potrebno dnevno, rok.
- Dugme "Novi cilj" (cijenu može procijeniti Gemini).

### Cilj štednje (npr. automobil)
- Naziv, godište, procijenjena cijena i raspon, napredak.
- **Kako cijena može promijeniti plan:** parametri automobila (godište, kilometraža, stanje) i izbor po čemu se planira: današnja cijena, sporiji pad (~2% godišnje), očekivani pad (~4%), brži pad (~7%). Preporuka: planirati po današnjoj cijeni.
- Dugmad "Izračunaj iz oglasa (Gemini)" i "Upiši cijenu ručno", podsjetnik za osvježavanje cijene svaka 3 mjeseca.
- Kalkulator: dnevno, sedmično, mjesečno do roka.
- Tabela "ako odvajaš svaki dan" (20, 30, 50 KM): broj dana i trajanje.

### Notifikacije
1. **Stalna notifikacija** sa svim vaktovima i istaknutim sljedećim ("Sljedeći: Akšam u 17:48").
2. **Večernje pitanje** (22:00): "Jesi li danas klanjao sve vakate?" s dugmadima "Da, sve" i "Nisam sve".
3. **Podsjetnik** s dugmadima "Gotovo" i "Odgodi 10 min".

### Profil
Otvara se dodirom na krug s inicijalom u zaglavlju ekrana Namaz (nije kartica u donjoj traci).
- **Kartica profila:** slika (ili inicijal), ime, nadimak (`@[nadimak]`), izabrani grad. Dugme "Uredi" otvara polja za ime, nadimak i grad, a "Sačuvaj" potvrđuje izmjene. Uz to tri male kartice: niz dana, broj riječi, napredak cilja štednje.
- **Grad za vaktiju:** zadano **Visoko**, korisnik može izabrati bilo koji drugi grad (čipovi za česte gradove i polje za pretragu). Izbor odmah mijenja naziv grada na ekranu Namaz i vremena vakata.
- **Izvještaj za period:** čipovi Zadnjih 7 dana, Zadnjih 30 dana, Ovaj mjesec, Ova godina i **Svoj period**. Polja Od i Do prikazuju izabrani raspon, a kod "Svoj period" korisnik sam bira datume. Čipovi za uključivanje: Namaz, Knjige, Podsjetnici, Novčanik. Dugmad: "Prikaži izvještaj" (isti raspored kao Izvještaji iz odjeljka iznad, ali za izabrani raspon) i "Izvezi kao PDF".
- **Aplikacija:** Vaktija (grad), Notifikacije, Zaključavanje (otisak prsta), Valuta (KM), Jezik (bosanski).
- **Podaci:** Sigurnosna kopija (Google Drive), Izvezi podatke (JSON), Gemini API ključ, O aplikaciji.
- **Podnožje:** "Product by JKs", "2026 · verzija 1.0.0".

## 5. Podaci (Room)

- `PrayerLog(date, vakat, done)`
- `Reminder(id, title, time, repeat[daily|weekly|monthly|yearly], dayRule, enabled)`
- `ReminderLog(reminderId, date, done)`
- `Note(id, title, body, tag, updatedAt)`
- `Book(id, title, author, totalPages, currentPage)`
- `Word(id, bookId, word, translationBs, definitionEn, example, page, createdAt, reviewCount)`
- `Category(id, kind[spent|earned], name)`
- `Transaction(id, kind[spent|earned|saved], amount, categoryId, goalId, date, place)`
- `Goal(id, name, price, saved, deadline, params)`
- `PriceEntry(goalId, date, price, source[gemini|manual])`
- `Hadith(id, arabic, translationBs, collection, number, grade, narrator)` (kurirana lista, uvezena iz provjerene zbirke); `HadithShown(date, hadithId)`; `Note.originalBody` za vraćanje poslije Gemini sređivanja
- `Profile(name, nick, avatarUri, city = "Visoko")` (postavke u DataStore)

## 6. Pravila izračuna

- Potrebno dnevno = (cijena do roka − ušteđeno) / broj dana do roka. Sedmično = dnevno × 7, mjesečno = dnevno × 365 / 12.
- Cijena do roka = današnja cijena × (1 − stopa)^godine, uz stopu iz izabranog scenarija.
- Trajanje štednje po fiksnom iznosu = (cijena − ušteđeno) / dnevni iznos, prikazano u danima i u godinama/mjesecima.
- Niz = broj uzastopnih dana u kojima su klanjani svi vakti.
- "Promaklo" u izvještaju: vakti bez oznake, ponavljajući podsjetnici bez "gotovo", dani bez čitanja, štednja ispod plana.

## 7. Vanjske usluge

- **Vaktija.ba**: povući vremena za mjesec unaprijed i sačuvati lokalno, tako da radi offline. Grad nije fiksan: korisnik ga mijenja u Profilu (zadano Visoko), a pri promjeni se povlače vremena za novi grad. Provjeriti trenutnu dokumentaciju API-ja i koje gradove podržava.
- **Gemini API**: samo na korisnikov dodir i samo online. Namjene (svaka se može isključiti u Profilu, "Gemini: gdje se koristi"): (1) Knjige: prijevod, definicija i primjer za riječ; (2) Štednja: procjena cijene iz oglasa i parametara; (3) Novčanik: kratak uvid u potrošnju; (4) Bilješke: sređivanje, sažetak, lista zadataka, ispravka, prijevod; (5) Namaz: kratko objašnjenje hadisa dana. Rezultati se čuvaju lokalno. Ključ se čuva šifrirano (EncryptedSharedPreferences).
- **Hadis dana**: Gemini NE piše i ne bira tekst hadisa (može halucinirati). Tekst, prijevod, izvor i stepen dolaze iz provjerene zbirke (npr. Buhari, Muslim; uvesti iz pouzdanog izvora i ručno provjeriti prije ugradnje). Primjer u dizajnu (Buhari 1 / Muslim 1907) je samo ilustracija. Gemini daje samo kratko pomoćno značenje s napomenom da za dublje tumačenje treba pitati učenjaka.
- **Google Drive**: izvoz i uvoz JSON backupa.

## 8. Redoslijed gradnje

1. Namaz (API, stalna notifikacija, praćenje, mjesečni pregled)
2. Podsjetnici i bilješke (+ backup)
3. Novčanik i štednja
4. Knjige i rječnik (Gemini)
5. Izvještaji (računaju se iz podataka ostalih modula)

## 9. Poznata ograničenja

- Stalna notifikacija se može ukloniti prevlačenjem, a neki proizvođači (Xiaomi, Samsung) gase pozadinske procese. Zato se osvježava zakazanim alarmima, a ne stalno pokrenutim servisom. Treba tražiti dozvolu za tačne alarme.
- Procjena cijene automobila je raspon, ne obećanje: pouzdana je samo cijena osvježavana iz stvarnih oglasa.
