package cider.remote.app.ui.components

/**
 * Vidrio líquido en AGSL, portado de my-lglass (`prototipo/shaders/glass.frag`,
 * el efecto de GNOME Shell que el autor usa en su PC). Recibe el fondo ya
 * desenfocado (cadena de RenderEffect: desenfoque → este shader) con un margen
 * alrededor de la pieza, para que la lente pueda tomar lo de fuera.
 *
 * Todo en px de la captura: la pieza va de [origin] a [origin] + [size].
 * Parte de my-lglass que no se usa aquí: lupa, borde fundido, desenfoque
 * progresivo, contraste adaptativo y el fondo nítido en la franja curvada.
 */
internal const val LIQUID_GLASS_AGSL = """
uniform shader backdrop;

uniform float2 size;        // tamaño de la pieza
uniform float2 origin;      // esquina de la pieza dentro de la captura
uniform float4 valid;       // parte de la captura con píxeles reales (x0, y0, x1, y1)
uniform float corner;       // radio de las esquinas
uniform float bezel;        // ancho de la franja curvada
uniform float refraction;   // intensidad de la lente (negativa: toma lo de fuera, como iOS 26)
uniform float dispersion;   // separación de colores
uniform float saturation;
uniform float brightness;
uniform half4 tint;         // velo sobre fondos oscuros
uniform half4 tintLight;    // velo sobre fondos claros
uniform float2 light;       // dirección hacia la luz (y hacia abajo)
uniform float2 veil;        // luminancia del fondo entre la que el velo pasa de oscuro a claro
uniform float rim;          // brillo del contorno
uniform float rimWidth;
uniform float rimOpposite;
uniform float innerShadow;
uniform float innerShadowWidth;
uniform float edgeLine;
uniform float outline;      // línea oscura en el contorno sobre fondos claros

half4 fondo(float2 p) {
    return backdrop.eval(clamp(p, valid.xy, valid.zw));
}

half luminancia(half3 c) {
    return dot(c, half3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 coord) {
    float2 p = coord - origin;

    // Distancia al borde del rectángulo redondeado (negativa dentro).
    float2 mitad = 0.5 * size;
    float2 c = p - mitad;
    float r = min(corner, min(mitad.x, mitad.y));
    float2 q = abs(c) - (mitad - r);
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    if (d > 1.0) return half4(0.0);
    float dentro = -d;

    // Normal hacia afuera.
    float2 n;
    if (q.x > 0.0 && q.y > 0.0) n = normalize(q);
    else n = (q.x > q.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    n *= float2(c.x < 0.0 ? -1.0 : 1.0, c.y < 0.0 ? -1.0 : 1.0);

    // Perfil circular de la lente: fuerte junto al borde, nulo en el centro.
    float b = max(bezel, 0.001);
    float t = clamp(1.0 - dentro / b, 0.0, 1.0);
    float h = refraction * b * (1.0 - sqrt(max(1.0 - t * t, 0.0)));
    if (refraction > 0.0) {
        // Sin pasar de las líneas centrales (la imagen se voltearía).
        float2 hueco = abs(c);
        float tope = 1.0e6;
        if (abs(n.x) > 0.001) tope = min(tope, hueco.x / abs(n.x));
        if (abs(n.y) > 0.001) tope = min(tope, hueco.y / abs(n.y));
        tope = 0.9 * max(tope, 0.5);
        h = h / sqrt(1.0 + (h / tope) * (h / tope));
    }
    float2 paso = -n;

    // Separación de colores.
    half4 muestra;
    if (dispersion > 0.0 && h != 0.0) {
        half4 g = fondo(coord + paso * h);
        muestra = half4(fondo(coord + paso * h * (1.0 - dispersion)).r, g.g,
                        fondo(coord + paso * h * (1.0 + dispersion)).b, g.a);
    } else {
        muestra = fondo(coord + paso * h);
    }

    half3 color = muestra.rgb;
    color = mix(half3(luminancia(color)), color, half(saturation));
    color *= half(brightness);

    // Brillo medio del fondo (cinco muestras) para el velo y el contorno.
    float2 m0 = origin + mitad;
    float2 cuarto = 0.5 * mitad;
    half fl = luminancia((fondo(m0).rgb + fondo(m0 - cuarto).rgb + fondo(m0 + cuarto).rgb +
                          fondo(m0 + float2(-cuarto.x, cuarto.y)).rgb + fondo(m0 + float2(cuarto.x, -cuarto.y)).rgb) * 0.2);

    half4 velo = mix(tint, tintLight, smoothstep(veil.x, veil.y, fl));
    color = mix(color, velo.rgb * muestra.a, velo.a);

    float ld = dot(n, light);

    // Sombra interior: da espesor al vidrio.
    if (innerShadow > 0.0)
        color *= half(1.0 - innerShadow * (1.0 - smoothstep(0.0, max(innerShadowWidth, 0.5), dentro)));

    // Línea brillante del lado de la luz, reflejo del opuesto y brillo suave en la franja curvada.
    float linea = 1.0 - clamp(dentro / max(rimWidth, 0.5), 0.0, 1.0);
    linea *= linea;
    float brillo = rim * (linea * (max(ld, 0.0) + rimOpposite * max(-ld, 0.0)) + 0.12 * t * t * t * (0.5 + 0.5 * ld));
    color += half3(brillo) * muestra.a;

    // Línea oscura fina donde no llega la luz.
    if (edgeLine > 0.0) {
        float borde = 1.0 - clamp(dentro, 0.0, 1.0);
        color *= half(1.0 - edgeLine * borde * (1.0 - abs(ld)));
    }
    // Contorno oscuro solo sobre fondos claros.
    if (outline > 0.0) {
        float borde = 1.0 - clamp(dentro / 1.2, 0.0, 1.0);
        color *= half(1.0 - outline * borde * smoothstep(0.45, 0.9, fl));
    }

    half mascara = half(clamp(0.5 - d, 0.0, 1.0));
    return half4(color, muestra.a) * mascara;
}
"""
