HardBass EQ (equalizer, PR #61)
Vor dem Merge
15. Kannst du auf einem Gerät testen, dass die App startet, wenn Android Auto oder der Hintergrund-Worker sie weckt?

--- ich werde es testen, sobald das durch ist

16. Ist es für dich in Ordnung, dass nach einer Wiederherstellung auf einem neuen Gerät die SoundCloud- und Spotify-Anmeldung neu nötig ist (Client-ID bei Spotify neu eingeben)?
Entscheidungen

--- das wäre besonders für soundcloud nervig, nur mit workaround. lieber wäre wenn das nicht wäre


17. Soll der inoffizielle SoundCloud-Zugang bleiben (WebView-Login mit gefälschtem Browser, client_id-Scraping, manuelles Cookie)? Die Alternativen sind entfernen, nur Links und Spotify, oder offiziellen API-Zugang beantragen.

--- ich höre ab und zu gern über soundcloud und bisher gabs keinen anderen weg als den player zu nutzen und den hatte ich ohne mein soundcloud go. vielleicht brauche ich go auch gar nicht mehr


18. Wie wird die App verteilt: privat über GitHub-Releases, oder ist der Play Store geplant? Davon hängen REQUEST_INSTALL_PACKAGES und der SoundCloud-Weg ab.

--- aktuell privat über git releases. weiteres ungeplant

19. Sollen die Tokens zusätzlich im Android Keystore verschlüsselt werden? Das brauchen wir auf einem echten Gerät zum Testen.

--- ist nicht nötig, denke ich

20. Darf R8/Minify für Release aktiviert werden? Das braucht Keep-Regeln für Hilt, Room und kotlinx.serialization und einen Gerätetest.

--- macht das einen klanglichen unterschied? wenn ja, bin ich offen

21. Soll der Foreground-Service dauerhaft laufen und die Benachrichtigung immer zeigen, oder nur bei aktiver Session?

--- zieht viel zu viel akku. wenn keine session erkannt wurde über zeit, soll der EQ sich einfach schließen

22. Darf Room von 2.7.0-alpha13 auf eine stabile Version wechseln, und dürfen die alten Versionen in :player (media3, okhttp, coroutines) angehoben werden? Das braucht einen Audio-Test auf dem Gerät.

--- kann das die soundqualität nehmen? sonst mach mal und wir testen


23. Soll die YouTube-Quelle weiterverfolgt oder entfernt werden? Laut Doku funktioniert sie nicht.

--- youtube music klappt eh, raus damit

24. Soll der Spike-Code komplett gelöscht werden statt nur im Release versteckt? Sollen TransientShaper und FakeAudioEngine raus oder nach src/test?

--- wenn der keinen nutzen hat, raus

25. Gehört das Desktop-Modul (EasyEffects/Equalizer-APO-Export) in dieses Repo, und wird es gepflegt?

--- der traum war den EQ auch auf meinem rechner so wie er für android ist nutzen zu können, klappt aber nicht. wurde nichtmehr gepflegt

26. Welche der drei Doku-Kopien (Root, docs/, roadmaps and documentations/) ist maßgeblich, und soll ein Root-README angelegt werden?

--- ja, leg ein root readme an und lösch die doppelten mit selbem context

27. Brauchst du die Liste aller Launcher-Apps (<queries>) mit App-Namen wirklich? Sie kann beim Play Store Probleme machen.

--- nur so kann ich die richtigen prozesse für tonspuen abfangen. sonst schlag mir was vor

28. Hast du den Limiter und das Anhängen an Fremd-Sessions auf echten Geräten geprüft, und auf welchen?

--- anhängen an fremd sessions? limiter teste ich quasi jedes mal im auto