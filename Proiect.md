---
tags: [facultate, mobile-computing, proiect, android, stt, opencode]
deadline_M1: 2026-11-01
deadline_M2: 2026-12-06
deadline_M3: 2027-01-11
---

# MC — Proiect (Mobile Computing, MSc PDCS, 2026–27)

Notă de context pentru agentul OpenCode local și pentru mine. Conține cerințele reale din
handout, deciziile luate până acum, starea repo-ului și ce urmează. Fără secrete aici.

## 0. Prompt de dat agentului local (copy-paste)

```
Read /vault/Stuff/Facultate/Master/MC/Proiect.md (or this file wherever it is synced) fully
before doing anything. It contains the fixed course requirements, the app concept and the
placement decision, the current repo state, and the known gaps.

Then, in the repo working copy:
1. Write app-idea.md answering every field, especially the mobile-computing framing.
2. Rebuild the prototype around on-device STT (record -> transcript list -> detail) using
   Navigation Compose with type-safe routes; no XML layouts, no Java, Kotlin + Compose +
   Material 3 only.
3. Put transcription behind a TranscriptionEngine interface with Local (real engine) and a
   fake for tests; no android.* imports in viewmodel/data layers.
4. Append a new dated section to AI_USE_LOG.md (append-only; do not rewrite existing sections).
5. Rewrite the reflection as a gap->lab map (L4..L10).
Do not add Room, Retrofit, WorkManager, Hilt or Coil yet: those are M2/M3. Do not commit a
model binary. Report a file tree and what you could not verify without a device.
```

## 1. Pe scurt

- **Ce construiesc:** alternativă WhisperFlow — STT on-device pentru Android. Pe macOS, partea
  QVAC/TS rămâne side project separat.
- **Stack impus de curs:** Kotlin + Compose + Material 3, MVVM + UDF, Hilt, coroutines/Flow,
  Navigation Compose (type-safe), Room + Retrofit + WorkManager, Coil 3. Fără Java, fără XML layouts.
- **Repo acum:** `github.com/Ak0p/mobile-computing-m1` (privat, trebuie public în org-ul cursului).
  Prototipul din el e TrekRo, nu aplicația STT — se rescrie.
- **M1 e duminică 1 nov 2026.** Bloc de lucru în calendar: sâmbătă 31 oct, 10:00–14:00.

## 2. Cerințe fixe (din MC Project Handout, nu din cardul Deck)

Cardul din Deck e o variantă sărăcită; handout-ul câștigă. Handout = `MC_Project_Handout.pdf`,
disponibil în Nextcloud (`/Deck/MC Project Handout.pdf`) și în atașamentele sesiunii Hermes.

| Milestone | Conținut | Deadline |
|---|---|---|
| M1 | Prototip Lab 3 + AI_USE_LOG.md + reflecție | 1 nov 2026 |
| M2 | Data layer: arhitectură, networking, Room, sync offline-first (Labs 4–7) | 6 dec 2026 |
| M3 | DI, teste/CI, concept integrat + decizia de plasare (Labs 8–10) | 11 ian 2027 |

- **Defense:** 14 sau 21 ianuarie, 10 minute live, toată echipa; slot opțional early 17 dec dacă M3
  e predat până pe 14 dec.
- **Pondere:** defense live 40% (explică + modifică cod scris de tine), owned areas, peer-eval,
  restul pe echipă (funcționalitate, arhitectură, teste/CI, SYSTEMS.md).
- **Repo:** unul public, în organizația cursului, merge commits (nu squash), fiecare PR aprobat de
  un coleg (nu de tine), CI verde ca gate. Commit-uri sub numele fiecăruia.
- **Ownership:** M1 colectiv; M2 — architecture / networking / persistence owner; M3 — DI-testing
  owner + integrated-concept owner. Fiecare membru deține minim o arie și răspunde la defense.
- **Zero checkpoint între M1 și M2** (5 săptămâni). Burst de commit-uri în decembrie = flag.

### Floor tehnic obligatoriu (orice lipsă = scor tăiat)
- Room ca sursă de adevăr locală; UI-ul observă store-ul local, nu rețeaua.
- Tier remote ales conștient + WorkManager sync offline-first sub o constrângere reală (rețea/baterie).
- O permisiune runtime tratată: rationale, denial graceful (degrade, nu crash), cale de re-request.
  Recomandat `POST_NOTIFICATIONS`, postată din worker la sync.
