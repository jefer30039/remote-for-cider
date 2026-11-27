import XCTest

/// Recorridos para los vídeos del simulador en CI (iosApp/ci/demo.sh graba la pantalla mientras corren).
/// No comprueban casi nada: mueven la app como lo haría una persona, con pausas para ver las animaciones.
final class DemoFlows: XCTestCase {
    // MARK: - Grabación (iosApp/ci/demo.sh graba un vídeo por prueba)

    private var flowDir: URL? {
        ProcessInfo.processInfo.environment["DEMO_OUT"].map { URL(fileURLWithPath: $0).appendingPathComponent(".flow") }
    }
    private var recording = false

    /// Pide a demo.sh que reinicie el Cider de demostración y empiece a grabar `<nombre>.mp4`; espera a
    /// que la grabación haya empezado. Sin DEMO_OUT (fuera del CI) no hace nada.
    private func record(_ name: String) {
        guard let dir = flowDir, FileManager.default.fileExists(atPath: dir.path) else { return }
        try? name.write(to: dir.appendingPathComponent("start"), atomically: true, encoding: .utf8)
        let started = dir.appendingPathComponent("recording")
        for _ in 0..<50 where !FileManager.default.fileExists(atPath: started.path) { usleep(200_000) }
        recording = true
    }

    override func tearDown() {
        if recording, let dir = flowDir {
            try? "".write(to: dir.appendingPathComponent("stop"), atomically: true, encoding: .utf8)
            let started = dir.appendingPathComponent("recording")
            for _ in 0..<50 where FileManager.default.fileExists(atPath: started.path) { usleep(200_000) }
            recording = false
        }
        super.tearDown()
    }

