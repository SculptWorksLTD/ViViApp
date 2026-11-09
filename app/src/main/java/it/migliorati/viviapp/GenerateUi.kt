package it.migliorati.viviapp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Stile suggerito per una branca dello yoga: nome, a cosa serve, prompt per ACE-Step, durata. */
class YogaStyle(val name: UiText, val desc: UiText, val prompt: String, val seconds: Int)

private val yogaStyles = listOf(
    YogaStyle({ "Hatha" },
        { tr("Slow, steady rhythm to hold the poses calmly.", "Spor, ujednačen ritam za mirno držanje položaja.", "Ritmo lento e regolare, per tenere le posizioni con calma.") },
        "ambient, soft piano, warm pads, gentle hand drum, calm, steady, 70 BPM, instrumental", 240),
    YogaStyle({ "Vinyasa Flow" },
        { tr("Smooth and flowing, follows the breath from pose to pose.", "Tečno i glatko, prati dah iz položaja u položaj.", "Scorrevole e fluido, accompagna il passaggio tra le posizioni sul respiro.") },
        "downtempo, organic chillout, soft hand percussion, warm bass, flowing, uplifting, 90 BPM, instrumental", 240),
    YogaStyle({ "Ashtanga" },
        { tr("Constant and hypnotic, supports the sequence without distracting.", "Stalno i hipnotično, podržava niz bez ometanja.", "Costante e ipnotico, sostiene la sequenza senza distrarre.") },
        "minimal, hypnotic, tanpura drone, steady soft percussion, focused, repetitive, 80 BPM, instrumental", 240),
    YogaStyle({ "Power Yoga" },
        { tr("More energetic, for dynamic and strength classes.", "Energičnije, za dinamične satove i satove snage.", "Più energico, per le lezioni dinamiche e di forza.") },
        "energetic downtempo, deep groove, tribal percussion, motivating, driving, 100 BPM, instrumental", 240),
    YogaStyle({ "Yin" },
        { tr("Very slow and deep, for long-held poses.", "Vrlo sporo i duboko, za dugo držane položaje.", "Lentissimo e profondo, per le posizioni tenute a lungo.") },
        "deep ambient, slow evolving drones, soft piano notes, spacious, very slow, 50 BPM, instrumental", 240),
    YogaStyle({ "Restorative" },
        { tr("Soft and enveloping, for recovery and relaxation.", "Meko i obavijajuće, za oporavak i opuštanje.", "Morbido e avvolgente, per il recupero e il rilassamento.") },
        "soft ambient, warm strings, gentle piano, soothing, peaceful, slow, 55 BPM, instrumental", 240),
    YogaStyle({ "Yoga Nidra" },
        { tr("Almost rhythmless, for guided relaxation and conscious sleep.", "Gotovo bez ritma, za vođeno opuštanje i svjesni san.", "Quasi senza ritmo, per il rilassamento guidato e il sonno cosciente.") },
        "dreamy ambient, deep drone, soft textures, no percussion, sleepy, floating, 45 BPM, instrumental", 240),
    YogaStyle({ "Kundalini" },
        { tr("Mantra-like and repetitive, for kriyas, breathing and active meditation.", "Poput mantre i ponavljajuće, za krije, disanje i aktivnu meditaciju.", "Mantrico e ripetitivo, per kriya, respiri e meditazioni attive.") },
        "kundalini, gong, tanpura drone, harmonium, mantra-like, hypnotic, rhythmic, 75 BPM, instrumental", 240),
    YogaStyle({ "Iyengar" },
        { tr("Discreet and neutral, to focus on alignment.", "Diskretno i neutralno, za fokus na poravnanje.", "Discreto e neutro, per lavorare sull'allineamento con concentrazione.") },
        "minimal ambient, soft piano, subtle pads, neutral, focused, unobtrusive, 65 BPM, instrumental", 240),
    YogaStyle({ "Sivananda" },
        { tr("Traditional Indian, serene, for the classic sequence.", "Tradicionalno indijsko, spokojno, za klasični niz.", "Tradizionale indiano, sereno, per la sequenza classica.") },
        "indian classical, sitar, bansuri flute, tanpura drone, serene, devotional, slow raga, instrumental", 240),
    YogaStyle({ "Jivamukti" },
        { tr("Spiritual but lively, between devotion and movement.", "Duhovno ali živahno, između predanosti i pokreta.", "Spirituale ma vivace, tra devozione e movimento.") },
        "devotional, harmonium, tabla, uplifting, spiritual, groove, 85 BPM, instrumental", 240),
    YogaStyle({ "Anusara" },
        { tr("Bright and open, for a joyful practice.", "Svijetlo i otvoreno, za radosnu praksu.", "Luminoso e aperto, per una pratica gioiosa.") },
        "bright ambient, acoustic guitar, soft piano, warm, joyful, heart opening, 80 BPM, instrumental", 240),
    YogaStyle({ "Hot Yoga" },
        { tr("Steady and motivating, for long sequences in a heated room.", "Ujednačeno i motivirajuće, za duge nizove u zagrijanoj dvorani.", "Costante e motivante, per le sequenze lunghe in sala calda.") },
        "deep house chill, steady groove, warm pads, motivating, hypnotic, 95 BPM, instrumental", 240),
    YogaStyle({ "Pranayama" },
        { tr("Suspended and regular, leaves room for the breath.", "Lebdeće i pravilno, ostavlja prostor dahu.", "Sospeso e regolare, lascia spazio al respiro.") },
        "breathing meditation, soft drone, gentle pulse, airy pads, slow, calm, 60 BPM, instrumental", 180),
    YogaStyle({ tr("Meditation", "Meditacija", "Meditazione") },
        { tr("Still and deep: bowls and drones.", "Mirno i duboko: zdjele i bordunski tonovi.", "Immobile e profondo, campane e bordoni.") },
        "tibetan singing bowls, crystal bowls, deep drone, meditation, timeless, peaceful, instrumental", 240),
    YogaStyle({ tr("Chakra / Sound healing", "Čakre / zvučno iscjeljivanje", "Chakra / Sound healing") },
        { tr("A sound bath of bowls and warm frequencies.", "Zvučna kupka zdjela i toplih frekvencija.", "Bagno sonoro di campane e frequenze calde.") },
        "sound bath, crystal singing bowls, gong, overtone drones, healing, resonant, instrumental", 240),
    YogaStyle({ tr("Prenatal yoga", "Joga za trudnice", "Yoga in gravidanza") },
        { tr("Gentle and reassuring, for mothers-to-be.", "Nježno i umirujuće, za buduće majke.", "Dolce e rassicurante, per le mamme in attesa.") },
        "gentle lullaby, soft piano, warm strings, tender, calm, nurturing, slow, instrumental", 240),
    YogaStyle({ tr("Kids yoga", "Joga za djecu", "Yoga per bambini") },
        { tr("Cheerful and playful, with nature sounds.", "Veselo i razigrano, sa zvukovima prirode.", "Allegro e giocoso, con suoni della natura.") },
        "playful, light marimba, ukulele, birds, nature, happy, gentle, 95 BPM, instrumental", 180),
    YogaStyle({ tr("Sun salutation", "Pozdrav suncu", "Saluto al sole") },
        { tr("Slowly rising, for opening morning sequences.", "Polako raste, za jutarnje uvodne nizove.", "Che sale piano, per le sequenze di apertura del mattino.") },
        "sunrise, uplifting ambient, soft percussion building, bright, hopeful, 85 BPM, instrumental", 180),
    YogaStyle({ "Savasana" },
        { tr("For the final relaxation, fading into silence.", "Za završno opuštanje, nestaje u tišini.", "Per il rilassamento finale, che sfuma nel silenzio.") },
        "deep ambient, slow drone, soft piano, sleepy, very relaxing, fading, 50 BPM, instrumental", 240),
)

