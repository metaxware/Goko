package org.goko.tools.viewer.jogl.preferences;

import org.eclipse.jface.resource.StringConverter;
import org.eclipse.swt.graphics.RGB;
import org.goko.core.common.exception.GkException;
import org.goko.core.common.measure.quantity.Length;
import org.goko.core.config.GkPreferenceInitializer;
import org.goko.core.math.Tuple6b;

public class JoglViewerPreferenceInitializer extends GkPreferenceInitializer{

	/** (inheritDoc)
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultGkPreferences()
	 */
	@Override
	public void initializeDefaultGkPreferences() throws GkException {
		JoglViewerPreference prefs = JoglViewerPreference.getInstance();
		prefs.setDefault(JoglViewerPreference.ROTARY_AXIS_ENABLED, true);
		prefs.setDefault(JoglViewerPreference.ROTARY_AXIS_DIRECTION, "X");
		prefs.setDefault(JoglViewerPreference.ROTARY_AXIS_POSITION_X, "0mm");
		prefs.setDefault(JoglViewerPreference.ROTARY_AXIS_POSITION_Y, "0mm");
		prefs.setDefault(JoglViewerPreference.ROTARY_AXIS_POSITION_Z, "0mm");		
		prefs.setDefault(JoglViewerPreference.MULTISAMPLING, "1");
		
		prefs.setDefault(JoglViewerPreference.MAJOR_GRID_SPACING, "10mm");
		prefs.setDefault(JoglViewerPreference.MINOR_GRID_SPACING, "1mm");
		
		prefs.setDefault(JoglViewerPreference.MAJOR_GRID_COLOR, StringConverter.asString(new RGB(124, 124, 124)));
		prefs.setDefault(JoglViewerPreference.MINOR_GRID_COLOR, StringConverter.asString(new RGB(64, 64, 64)));
		prefs.setDefault(JoglViewerPreference.GRID_OPACITY, 50);
		
		prefs.setDefault(JoglViewerPreference.GRID_START_X, "-100mm");
		prefs.setDefault(JoglViewerPreference.GRID_START_Y, "-100mm");
		prefs.setDefault(JoglViewerPreference.GRID_START_Z, "-100mm");
		prefs.setDefault(JoglViewerPreference.GRID_END_X, "100mm");
		prefs.setDefault(JoglViewerPreference.GRID_END_Y, "100mm");
		prefs.setDefault(JoglViewerPreference.GRID_END_Z, "100mm");
		
		Tuple6b position = new Tuple6b();
		position.setX( Length.parse(prefs.getString(JoglViewerPreference.ROTARY_AXIS_POSITION_X)));
		position.setY( Length.parse(prefs.getString(JoglViewerPreference.ROTARY_AXIS_POSITION_Y)));
		position.setZ( Length.parse(prefs.getString(JoglViewerPreference.ROTARY_AXIS_POSITION_Z)));
		
		prefs.setRotaryAxisPosition(position);
	}

}
