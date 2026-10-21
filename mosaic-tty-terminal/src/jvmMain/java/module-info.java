module org.unitedsoftware.mosaic.tty.terminal {
	requires transitive kotlin.stdlib;
	requires transitive kotlinx.coroutines.core;
	requires transitive org.unitedsoftware.mosaic.tty;
	requires transitive org.unitedsoftware.mosaic.terminal;

	exports org.unitedsoftware.mosaic.tty.terminal;
}
