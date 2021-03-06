/*
 *
 *   Goko
 *   Copyright (C) 2013  PsyKo
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.goko.tools.commandpanel.controller;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import org.eclipse.core.databinding.observable.Observables;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.goko.common.bindings.AbstractController;
import org.goko.core.common.event.EventListener;
import org.goko.core.common.exception.GkException;
import org.goko.core.common.measure.quantity.Length;
import org.goko.core.common.measure.quantity.Speed;
import org.goko.core.common.measure.units.Unit;
import org.goko.core.controller.IControllerService;
import org.goko.core.controller.ICoordinateSystemAdapter;
import org.goko.core.controller.IJogService;
import org.goko.core.controller.action.IGkControllerAction;
import org.goko.core.controller.bean.EnumControllerAxis;
import org.goko.core.controller.event.MachineValueUpdateEvent;
import org.goko.core.gcode.execution.IExecutionTokenState;
import org.goko.core.gcode.rs274ngcv3.context.EnumCoordinateSystem;
import org.goko.core.gcode.rs274ngcv3.context.GCodeContext;
import org.goko.core.log.GkLog;

/**
 * Command panel controller
 *
 * @author PsyKo
 *
 */
public class CommandPanelController  extends AbstractController<CommandPanelModel> implements PropertyChangeListener {
	private final static GkLog LOG = GkLog.getLogger(CommandPanelController.class);
	@Inject
	private IControllerService<IExecutionTokenState, GCodeContext> controllerService;
	@Inject
	@Optional
	private ICoordinateSystemAdapter<EnumCoordinateSystem> coordinateSystemAdapter;	
	@Inject	
	private IJogService jogService;
	private Timer timer;
	
	public CommandPanelController(CommandPanelModel binding) {
		super(binding);
		getDataModel().addPropertyChangeListener(this);
		
	}

	@Override
	public void initialize() throws GkException {
		controllerService.addListener(this);	
		getDataModel().setPreciseJogForced(false);
	}

	public void bindEnableControlWithAction(Control widget, String actionId) throws GkException{
		getDataModel().setActionEnabled( actionId, false );
		// Let's do the binding
		IObservableValue 	modelObservable 	= Observables.observeMapEntry(getDataModel().getActionState(), actionId, Boolean.class);
		IObservableValue controlObservable 		= WidgetProperties.enabled().observe(widget);
		getBindingContext().bindValue(controlObservable, modelObservable);

		// If supported, let's report to the action definition
		if(controllerService.isControllerAction(actionId)){
			IGkControllerAction action = controllerService.getControllerAction(actionId);
			getDataModel().setActionEnabled( action.getId(), action.canExecute() );
		}
	}

	@EventListener(MachineValueUpdateEvent.class)
	public void onMachineStateUpdate(final MachineValueUpdateEvent event) throws GkException{
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				try {
					refreshExecutableAction();
				} catch (GkException e) {
					LOG.error(e);
				}
			}
		});
	}

	public void refreshExecutableAction() throws GkException{
		for(Object key : getDataModel().getActionState().keySet()){
			if(controllerService.isControllerAction(String.valueOf(key))){
				IGkControllerAction action = controllerService.getControllerAction(String.valueOf(key));
				getDataModel().setActionEnabled( action.getId(), action.canExecute() );
			}
		}
	}

	public void bindButtonToExecuteAction(Control widget, String actionId, final Object... parameters) throws GkException{
		if(controllerService.isControllerAction(actionId)){
			final IGkControllerAction action = controllerService.getControllerAction(actionId);
			widget.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseUp(MouseEvent e) {
					try {
						action.execute(parameters);
					} catch (GkException e1) {
						LOG.error(e1);
					}
				}
			});
		}
	}
	
	public void bindFocusLostToExecuteAction(Control widget, String actionId, final Object... parameters) throws GkException{
		if(controllerService.isControllerAction(actionId)){
			final IGkControllerAction action = controllerService.getControllerAction(actionId);
			widget.addFocusListener(new FocusAdapter() {
				@Override
				public void focusLost(FocusEvent e) {
					try {
						action.execute(parameters);
					} catch (GkException e1) {
						LOG.error(e1);
					}
				}
			});
		}
	}

	public void bindJogButton(Button widget, final EnumControllerAxis axis) throws GkException {		
		widget.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				try {					
					Unit<Length> unit = controllerService.getGCodeContext().getUnit().getUnit();
					getDataModel().setLengthUnit(unit);
					Length step = null;					
					boolean isPrecise = getDataModel().isPreciseJog();
					final Speed feedrate = getDataModel().getJogSpeed();
					if(isPrecise){
						step = getDataModel().getJogIncrement();
					}
					final Length taskStep = step;
					timer = new Timer();					
					timer.schedule(new TimerTask() {
						
						@Override
						public void run() {
							try {
								jogService.jog(axis, taskStep, feedrate);
							} catch (GkException e) {
								LOG.error(e);
							}
						}
					}, 0, 100);
								
				} catch (GkException e1) {
					LOG.error(e1);
				}
			}

			@Override
			public void mouseUp(MouseEvent e) {
				try {
					Unit<Length> unit = controllerService.getGCodeContext().getUnit().getUnit();
					getDataModel().setLengthUnit(unit);
					timer.cancel();
					jogService.stopJog();					
				} catch (GkException e1) {
					LOG.error(e1);
				}
			}
		});
		
		widget.addFocusListener(new FocusAdapter() {
			/** (inheritDoc)
			 * @see org.eclipse.swt.events.FocusAdapter#focusLost(org.eclipse.swt.events.FocusEvent)
			 */
			@Override
			public void focusLost(FocusEvent e) {
				try {						
					jogService.stopJog();					
				} catch (GkException e1) {
					LOG.error(e1);
				}
			}
		});
	}
	
	public void setCoordinateSystem(EnumCoordinateSystem enumCoordinateSystem) throws GkException {
		coordinateSystemAdapter.setCurrentCoordinateSystem(enumCoordinateSystem);
	}

	public void zeroCoordinateSystem() throws GkException {
		coordinateSystemAdapter.resetCurrentCoordinateSystem();
	}

	/** (inheritDoc)
	 * @see java.beans.PropertyChangeListener#propertyChange(java.beans.PropertyChangeEvent)
	 */
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
				
	}	
}

