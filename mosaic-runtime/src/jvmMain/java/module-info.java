module org.unitedsoftware.mosaic.runtime {
	requires transitive kotlin.stdlib;
	requires transitive kotlinx.coroutines.core;
	requires transitive org.unitedsoftware.mosaic.terminal;
	requires org.unitedsoftware.mosaic.tty.terminal;

	exports org.unitedsoftware.mosaic;
	exports org.unitedsoftware.mosaic.layout;
	exports org.unitedsoftware.mosaic.modifier;
	exports org.unitedsoftware.mosaic.text;
	exports org.unitedsoftware.mosaic.ui;
	exports org.unitedsoftware.mosaic.ui.unit;
}
