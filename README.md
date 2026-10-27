# Wortschatz

Osobna Android aplikacija za razgovorni njemački: 1000 najčešćih riječi, svaka s rodom,
množinom ili glagolskim oblicima, hrvatskim prijevodom i rečenicom kakvu bi Nijemac stvarno rekao.

## Kako radi

1. **Test (znam / ne znam)** – prođeš listu redom od najčešćih riječi. Povuci karticu udesno
   (znam) ili ulijevo (ne znam), dodirni je za prijevod. Ono što znaš se preskače, ostalo ide u učenje.
2. **Danas** – dnevno ponavljanje s razmakom (spaced repetition): riječi koje dospijevaju danas
   plus nekoliko novih (zadano 10 dnevno). Ocjene *Opet / Teško / Dobro / Lako* određuju kad
   se riječ vraća. Riječ s razmakom od 3+ tjedna smatra se naučenom.
3. **Rječnik** – sve riječi s pretragom (njemački ili hrvatski) i filtrima po statusu.

Članovi su obojani (der plavo, die crveno, das zeleno), a izgovor ide preko njemačkog glasa
na mobitelu (Google govorne usluge → Deutsch). Smjer kartica može biti DE → HR, HR → DE
(bolje za govor) ili mješovito. Ima i dnevni podsjetnik.

## Kako dobiti APK

Nakon svakog pusha na `main` workflow **Build APK** složi aplikaciju (5–10 min).
APK preuzmi na mobitel iz **Releases** (`Wortschatz.apk`) i instaliraj.
Svi buildovi su potpisani istim ključem (`keystore/`), pa se nova verzija instalira preko
stare i napredak ostaje.

## Gdje je što

| Datoteka | Sadržaj |
|---|---|
| `assets/words.json` | lista riječi (r, de, a, pl, pos, f, hr, ex, exHr) |
| `Model.kt` | riječi, kartice, statusi, postavke |
| `Data.kt` | učitavanje liste, spremanje napretka (SharedPreferences, JSON) |
| `Srs.kt` | raspored ponavljanja, dnevni red kartica, niz dana |
| `AppViewModel.kt` | stanje aplikacije i sve radnje |
| `Speech.kt` | njemački izgovor (TTS) |
| `Reminders.kt` | dnevni podsjetnik |
| `ui/` | ekrani: Danas, Test, ponavljanje, Rječnik, Postavke |
