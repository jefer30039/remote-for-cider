import SwiftUI

/// Banco de pruebas (solo CI, `-glassLab YES`): animaciones de Liquid Glass de iOS 27 que la versión
/// de Android quiere copiar. (1) Un botón de barra que aparece junto a otro: al empujar una página
/// con compartir + "…", al añadirlo con `withAnimation` en la barra y en un `GlassEffectContainer`
/// con `glassEffectID`. (2) El "…" de la barra que se convierte en menú. Fondo de colores detrás para
/// ver el vidrio.
struct GlassLab: View {
    @State private var path: [Int] = []
    @State private var shareInBar = false
    @State private var shareInContainer = false
    @Namespace private var glass

    var body: some View {
        NavigationStack(path: $path) {
            LabBackground {
                VStack(spacing: 18) {
                    Spacer().frame(height: 140)
                    labButton("Abrir página", id: "labPush") { path.append(1) }
                    labButton("Compartir en la barra", id: "labToggleBar") {
                        withAnimation { shareInBar.toggle() }
                    }
                    labButton("Compartir en el contenedor", id: "labToggleContainer") {
                        withAnimation { shareInContainer.toggle() }
                    }
                    // Variante sin barra: dos botones de vidrio en un contenedor (se funden al acercarse).
                    GlassEffectContainer(spacing: 40) {
                        HStack(spacing: 12) {
                            if shareInContainer {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.system(size: 19, weight: .medium))
                                    .frame(width: 48, height: 48)
                                    .glassEffect(.regular.interactive(), in: .circle)
                                    .glassEffectID("share", in: glass)
                            }
                            Image(systemName: "ellipsis")
                                .font(.system(size: 19, weight: .medium))
                                .frame(width: 48, height: 48)
                                .glassEffect(.regular.interactive(), in: .circle)
                                .glassEffectID("more", in: glass)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.horizontal, 20)
                    .padding(.top, 30)
                    Spacer()
                }
            }
            .toolbar {
                if shareInBar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Compartir", systemImage: "square.and.arrow.up") {}
                    }
                }
                ToolbarItem(placement: .topBarTrailing) { LabMenu(id: "labMenuRoot") }
            }
            .navigationDestination(for: Int.self) { _ in
                LabBackground(shift: 0.35) {
                    VStack {
                        Spacer().frame(height: 160)
                        Text("Página empujada").font(.largeTitle.bold()).foregroundStyle(.white)
                        Spacer()
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Compartir", systemImage: "square.and.arrow.up") {}
                    }
                    ToolbarItem(placement: .topBarTrailing) { LabMenu(id: "labMenuPushed") }
                }
            }
        }
    }

    private func labButton(_ title: String, id: String, action: @escaping () -> Void) -> some View {
        Button(title, action: action)
            .buttonStyle(.glass)
            .accessibilityIdentifier(id)
    }
}

/// "…" de la barra con menú (como el de la página de álbum).
private struct LabMenu: View {
    let id: String

    var body: some View {
        Menu {
            Button("Reproducir a continuación", systemImage: "text.line.first.and.arrowtriangle.forward") {}
            Button("Reproducir al final", systemImage: "text.line.last.and.arrowtriangle.forward") {}
            Button("Añadir a una playlist…", systemImage: "text.badge.plus") {}
            Divider()
            Button("Compartir…", systemImage: "square.and.arrow.up") {}
        } label: {
            Image(systemName: "ellipsis")
        }
        .accessibilityIdentifier(id)
    }
}

/// Franjas de colores y texto que se desplazan: lo que hay detrás del vidrio.
private struct LabBackground<Content: View>: View {
    var shift: Double = 0
    @ViewBuilder let content: Content

    var body: some View {
        ScrollView {
            ZStack(alignment: .top) {
                VStack(spacing: 0) {
                    ForEach(0..<24, id: \.self) { i in
                        let hue = (Double(i) / 12 + shift).truncatingRemainder(dividingBy: 1)
                        Color(hue: hue, saturation: 0.75, brightness: 0.95)
                            .frame(height: 70)
                            .overlay(alignment: .leading) {
                                Text(verbatim: "Franja \(i + 1) · Remote for Cider")
                                    .font(.title3.bold())
                                    .foregroundStyle(.white)
                                    .padding(.leading, 20)
                            }
                    }
                }
                content
            }
        }
        .ignoresSafeArea()
    }
}
