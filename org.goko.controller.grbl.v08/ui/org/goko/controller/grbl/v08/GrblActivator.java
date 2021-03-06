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
package org.goko.controller.grbl.v08;

import java.util.ResourceBundle;

import org.goko.core.common.i18n.MessageResource;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class GrblActivator implements BundleActivator{

	@Override
	public void start(BundleContext context) throws Exception {
		MessageResource.registerResourceBundle(ResourceBundle.getBundle("org.goko.controller.grbl.v08.i18n.Messages"));
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		// TODO Auto-generated method stub

	}

}
