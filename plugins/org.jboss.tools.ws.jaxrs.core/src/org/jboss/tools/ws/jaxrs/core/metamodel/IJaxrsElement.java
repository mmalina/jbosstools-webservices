/******************************************************************************* 
 * Copyright (c) 2008 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Xavier Coulon - Initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.ws.jaxrs.core.metamodel;

import org.eclipse.core.resources.IResource;

/**
 * Base interface for all JAX-RS elements
 * @author Xavier Coulon
 *
 */
public interface IJaxrsElement {
	
	public abstract IJaxrsMetamodel getMetamodel();

	public abstract EnumElementKind getElementKind();

	public abstract EnumElementCategory getElementCategory();
	
	public abstract IResource getResource();
	
	public abstract String getName();	
	
	public abstract boolean isBinary();


}
