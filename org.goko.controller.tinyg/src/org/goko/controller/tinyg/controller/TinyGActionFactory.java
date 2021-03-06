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
package org.goko.controller.tinyg.controller;

import java.util.HashMap;
import java.util.Map;

import org.goko.controller.tinyg.controller.actions.TinyGCycleStartAction;
import org.goko.controller.tinyg.controller.actions.TinyGFeedHoldAction;
import org.goko.controller.tinyg.controller.actions.TinyGHomingAction;
import org.goko.controller.tinyg.controller.actions.TinyGKillAlarmAction;
import org.goko.controller.tinyg.controller.actions.TinyGResetZeroAction;
import org.goko.controller.tinyg.controller.actions.TinyGSpindleOffAction;
import org.goko.controller.tinyg.controller.actions.TinyGSpindleOnAction;
import org.goko.controller.tinyg.controller.actions.TinyGStartJogAction;
import org.goko.controller.tinyg.controller.actions.TinyGStopAction;
import org.goko.controller.tinyg.controller.actions.TinyGStopJogAction;
import org.goko.core.controller.action.IGkControllerAction;

public class TinyGActionFactory {
	private Map<String, IGkControllerAction> mapAction;
	private TinyGControllerService controllerService;

	/**
	 * @param controllerService
	 */
	protected TinyGActionFactory(TinyGControllerService controllerService) {
		super();
		this.controllerService = controllerService;
		mapAction = new HashMap<String, IGkControllerAction>();
		createHomingAction();
		createCycleStartAction();
		createStopAction();
		createFeedHoldAction();
		createResetZeroAction();
		createJogStartAction();
		createJogStopAction();
		createSpindleAction();
		createKillAlarmAction();
	}
	
	private void createKillAlarmAction() {		
		TinyGKillAlarmAction action = new TinyGKillAlarmAction(controllerService);
		mapAction.put(action.getId(), action);
	}

	private void createJogStopAction(){
		TinyGStopJogAction action = new TinyGStopJogAction(controllerService);
		mapAction.put(action.getId(), action);
	}

	private void createJogStartAction(){
		TinyGStartJogAction action = new TinyGStartJogAction(controllerService);
		mapAction.put(action.getId(), action);
	}
	private void createResetZeroAction() {
		TinyGResetZeroAction action = new TinyGResetZeroAction(controllerService);
		mapAction.put(action.getId(), action);
	}

	private void createFeedHoldAction() {
		TinyGFeedHoldAction action = new TinyGFeedHoldAction(controllerService);
		mapAction.put(action.getId(), action);
	}

	private void createStopAction() {
		TinyGStopAction action = new TinyGStopAction(controllerService);
		mapAction.put(action.getId(), action);
	}

	private void createCycleStartAction() {
		TinyGCycleStartAction action = new TinyGCycleStartAction(controllerService);
		mapAction.put(action.getId(), action);
	}

	private void createHomingAction(){
		TinyGHomingAction action = new TinyGHomingAction(controllerService);
		mapAction.put(action.getId(), action);
	}

	private void createSpindleAction(){
		TinyGSpindleOffAction action = new TinyGSpindleOffAction(controllerService);
		mapAction.put(action.getId(), action);
		TinyGSpindleOnAction actionOn = new TinyGSpindleOnAction(controllerService);
		mapAction.put(actionOn.getId(), actionOn);
	}
	
	protected IGkControllerAction findAction(String id){
		IGkControllerAction action = null;
		if(mapAction.containsKey(id)){
			action = mapAction.get(id);
		}
		return action;
	}
}
