# Chiave di firma di ViViApp

Tutte le versioni di ViViApp vengono firmate con questa chiave, così Android accetta gli aggiornamenti.
Non cancellarla e non cambiarla: con una chiave diversa il tablet rifiuterebbe l'aggiornamento
e bisognerebbe disinstallare l'app (perdendo brani e impostazioni).

Se in futuro la repository torna privata o la sposti nei segreti di GitHub
(`SIGNING_KEYSTORE_B64` = `base64 viviapp.jks`, `SIGNING_PASSWORD` = contenuto di password.txt),
la compilazione usa quelli e questa cartella si può togliere.