    private func launch(_ extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ciderHost", "127.0.0.1", "-ciderPort", "10767", "-ciderToken", "demo",
                               "-AppleLanguages", "(es)", "-AppleLocale", "es_ES"] + extra
        app.launch()
        return app
    }

    // MARK: - Banco de vidrio (para copiar animaciones de iOS 27 en Android)

    private func labButton(_ app: XCUIApplication, _ id: String) -> XCUIElement {
        let b = app.buttons[id]
        _ = b.waitForExistence(timeout: 8)
        return b
    }

    private func edgeSwipeBack(_ app: XCUIApplication, velocity: XCUIGestureVelocity) {
        let w = app.windows.firstMatch
        let start = w.coordinate(withNormalizedOffset: CGVector(dx: 0.005, dy: 0.5))
        start.press(forDuration: 0.05, thenDragTo: w.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)),
                    withVelocity: velocity, thenHoldForDuration: 0.1)
    }

    /// (1a) Empujar una página con compartir + "…" en la barra y volver (botón y gesto desde el borde).
    func testGlassLabPush() {
        record("30-vidrio-empujar-y-volver")
        let app = launch(["-glassLab", "YES"])
        sleep(3)
        for _ in 0..<2 {
            labButton(app, "labPush").tap()
            sleep(2)
            app.navigationBars.buttons.firstMatch.tap()
            sleep(2)
        }
        labButton(app, "labPush").tap()
        sleep(2)
        edgeSwipeBack(app, velocity: .slow)
        sleep(2)
        labButton(app, "labPush").tap()
        sleep(2)
        edgeSwipeBack(app, velocity: .fast)
        sleep(2)
    }

    /// (1b) Añadir y quitar compartir junto al "…" con `withAnimation`: en la barra y en un
    /// `GlassEffectContainer` con `glassEffectID`.
    func testGlassLabToggle() {
        record("31-vidrio-anadir-boton")
        let app = launch(["-glassLab", "YES"])
        sleep(3)
        for _ in 0..<4 {
            labButton(app, "labToggleBar").tap()
            sleep(2)
        }
        for _ in 0..<4 {
            labButton(app, "labToggleContainer").tap()
            sleep(2)
        }
    }

    /// (2) El "…" de la barra que se abre en menú y se cierra, en la raíz y en la página empujada.
    func testGlassLabMenu() {
        record("32-vidrio-menu-de-barra")
        let app = launch(["-glassLab", "YES"])
        sleep(3)
        let outside = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.3, dy: 0.88))
        for _ in 0..<3 {
            labButton(app, "labMenuRoot").tap()
            sleep(2)
            outside.tap()
            sleep(2)
        }
        labButton(app, "labPush").tap()
        sleep(2)
        for _ in 0..<2 {
            labButton(app, "labMenuPushed").tap()
            sleep(2)
            outside.tap()
            sleep(2)
        }
    }

    /// Biblioteca: desplazar hacia abajo (la barra se minimiza con el miniplayer dentro) y volver arriba.
    func testScrollMinimizesTabBar() {
        record("20-recorrido-desplazar-minimiza-barra")
        let app = launch(["-demoTab", "library"])
        sleep(4)
        let scroll = app.scrollViews.firstMatch
        scroll.swipeUp(velocity: .slow)
        sleep(2)
        scroll.swipeUp(velocity: .slow)
        sleep(2)
        scroll.swipeDown(velocity: .slow)
        sleep(2)
    }

    /// Abrir la hoja de reproducción tocando el miniplayer, pausar, reanudar y cerrarla deslizando.
    func testOpenAndClosePlayer() {
        record("21-recorrido-abrir-y-cerrar-hoja")
        let app = launch(["-demoTab", "library", "-demoLogFrames", "YES"])
        let mini = app.otherElements["miniPlayer"]
        XCTAssertTrue(mini.waitForExistence(timeout: 10))
        sleep(3)
        mini.tap()
        sleep(3)
        let play = app.buttons["playPause"]
        if play.waitForExistence(timeout: 3) {
            play.tap()
            sleep(2)
            play.tap()
            sleep(2)
        }
        app.windows.firstMatch.swipeDown(velocity: .fast)
        sleep(3)
        // Otra vez, cerrando despacio: la hoja sigue al dedo.
        mini.tap()
        sleep(3)
        let start = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3))
        start.press(forDuration: 0.1, thenDragTo: start.withOffset(CGVector(dx: 0, dy: 500)), withVelocity: 600, thenHoldForDuration: 0.3)
        sleep(3)
    }

    /// Letra: abrir la hoja, pasar a la letra y dejarla correr (barrido por sílaba, cambio de línea),
    /// arrastrarla, esperar a que vuelva sola, tocar una línea y volver a la carátula.
    func testLyrics() {
        record("23-recorrido-letra")
        let app = launch(["-demoTab", "library"])
        let mini = app.otherElements["miniPlayer"]
        XCTAssertTrue(mini.waitForExistence(timeout: 10))
        sleep(3)
        mini.tap()
        sleep(2)
        let lyrics = app.buttons["lyricsButton"]
        XCTAssertTrue(lyrics.waitForExistence(timeout: 5))
        lyrics.tap()
        sleep(14)   // los controles se repliegan a los 4 s
        save("50-letra")
        let middle = app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55))
        middle.press(forDuration: 0.05, thenDragTo: middle.withOffset(CGVector(dx: 0, dy: -250)))
        sleep(1)
        save("51-letra-arrastrada")
        sleep(5)    // vuelve a seguir la canción
        save("52-letra-vuelve")
        app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.97)).tap()
        sleep(1)
        if lyrics.waitForExistence(timeout: 3) { lyrics.tap() }
        sleep(3)
    }

    /// Cola: abrir la hoja, pasar a la cola, cambiar aleatorio, quitar una canción deslizando,
    /// moverla arrastrando y saltar a otra tocándola.
    func testQueue() {
        record("25-recorrido-cola")
        let app = launch(["-demoTab", "library"])
        let mini = app.otherElements["miniPlayer"]
        XCTAssertTrue(mini.waitForExistence(timeout: 10))
        sleep(3)
        mini.tap()
        sleep(2)
        let queue = app.buttons["queueButton"]
        XCTAssertTrue(queue.waitForExistence(timeout: 5))
        queue.tap()
        sleep(3)
        save("70-cola")
        write("70-cola-arbol.txt", app.debugDescription)
        let shuffle = app.buttons["queueShuffle"]
        if shuffle.waitForExistence(timeout: 3) {
            shuffle.tap()
            sleep(2)
            save("71-cola-aleatorio")
            shuffle.tap()
            sleep(1)
        }
        let rows = app.descendants(matching: .any).matching(identifier: "queueRow")
        if rows.count > 1 {
            rows.element(boundBy: 0).swipeLeft(velocity: .fast)
            sleep(2)
            save("72-cola-quitar")
        }
        if rows.count > 3 {
            let row = rows.element(boundBy: 0)
            let target = rows.element(boundBy: 2)
            row.press(forDuration: 0.8, thenDragTo: target)
            sleep(2)
            save("73-cola-mover")
            rows.element(boundBy: 1).tap()
            sleep(3)
            save("74-cola-saltar")
        }
        queue.tap()
        sleep(3)
    }

    /// Biblioteca: Álbumes → un álbum (zoom), tocar una canción, volver; Playlists → una playlist;
    /// Artistas → un artista; Canciones.
    func testLibraryPages() {
        record("26-recorrido-biblioteca")
        let app = launch(["-demoTab", "library"])
        sleep(4)
        save("80-biblioteca")
        func back() {
            let button = app.navigationBars.buttons.firstMatch
            if button.exists { button.tap() } else { app.swipeRight() }
            sleep(2)
        }
        let albums = app.buttons["library-albums"]
        if albums.waitForExistence(timeout: 5) {
            albums.tap()
            sleep(3)
            save("81-albumes")
            let tile = app.buttons.matching(identifier: "coverTile").firstMatch
            if tile.waitForExistence(timeout: 5) {
                tile.tap()
                sleep(4)
                save("82-album")
                write("82-album-arbol.txt", app.debugDescription)
                app.swipeUp(velocity: .slow)
                sleep(2)
                save("83-album-abajo")
                let track = app.descendants(matching: .any).matching(identifier: "trackRow").element(boundBy: 1)
                if track.exists { track.tap(); sleep(2) }
                back()
            }
            back()
        }
        let playlists = app.buttons["library-playlists"]
        if playlists.waitForExistence(timeout: 5) {
            playlists.tap()
            sleep(3)
            save("84-playlists")
            let row = app.descendants(matching: .any).matching(identifier: "playlistRow").firstMatch
            if row.waitForExistence(timeout: 5) {
                row.tap()
                sleep(4)
                save("85-playlist")
                back()
            }
            back()
        }
        let artists = app.buttons["library-artists"]
        if artists.waitForExistence(timeout: 5) {
            artists.tap()
            sleep(3)
            save("86-artistas")
            let row = app.descendants(matching: .any).matching(identifier: "artistRow").firstMatch
            if row.waitForExistence(timeout: 5) {
                row.tap()
                sleep(5)
                save("87-artista")
                app.swipeUp(velocity: .slow)
                sleep(2)
                save("88-artista-abajo")
                back()
            }
            back()
        }
        let songs = app.buttons["library-songs"]
        if songs.waitForExistence(timeout: 5) {
            songs.tap()
            sleep(3)
            save("89-canciones")
            let row = app.descendants(matching: .any).matching(identifier: "songRow").firstMatch
            if row.waitForExistence(timeout: 3) {
                row.press(forDuration: 1.2)
                sleep(2)
                save("89b-menu-cancion")
                app.windows.firstMatch.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.05)).tap()
                sleep(1)
            }
        }
    }

    /// Buscar: categorías, escribir, resultados y abrir uno.
    func testSearch() {
        record("27-recorrido-buscar")
        let app = launch(["-demoTab", "search"])
        sleep(4)
        save("90-buscar")
        let field = app.searchFields.firstMatch
        if field.waitForExistence(timeout: 5) {
            field.tap()
            sleep(1)
            field.typeText("SZA")
            sleep(4)
            save("91-buscar-resultados")
            write("91-buscar-arbol.txt", app.debugDescription)
            let result = app.descendants(matching: .any).matching(identifier: "searchResult").firstMatch
            if result.exists {
                result.tap()
                sleep(4)
                save("92-buscar-abierto")
            }
        }
        let category = app.descendants(matching: .any).matching(identifier: "categoryTile").firstMatch
        if category.exists {
            category.tap()
            sleep(3)
            save("93-categoria")
        }
    }

    /// Inicio, Nuevo y Radio: desplazar cada una y abrir una tarjeta grande desde Inicio.
    func testShelves() {
        record("28-recorrido-inicio-nuevo-radio")
        var app = launch(["-demoTab", "home"])
        sleep(5)
        save("95-inicio")
        app.swipeUp(velocity: .slow)
        sleep(2)
        save("96-inicio-abajo")
        let hero = app.descendants(matching: .any).matching(identifier: "heroCard").firstMatch
        app.swipeDown(velocity: .slow)
        sleep(1)
        if hero.exists {
            hero.tap()
            sleep(7)
            save("97-inicio-tarjeta-abierta")
        }
        app.terminate()
        app = launch(["-demoTab", "new"])
        sleep(5)
        save("98-nuevo")
        app.swipeUp(velocity: .slow)
        sleep(2)
        save("98b-nuevo-abajo")
        app.terminate()
        app = launch(["-demoTab", "radio"])
        sleep(5)
        save("99-radio")
    }

    /// Liquid Glass teñido (Ajustes › Pantalla y brillo › Liquid Glass › Teñido, iOS 26.1+): se activa en
    /// los Ajustes del simulador y se repiten las capturas de la barra, el miniplayer y la hoja para
    /// compararlas con las de siempre (transparente). Va el último: deja el ajuste cambiado.
    func testTintedGlass() {
        record("24-recorrido-vidrio-tenido")
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        settings.launch()
        sleep(2)
        save("60-ajustes")
        func open(_ labels: [String], in app: XCUIApplication) -> Bool {
            for label in labels {
                let candidates = [app.cells.staticTexts[label], app.buttons[label], app.staticTexts[label]]
                for element in candidates {
                    var tries = 0
                    while !element.exists && tries < 6 { app.swipeUp(velocity: .slow); tries += 1 }
                    if element.exists {
                        element.tap()
                        sleep(2)
                        return true
                    }
                    // Volver arriba antes de probar el siguiente nombre.
                    for _ in 0..<6 { app.swipeDown(velocity: .fast) }
                }
            }
            return false
        }
        // iOS 27: Ajustes › Appearance › Liquid Glass tiene una barra "Tint Amount" de Clear (izquierda) a
        // Tinted (derecha), por defecto en el medio (las capturas de siempre). Se lleva al máximo de tinte.
        let display = open(["Appearance", "Apariencia", "Display & Brightness", "Pantalla y brillo"], in: settings)
        save("61-ajustes-pantalla")
        let glass = display && open(["Liquid Glass"], in: settings)
        save("62-ajustes-liquid-glass")
        write("64-ajustes-arbol-liquid-glass.txt", settings.debugDescription)
        var tinted = false
        let slider = settings.sliders.firstMatch
        if glass, slider.waitForExistence(timeout: 3) {
            slider.adjust(toNormalizedSliderPosition: 1)
            sleep(2)
            tinted = true
        } else if glass {
            tinted = open(["Tinted", "Teñido"], in: settings)
        }
        save("63-ajustes-tenido")
        guard tinted else {
            XCTFail("No se encontró el ajuste de Liquid Glass (pantalla: \(display), liquid glass: \(glass))")
            return
        }
        settings.terminate()

        var app = launch(["-demoTab", "home"])
        sleep(8)
        save("65-tenido-inicio")
        app = launch(["-demoTab", "library"])
        sleep(10)
        save("66-tenido-biblioteca")
        app = launch(["-demoOpenPlayer", "YES"])
        sleep(12)
        save("67-tenido-hoja")
        _ = app
    }

    /// Pantalla de bloqueo: con la app conectada (suena el silencio), bloquear y despertar el simulador
    /// para ver el reproductor del sistema con la canción de Cider. Guarda capturas en `DEMO_OUT`
    /// (`TEST_RUNNER_DEMO_OUT` en xcodebuild) además de adjuntarlas a la prueba.
    func testLockScreen() {
        record("22-recorrido-pantalla-bloqueo")
        let app = launch(["-demoTab", "library"])
        XCTAssertTrue(app.otherElements["miniPlayer"].waitForExistence(timeout: 10))
        sleep(6)
        let device = XCUIDevice.shared
        let lock = NSSelectorFromString("pressLockButton")
        guard device.responds(to: lock) else {
            XCTFail("XCUIDevice no tiene pressLockButton en esta versión")
            return
        }
        device.perform(lock)   // bloquear (pantalla apagada)
        sleep(2)
        device.perform(lock)   // despertar: pantalla de bloqueo
        // Una captura por segundo: ¿cuándo se oscurece? (si es la pantalla siempre activa, a los ~8–10 s)
        for second in 1...12 {
            sleep(1)
            save(String(format: "39-bloqueo-%02ds", second))
        }
        save("40-pantalla-bloqueo")
        sleep(2)
        save("41-pantalla-bloqueo-despues")
        // La portada animada: iOS pide el vídeo al enseñar la carátula grande y la app lo baja (~5 MB).
        sleep(10)
        save("44-pantalla-bloqueo-animada")
        // ¿Están los botones del reproductor aunque no se vean? Árbol de accesibilidad de SpringBoard.
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        write("42-pantalla-bloqueo-accesibilidad.txt", springboard.debugDescription)
        // Si hay botón de pausa, tocarlo: la orden debe llegar al Cider de demostración (fake-cider.log).
        // Un toque en una zona vacía (encima del indicador de inicio): si estaba en la pantalla
        // siempre activa, se despierta y la captura sale más clara.
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9)).tap()
        sleep(1)
        save("45-bloqueo-tras-tocar")
        let pause = springboard.buttons.matching(NSPredicate(format: "label IN %@", ["Pause", "Pausa", "Pausar"])).firstMatch
        if pause.waitForExistence(timeout: 3) {
            pause.tap()
            sleep(3)
            save("43-pantalla-bloqueo-tras-pausa")
        }
        // Desbloquear (sin código): las pruebas siguientes van en la misma tanda.
        device.press(.home)
        sleep(2)
    }

    private func write(_ name: String, _ text: String) {
        let attachment = XCTAttachment(string: text)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["DEMO_OUT"] {
            try? text.write(to: URL(fileURLWithPath: dir).appendingPathComponent(name), atomically: true, encoding: .utf8)
        }
    }

    private func save(_ name: String) {
        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        if let dir = ProcessInfo.processInfo.environment["DEMO_OUT"] {
            try? shot.pngRepresentation.write(to: URL(fileURLWithPath: dir).appendingPathComponent("\(name).png"))
        }
    }
}
