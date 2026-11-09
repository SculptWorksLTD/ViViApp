# ViViApp

**Manuale / Manual / Priručnik:** [Italiano](docs/MANUALE_IT.md) · [English](docs/MANUAL_EN.md) · [Hrvatski](docs/PRIRUCNIK_HR.md)

App Android per lezioni di yoga in cuffia: la voce dell'insegnante (microfono Bluetooth) e la musica vengono mixate sul tablet e mandate dal cavo USB-C al trasmettitore delle cuffie.

```
Headset Bluetooth ──voce──▶  TABLET (ViViApp)  ──USB-C → jack──▶ trasmettitore ──▶ cuffie clienti
                              voce + musica
```

## Cosa fa

- **Voce a bassa latenza**: microfono Bluetooth in ingresso, uscita forzata sul cavo. Il buffer parte dal minimo e cresce da solo solo se servisse.
- **Si accende solo quando serve**: lavora solo con la lezione avviata e il cavo collegato. Se il cavo si stacca, voce e musica vanno in pausa all'istante, così niente esce dagli altoparlanti del tablet. Quando lo ricolleghi riparte (ripresa automatica, disattivabile).
- **Mixer a due canali**: volume voce, muto, noise gate (on/off), VU meter; volume musica, play/pausa/stop/avanti, loop, brano successivo automatico.
- **Ducking automatico (on/off)**: quando lei parla la musica si abbassa. Puoi regolare di quanto, la sensibilità e quanto ci mette a tornare su.
- **Fade in/out (on/off)** con durate regolabili.
- **Libreria offline**: importi i tuoi mp3/wav/m4a dal tablet, oppure ci finiscono le tracce generate con l'AI.
- **Generatore AI online**: scegli uno stile (o scrivi il tuo) e la durata (fino a 4 minuti). Il brano strumentale viene generato online, gratis, e scaricato nella Libreria: da quel momento funziona anche senza internet.
- **Lavora a schermo spento**: notifica fissa e schermo tenuto acceso durante la lezione.

## 1. Ottenere l'APK

**Opzione A, Android Studio (consigliata):**
1. Installa Android Studio e apri la cartella `ViViApp`.
2. Aspetta che finisca la sincronizzazione Gradle (la prima volta scarica parecchio).
3. Collega il tablet con "Debug USB" attivo e premi ▶ Run, oppure usa *Build → Build APK(s)*.

**Opzione B, GitHub (senza installare niente):**
1. Carica la cartella in un repository GitHub.
2. Nella scheda *Actions* parte da solo "Compila APK".
3. Alla fine scarichi `ViViApp-apk` dalla pagina del job, lo copi sul tablet e lo installi (consenti "origini sconosciute").

## 2. Preparare il tablet (Honor)

- Al primo avvio concedi **Microfono**, **Dispositivi nelle vicinanze** (Bluetooth) e **Notifiche**.
- *Impostazioni → Batteria → Avvio app → ViViApp → gestione manuale*, con tutte le voci attive. Senza questo, MagicOS potrebbe chiudere l'app durante la lezione.

## 3. Alla lezione

1. Accoppia l'headset al tablet (normale accoppiamento Bluetooth).
2. Collega l'adattatore USB-C → jack al trasmettitore.
3. Apri ViViApp → **Avvia lezione**. In alto vedi quale microfono e quale uscita sta usando.
4. Se ci sono più microfoni, scegli quello giusto con i pulsanti nella sezione Voce.
5. Musica: dalla **Libreria** premi "Suona", poi gestiscila dal Mixer.

**Adattatore USB-C → jack:** serve uno di quelli "attivi" (con chip DAC dentro, tipo quelli Apple o Samsung). L'app lo vede come "USB-C". Se il tablet lo riconosce come jack funziona lo stesso.

## 4. Generare musica con l'AI (online, gratis)

L'app usa **ACE-Step**, un modello open source che genera brani interi. Gira gratis sui server di Hugging Face: niente da installare né da tenere acceso.

1. Crea un account gratuito su **huggingface.co**.
2. Vai in *Settings → Access Tokens → Create new token*, scegli il tipo **Read** e copia il token (inizia con `hf_`).
3. Nell'app, scheda **Genera**: incolla il token e premi **Prova connessione**.
4. Scegli uno stile, la durata e premi **Genera brano**. Di solito ci vogliono uno o due minuti, poi il brano compare in Libreria.

**Limite:** l'account gratuito ha una piccola quota giornaliera di GPU, sufficiente per qualche brano al giorno. Se finisce, l'app lo dice: si riprova il giorno dopo. Con l'abbonamento PRO di Hugging Face (9 $ al mese) la quota è 8 volte più grande.

Il generatore è un servizio pubblico di Hugging Face, non nostro: se un giorno cambia o si spegne, i brani già scaricati restano e basta aggiornare l'app.

## Limiti da conoscere

- **Il Bluetooth aggiunge 100–200 ms di ritardo** sulla voce e porta la qualità a livello telefonico. Il software non può eliminarlo. Per le clienti va bene, perché sentono solo la voce in cuffia. Se un giorno volete fare il salto di qualità, basta un microfono wireless con ricevitore USB-C: l'app lo riconosce da sola, senza modifiche.
- Mentre il microfono Bluetooth è attivo, nell'headset di lei la musica non si sente (il Bluetooth in modalità chiamata funziona così). Se a lei serve sentire il mix, può usare una cuffia del sistema delle clienti.
- Se con il microfono Bluetooth la musica non esce dal cavo, spegni *Impostazioni → Modalità chiamata*.

## Struttura

```
app/        app Android (Kotlin + Jetpack Compose)
  VoiceEngine.kt   percorso voce a bassa latenza
  MusicPlayer.kt   canale musica, fade, ducking
  Mixer.kt         dispositivi, sessione, libreria, generatore online
  MainActivity.kt  interfaccia
```
