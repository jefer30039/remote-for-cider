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
        let display = open(["Display & Brightness", "Pantalla y brillo"], in: settings)
        save("61-ajustes-pantalla")
        let glass = display && open(["Liquid Glass"], in: settings)
        save("62-ajustes-liquid-glass")
        let tinted = glass && open(["Tinted", "Teñido"], in: settings)
        save("63-ajustes-tenido")
        write("64-ajustes-arbol.txt", settings.debugDescription)
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
