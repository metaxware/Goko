package org.goko.common.preferences.fieldeditor.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.goko.common.preferences.fieldeditor.ui.converter.BindingConverter;
import org.goko.common.preferences.fieldeditor.ui.converter.QuantityToStringConverter;
import org.goko.common.preferences.fieldeditor.ui.converter.StringToQuantityConverter;
import org.goko.core.common.measure.quantity.Quantity;
import org.goko.core.common.measure.units.Unit;
import org.goko.core.log.GkLog;

public abstract class UiQuantityFieldEditor<Q extends Quantity<Q>> extends UiBigDecimalFieldEditor {
	private static final GkLog LOG = GkLog.getLogger(UiQuantityFieldEditor.class);
	private Label labelUnit;
	private Unit<Q> unit;
	
	public UiQuantityFieldEditor(Composite parent, int style) {
		super(parent, style);
	}
	
	/** (inheritDoc)
	 * @see org.goko.common.preferences.fieldeditor.ui.UiFieldEditor#createLayout(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	protected void createLayout(Composite parent) {
    	GridLayout layout = new GridLayout(3, false);
    	layout.marginHeight = 2;
    	layout.marginWidth = 2;
    	setLayout(layout);    	
	}
	
	/** (inheritDoc)
	 * @see org.goko.common.preferences.fieldeditor.ui.UiStringFieldEditor#createControls(org.eclipse.swt.widgets.Composite, int)
	 */
	@Override
	protected void createControls(Composite parent, int style) {		
		super.createControls(parent, style);
		labelUnit = new Label(this, style);    	
    	labelUnit.setText("--");
    	labelUnit.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
    	labelUnit.pack();
    	pack();
	}

	/**
	 * @return the unit
	 */
	public Unit<Q> getUnit() {
		return unit;
	}

	/**
	 * @param unit the unit to set
	 */
	public void setUnit(Unit<Q> unit) {
		this.unit = unit;
		labelUnit.setText( unit.getSymbol() );
		labelUnit.pack();
		pack();
		layout();
	}
	
	/** (inheritDoc)
	 * @see org.goko.common.preferences.fieldeditor.ui.UiBigDecimalFieldEditor#getConverter()
	 */
	@Override
	public IBindingConverter getConverter() {				
		return new BindingConverter(getQuantityConverter(), new QuantityToStringConverter());		
	}
	
	protected abstract StringToQuantityConverter<Q> getQuantityConverter();
	
//	class StringToQuantityConverter extends Converter{
//
//		public StringToQuantityConverter() {
//			super(String.class, BigDecimalQuantity.class);
//		}
//
//		/** (inheritDoc)
//		 * @see org.eclipse.core.databinding.conversion.IConverter#convert(java.lang.Object)
//		 */
//		@Override
//		public Object convert(Object fromObject) {			
//			try {
//				return NumberQuantity.of(BigDecimalUtils.parse((String)fromObject), unit);
//			} catch (GkException e) {
//				return null;
//			}
//		}
//		
//	}
//	
//	class QuantityToStringConverter extends Converter{
//
//		public QuantityToStringConverter() {
//			super(BigDecimalQuantity.class, String.class);
//		}
//
//		/** (inheritDoc)
//		 * @see org.eclipse.core.databinding.conversion.IConverter#convert(java.lang.Object)
//		 */
//		@Override
//		public Object convert(Object fromObject) {	
//			try {
//				return GokoPreference.getInstance().format((Quantity<Q>)fromObject,false, false, unit);
//			} catch (GkException e) {
//				LOG.error(e);
//				return "ERROR";
//			}			
//		}
//		
//	}
}
