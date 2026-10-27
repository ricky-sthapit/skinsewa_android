# Keep no secrets in the client. Gson uses reflective DTO names; preserve their fields in minified releases.
-keepclassmembers class org.skinsewa.core.network.dto.** { <fields>; }
