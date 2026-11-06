# Modelos de la API (kotlinx.serialization): la librería trae sus propias reglas,
# pero los serializadores generados en `shared` se buscan por reflexión en el companion.
-keepclassmembers @kotlinx.serialization.Serializable class cider.remote.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class cider.remote.**$$serializer { *; }
-dontwarn org.slf4j.**
