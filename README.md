# Wortschatz

Osobna Android aplikacija za razgovorni njemački: 1000 najčešćih riječi, svaka s rodom,
množinom ili glagolskim oblicima, hrvatskim prijevodom i rečenicom kakvu bi Nijemac stvarno rekao.

## Kako radi

1. **Test (znam / ne znam)** – prođeš listu redom od najčešćih riječi. Povuci karticu udesno
   (znam) ili ulijevo (ne znam), dodirni je za prijevod. Ono što znaš se preskače, ostalo ide u učenje.
2. **Danas** – dnevno ponavljanje s razmakom: riječi koje dospijevaju danas plus nekoliko
   novih (zadano 10 dnevno). Raspored računa **FSRS** (model pamćenja: stabilnost i težina
   svake riječi), uz ciljano pamćenje 90 %. Tri vrste kartica:
   - **prepoznaj** – njemačka riječ → značenje,
   - **reci na njemačkom** – hrvatska riječ → izgovoriš njemačku (s članom), mikrofon provjeri,
   - **dopuni rečenicu** – primjer s prazninom → izgovoriš riječ koja fali.
   U mješovitom načinu nove riječi prvo prepoznaješ, a zatim ih sve češće izgovaraš.
3. **Shadowing** – slušaš rečenicu i odmah je ponoviš naglas; vidiš koje su riječi prošle.
4. **Rječnik** – sve riječi s pretragom (njemački ili hrvatski) i filtrima po statusu.

Članovi su obojani (der plavo, die crveno, das zeleno), a izgovor ide preko njemačkog glasa
na mobitelu (Google govorne usluge → Deutsch); prepoznavanje govora koristi Googleov
prepoznavač (treba dopuštenje za mikrofon). Ima i dnevni podsjetnik.

## Kako dobiti APK

Nakon svakog pusha na `main` workflow **Build APK** složi aplikaciju (5–10 min).
APK preuzmi na mobitel iz **Releases** (`Wortschatz.apk`) i instaliraj.
Svi buildovi su potpisani istim ključem (`keystore/`), pa se nova verzija instalira preko
stare i napredak ostaje.

## Gdje je što

| Datoteka | Sadržaj |
|---|---|
| `assets/words.json` | lista riječi (r, de, a, pl, pos, f, hr, ex, exHr, cl = praznine) |
| `Model.kt` | riječi, kartice, statusi, postavke |
| `Data.kt` | učitavanje liste, spremanje napretka (SharedPreferences, JSON) |
| `Srs.kt` | FSRS raspored, dnevni red i vrste kartica, niz dana |
| `Listener.kt` | prepoznavanje govora i provjera odgovora |
| `AppViewModel.kt` | stanje aplikacije i sve radnje |
| `Speech.kt` | njemački izgovor (TTS) |
| `Reminders.kt` | dnevni podsjetnik |
| `ui/` | ekrani: Danas, Test, ponavljanje, shadowing, Rječnik, Postavke |
