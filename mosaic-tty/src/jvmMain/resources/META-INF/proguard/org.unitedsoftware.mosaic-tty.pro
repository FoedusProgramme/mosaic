# Note: Our JNI code only creates JDK types and only uses Java built-in types across the boundary.
-keep class org.unitedsoftware.mosaic.tty.Jni {
  native <methods>;
}

-keepdirectories org/unitedsoftware/mosaic/tty/jni

# These members are interacted with through native code.
-keep interface org.unitedsoftware.mosaic.tty.Tty$Callback {
	<methods>;
}