@Composable
fun GenerateTab() {
    val st by Mixer.state.collectAsState()
    val s by Mixer.settings.collectAsState()
    var prompt by rememberSaveable { mutableStateOf(yogaStyles[0].prompt) }
    var seconds by rememberSaveable { mutableFloatStateOf(yogaStyles[0].seconds.toFloat()) }
    val selected = yogaStyles.firstOrNull { it.prompt == prompt }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(tr("Hugging Face account", "Hugging Face račun", "Account Hugging Face"), style = MaterialTheme.typography.titleMedium)
                Hint(tr(
                    "Music is generated online, for free, with the ACE-Step model. It needs a token from your huggingface.co account (Settings → Access Tokens → type Read).",
                    "Glazba se generira online, besplatno, modelom ACE-Step. Potreban je token tvog huggingface.co računa (Settings → Access Tokens → vrsta Read).",
                    "La musica viene generata online, gratis, con il modello ACE-Step. Serve un token del tuo account huggingface.co (Settings → Access Tokens → tipo Read).",
                ))
                OutlinedTextField(
                    value = s.serverToken, onValueChange = { v -> Mixer.update { it.copy(serverToken = v.trim()) } },
                    label = { Text("Token (hf_…)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { Mixer.testServer() }) { Text(tr("Test connection", "Provjeri vezu", "Prova connessione")) }
            }
        }
        Spacer(Modifier.height(16.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(tr("New instrumental track", "Nova instrumentalna pjesma", "Nuovo brano strumentale"), style = MaterialTheme.typography.titleMedium)
                Hint(tr(
                    "Describe the style with English keywords separated by commas. Pick a style and edit it if you like.",
                    "Opiši stil engleskim ključnim riječima odvojenim zarezima. Odaberi stil i po želji ga uredi.",
                    "Descrivi lo stile con parole chiave in inglese separate da virgole. Scegli uno stile e modificalo se vuoi.",
                ))
                Spacer(Modifier.height(8.dp))
                Text(tr("Styles for the branches of yoga", "Stilovi za grane joge", "Stili per le branche dello yoga"), style = MaterialTheme.typography.labelLarge)
                // Due file di stili che scorrono in orizzontale
                yogaStyles.chunked((yogaStyles.size + 1) / 2).forEach { row ->
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { y ->
                            FilterChip(
                                selected = prompt == y.prompt,
                                onClick = { prompt = y.prompt; seconds = y.seconds.toFloat() },
                                label = { Text(y.name()) },
                            )
                        }
                    }
                }
                Hint(
                    selected?.let { it.name() + ": " + it.desc() } ?: tr("Custom style.", "Prilagođeni stil.", "Stile personalizzato."),
                    Modifier.padding(vertical = 6.dp),
                )
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text(tr("Music style", "Stil glazbe", "Stile della musica")) }, minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                val mins = seconds.roundToInt() / 60
                val secs = seconds.roundToInt() % 60
                LabeledSlider(tr("Duration", "Trajanje", "Durata"), seconds, 30f..240f, "%d:%02d".format(mins, secs)) { seconds = (it / 10).roundToInt() * 10f }
                Hint(tr(
                    "The free quota is a few tracks a day. Downloaded tracks stay on the tablet forever.",
                    "Besplatna kvota je nekoliko pjesama dnevno. Preuzete pjesme zauvijek ostaju na tabletu.",
                    "La quota gratuita è di qualche brano al giorno. I brani scaricati restano sul tablet per sempre.",
                ))
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { Mixer.generate(prompt.trim(), seconds.roundToInt()) },
                    enabled = !st.generating && prompt.isNotBlank(),
                ) { Text(if (st.generating) tr("Generating…", "Generiranje…", "Generazione…") else "✦  " + tr("Generate track", "Generiraj pjesmu", "Genera brano")) }
                if (st.generating) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        st.genStatus?.let { g ->
            Spacer(Modifier.height(12.dp))
            Text(g())
        }
        Spacer(Modifier.height(16.dp))
        Text(tr("Advanced: generator address", "Napredno: adresa generatora", "Avanzate: indirizzo del generatore"), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = s.serverUrl, onValueChange = { v -> Mixer.update { it.copy(serverUrl = v.trim()) } },
            placeholder = { Text(Mixer.DEFAULT_SPACE) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(tr("Leave empty to use the default one.", "Ostavi prazno za zadani.", "Lascia vuoto per usare quello predefinito."))
    }
}
