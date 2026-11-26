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
        let app = launch(["-demoTab", "library"])
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
    }
}