- I/O pe coroutines/Flow, WorkManager pentru muncă amânată, supraviețuire la Doze.
- Teste JVM peste logică (cu fake-uri) + minim un test instrumentat + CI obligatoriu pe PR.
- Un concept integrat cu decizie reală de plasare (vezi §3).

## 3. Decizia de plasare (miezul notelor)

**Teza:** STT rulează on-device cu un model mic (Whisper tiny/base sau Parakeet prin sherpa-onnx);
varianta cloud încarcă un model mare + post-procesare LLM (formatare, comenzi, trigger de aplicații).
Ambele în spatele aceleiași interfețe (`TranscriptionEngine`), comutabile.

Ce se apără la defense:
- **Privacy:** audio nu pleacă de pe device în modul local.
- **Offline:** dictare funcțională fără semnal; coada de upload așteaptă rețea + charging.
- **Latency:** model mic = latență mică, dar acuratețe mai slabă pe accente/zgomot.
- **Energy/bandwidth:** inferență locală consumă baterie; cloud consumă trafic.
- **Ce măsor:** timp per minut de audio on-device vs round-trip cloud, WER pe un set propriu de
  clipuri, baterie/energie. Cifrele intră în SYSTEMS.md la M3.

**QVAC (Tether) — unde intră:** framework TS, pe Android merge doar prin Expo/React Native, deci
**nu** poate fi engine-ul aplicației de curs (stack-ul e fixat pe Kotlin/Compose). Locurile corecte:
- jumătatea macOS a side project-ului (macOS 14+, arm64, Metal);
- tier-ul remote al aplicației Android (server care rulează model mare) — permis, dacă stă în spatele
  unei interfețe de repository.
Cerințe QVAC: macOS 14+ arm64/Metal; Android 12+ arm64 cu Vulkan/OpenCL, dar doar cu Expo.

## 4. Engine STT pe Android — ce e realist

- **sherpa-onnx (recomandat):** AAR cu API Kotlin (`com.xdcobra.sherpa:sherpa-onnx`), streaming
  `OnlineRecognizer`, VAD, modele Whisper/Parakeet/Canary. Fără JNI scris de mine. Atenție: AAR-ul
  prebuilt leagă static espeak-ng (GPL-3.0) — de menționat sau de construit din sursă.
- **whisper.cpp via JNI:** `examples/whisper.android` există; batch e rapid (~5 s audio în 1–2 s pe
  ARM64 mid-range). Capcană reală: streaming naiv (buffer crescător + `whisper_full` repetat) e de
  ~5× mai lent decât real-time, latența urcă la 30 s și apare ANR. Corect: batch per enunciat cu
  segmentare VAD.
- **Nu folosi:** ML Kit pentru STT (face traducere/language-ID/imagine, nu transcriere generală);
  `SpeechRecognizer` cu `EXTRA_PREFER_OFFLINE` doar ca fallback degradat (opac, nimic de apărat).
- **Livrarea modelului:** nu comite 75–150 MB. Download la prima rulare cu progres, sau doar tiny
  cuantizat bundled. Trade-off-ul se notează în reflecție.

## 5. Scope: ce NU fac în proiectul de curs

- **Fără IME/keyboard** (InputMethodService) — WhisperFlow e desktop-shaped; pe Android ar însemna
  muncă mare ne-notată. Aplicația = recorder + transcriber + listă de transcrieri.
- **Fără Accessibility/triggering apps** (gen Siri) — notat ca future work în app-idea.md, nu construit.
- Fără sync multi-device, fără conturi, fără editare audio.

## 6. Starea repo-ului `mobile-computing-m1`

Locally: `/home/hermeswebui/projects/mobile-computing-m1` (pe Mac: clone din GitHub).

**Gata:**
- Prototip TrekRo: Compose + Material 3, single Activity, 10 trasee in-memory, listă→detaliu,
  căutare tolerantă la diacritice, MVVM + UDF (`TrailUiState` / `sealed TrailIntent` /
  `MutableStateFlow.asStateFlow()`), DI manual (`AppContainer` + factory), 4 teste JVM.
