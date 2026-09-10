# kotlinx.serialization keeps its generated serializers through reflection-free
# lookups, but the @Serializable classes' companions must survive shrinking.
-keepclassmembers class com.harmen.pafta.** {
    *** Companion;
}
-keepclasseswithmembers class com.harmen.pafta.** {
    kotlinx.serialization.KSerializer serializer(...);
}
