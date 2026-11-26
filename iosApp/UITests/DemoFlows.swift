import XCTest

/// Recorridos para los vídeos del simulador en CI (iosApp/ci/demo.sh graba la pantalla mientras corren).
/// No comprueban casi nada: mueven la app como lo haría una persona, con pausas para ver las animaciones.
final class DemoFlows: XCTestCase {
    private func launch(_ extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-ciderHost", "127.0.0.1", "-ciderPort", "10767", "-ciderToken", "demo",
                               "-AppleLanguages", "(es)", "-AppleLocale", "es_ES"] + extra
        app.launch()
        return app
    }

    /// Biblioteca: desplazar hacia abajo (la barra se minimiza con el miniplayer dentro) y volver arriba.
    func testScrollMinimizesTabBar() {
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

    /// Pantalla de bloqueo: con la app conectada (suena el silencio), bloquear y despertar el simulador
    /// para ver el reproductor del sistema con la canción de Cider. Guarda capturas en `DEMO_OUT`
    /// (`TEST_RUNNER_DEMO_OUT` en xcodebuild) además de adjuntarlas a la prueba.
    func testLockScreen() {
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
