# Z-Type ProGuard Rules

# Keep the IME service
-keep class com.zooptype.ztype.engine.ZTypeIMEService { *; }

# Keep Room entities
-keep class com.zooptype.ztype.persistence.** { *; }

# Keep OpenGL shader code (loaded as strings)
-keep class com.zooptype.ztype.engine.shaders.** { *; }