- CI GitHub Actions verde: `testDebugUnitTest` + `assembleDebug` (run 41070138378, apoi 41071144011).
- Tag `m1-submission` → commit `8365e9d`.
- `AI_USE_LOG.md` cu addendum despre verificarea prin CI; `REFLECTION.md`; `PROMPT.txt`.

**Lipsește / e greșit:**
- [ ] Prototipul e TrekRo (catalog static), nu aplicația STT. Se rescrie pe conceptul din §3.
- [ ] Navigația e short-circuit: `selectedTrail` în UiState + `BackHandler`. Handout cere
      **Navigation Compose cu rute type-safe** și supraviețuire la process death.
- [ ] Lipsește `app-idea.md` (template Lab 3, cu secțiunea de mobile-computing framing).
- [ ] Reflecția nu mapează lipsurile pe laboratoare (L4 arhitectură, L5 networking, L6 persistență,
      L7 offline/sync, L8 DI, L9 teste/CI, L10 concept).
- [ ] Repo privat în `Ak0p`, nu public în org-ul cursului.
- [ ] Commit author = `andrei@cloud.akop.ro` (placeholder) — nu se leagă de contul GitHub.

## 7. Ordinea de lucru (M1)

1. Scrie `app-idea.md`: nume, pitch, problema, useri, ecrane, „lucrul" gestionat (o transcriere),
   câmpuri, apoi cele patru răspunsuri de framing (ce persistă local, ce trece prin rețea, ce merge
   fără semnal, întrebarea de plasare).
2. Rebuild prototip pe STT: ecran principal = buton record + listă de transcrieri; detaliu = text,
   durată, limbă, buton upload (stub). Navigare cu Navigation Compose, rute type-safe.
3. Un engine real on-device (sherpa-onnx AAR) cu fallback fake pentru teste; interfața
   `TranscriptionEngine` cu două implementări (Local, RemoteStub).
4. `AI_USE_LOG.md` secțiune nouă pentru sesiunea asta (append-only, nu rescrie ce există).
5. Reflecție rescrisă ca gap→lab map.
6. Repo public în org + PR + tag `m1-submission`.

## 8. Cum îmi dai acces la OpenCode local (Hermes ↔ Mac)

Hermes rulează într-un pod (10.244.0.0/16) care ajunge în LAN (`192.168.1.64:443` răspunde) și în
tailnet (`100.100.100.100` servește UI-ul Tailscale). Nu are client Tailscale, deci nu poate lista
peerii — are nevoie de adresa Mac-ului.

Pe Mac:

```bash
export OPENCODE_SERVER_PASSWORD='<parola lunga, random>'
opencode serve --hostname "$(tailscale ip -4)" --port 4096
# verifică gate-ul de auth:
curl -o /dev/null -w '%{http_code}\n' http://127.0.0.1:4096/app   # 401 fără credențiale
```

- **Nu** `--hostname 0.0.0.0` pe laptop: expune RCE neautentificat pe orice rețea la care e conectat.
- Dacă folosesc IP-ul LAN în loc de tailnet, firewall pe port 4096 către `10.244.0.0/16`.
- Alternativă fără expunere HTTP: SSH + `ssh -L 4096:127.0.0.1:4096`, cheie publică.
- Versiuni: containerul are opencode **1.19.5**; ține serverul pe același minor.
- Parola nu se pune în chat — fișier local sau varianta cu cheie SSH.

Ce fac eu după ce am adresa: probe 401/200, apoi `opencode run --attach <url> 'Respond with
exactly: REMOTE_OPENCODE_OK'`, apoi clonez repo-ul pe Mac și rulez scaffolding-ul acolo.

## 9. Referințe

- Handout: `MC Project Handout.pdf` (Nextcloud `/Deck/`, atașament Hermes).
- Labs: `github.com/radumarin88/2026-mc-labs` — branch per lab: `lab1-starter`/`lab1-solution` …
  `lab4-starter`. Lab 3 = `lab3-ai-prototype` (ghid + `app-idea.md`, `AI-USE-LOG.md`,
  `reflection.md`, fără cod starter).
- Repo proiect: `github.com/Ak0p/mobile-computing-m1`, tag `m1-submission`.
- Skill Hermes relevant: `android-compose-app-projects` (stack, floor, capcane, fapte din handout).
