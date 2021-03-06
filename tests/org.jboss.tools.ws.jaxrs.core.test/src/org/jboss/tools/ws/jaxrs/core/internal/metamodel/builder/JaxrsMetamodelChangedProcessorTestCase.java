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
package org.jboss.tools.ws.jaxrs.core.internal.metamodel.builder;

import static org.eclipse.jdt.core.IJavaElementDelta.ADDED;
import static org.eclipse.jdt.core.IJavaElementDelta.CHANGED;
import static org.eclipse.jdt.core.IJavaElementDelta.REMOVED;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.jboss.tools.ws.jaxrs.core.WorkbenchUtils.changeAnnotation;
import static org.jboss.tools.ws.jaxrs.core.WorkbenchUtils.resolveAnnotation;
import static org.jboss.tools.ws.jaxrs.core.WorkbenchUtils.getMethod;
import static org.jboss.tools.ws.jaxrs.core.WorkbenchUtils.getType;
import static org.jboss.tools.ws.jaxrs.core.internal.metamodel.builder.JaxrsElementDelta.F_ELEMENT_KIND;
import static org.jboss.tools.ws.jaxrs.core.internal.metamodel.builder.JaxrsElementDelta.F_PATH_VALUE;
import static org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname.APPLICATION_PATH;
import static org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname.CONSUMES;
import static org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname.GET;
import static org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname.HTTP_METHOD;
import static org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname.PATH;
import static org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname.POST;
import static org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname.PRODUCES;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.spy;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.jboss.tools.ws.jaxrs.core.AbstractCommonTestCase;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsBaseElement;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsBuiltinHttpMethod;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsEndpoint;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsHttpMethod;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsJavaApplication;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsMetamodel;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResource;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResourceMethod;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsResourceMethod.Builder;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsWebxmlApplication;
import org.jboss.tools.ws.jaxrs.core.internal.utils.WtpUtils;
import org.jboss.tools.ws.jaxrs.core.jdt.Annotation;
import org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname;
import org.jboss.tools.ws.jaxrs.core.jdt.JavaMethodParameter;
import org.jboss.tools.ws.jaxrs.core.jdt.JavaMethodSignature;
import org.jboss.tools.ws.jaxrs.core.jdt.JdtUtils;
import org.jboss.tools.ws.jaxrs.core.metamodel.EnumElementKind;
import org.jboss.tools.ws.jaxrs.core.metamodel.IJaxrsEndpoint;
import org.jboss.tools.ws.jaxrs.core.metamodel.IJaxrsHttpMethod;
import org.jboss.tools.ws.jaxrs.core.metamodel.JaxrsEndpointDelta;
import org.jboss.tools.ws.jaxrs.core.metamodel.JaxrsMetamodelDelta;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class JaxrsMetamodelChangedProcessorTestCase extends AbstractCommonTestCase {

	private JaxrsMetamodel metamodel;

	private final JaxrsMetamodelChangedProcessor delegate = new JaxrsMetamodelChangedProcessor();

	private static final IProgressMonitor progressMonitor = new NullProgressMonitor();

	@Before
	public void setup() throws CoreException {
		metamodel = spy(JaxrsMetamodel.create(javaProject));
	}

	private JaxrsJavaApplication createJavaApplication(String typeName) throws CoreException, JavaModelException {
		final IType applicationType = getType(typeName, javaProject);
		final Annotation appPathAnnotation = resolveAnnotation(applicationType, APPLICATION_PATH.qualifiedName);
		final JaxrsJavaApplication application = new JaxrsJavaApplication(applicationType,
				appPathAnnotation, true, metamodel);
		metamodel.add(application);
		return application;
	}

	private JaxrsWebxmlApplication createWebxmlApplication(String typeName, String applicationPath) throws CoreException, JavaModelException {
		final JaxrsWebxmlApplication application = new JaxrsWebxmlApplication(typeName,
				applicationPath, WtpUtils.getWebDeploymentDescriptor(project), metamodel);
		metamodel.add(application);
		return application;
	}
	
	private JaxrsResource createResource(String typeName) throws CoreException, JavaModelException {
		final IType resourceType = getType(typeName, javaProject);
		final JaxrsResource resource = new JaxrsResource.Builder(resourceType, metamodel).pathTemplate(
				resolveAnnotation(resourceType, PATH.qualifiedName)).build();
		metamodel.add(resource);
		return resource;
	}

	private JaxrsResourceMethod createResourceMethod(String methodName, JaxrsResource parentResource,
			EnumJaxrsClassname httpMethodElement) throws CoreException, JavaModelException {
		final IType javaType = parentResource.getJavaElement();
		final ICompilationUnit compilationUnit = javaType.getCompilationUnit();
		final IMethod javaMethod = getMethod(javaType, methodName);
		final JavaMethodSignature methodSignature = JdtUtils.resolveMethodSignature(javaMethod,
				JdtUtils.parse(compilationUnit, progressMonitor));

		final Builder builder = new JaxrsResourceMethod.Builder(javaMethod, (JaxrsResource) parentResource, metamodel)
				.pathTemplate(resolveAnnotation(javaMethod, PATH.qualifiedName)).returnType(methodSignature.getReturnedType());
		if (httpMethodElement != null) {
			builder.httpMethod(resolveAnnotation(javaMethod, httpMethodElement.qualifiedName));
		}
		
		for (Entry<String, JavaMethodParameter> methodParameterEntry : methodSignature.getMethodParameters().entrySet()) {
			JavaMethodParameter parameter = methodParameterEntry.getValue();
			builder.methodParameter(parameter);
		}
		final JaxrsResourceMethod resourceMethod = builder.build();
		metamodel.add(resourceMethod);
		return resourceMethod;
	}

	private JaxrsHttpMethod createHttpMethod(String qualifiedName) throws JavaModelException, CoreException {
		final IType type = getType(qualifiedName, javaProject);
		final Annotation httpAnnotation = resolveAnnotation(type, HTTP_METHOD.qualifiedName);
		final JaxrsHttpMethod httpMethod = new JaxrsHttpMethod.Builder(type, metamodel).httpMethod(httpAnnotation).build();
		metamodel.add(httpMethod);
		return httpMethod;
	}
	
	private JaxrsEndpoint createEndpoint(JaxrsMetamodel metamodel, JaxrsHttpMethod httpMethod, JaxrsResourceMethod... resourceMethods) {
		JaxrsEndpoint endpoint = new JaxrsEndpoint(metamodel, httpMethod, new LinkedList<JaxrsResourceMethod>(
				Arrays.asList(resourceMethods)));
		metamodel.add(endpoint);
		return endpoint;
	}

	private JaxrsEndpoint createEndpoint(JaxrsHttpMethod httpMethod, JaxrsResourceMethod... resourceMethods) {
		JaxrsEndpoint endpoint = new JaxrsEndpoint(this.metamodel, httpMethod, new LinkedList<JaxrsResourceMethod>(
				Arrays.asList(resourceMethods)));
		metamodel.add(endpoint);
		return endpoint;
	}

	private List<JaxrsEndpointDelta> processEvent(JaxrsElementDelta affectedElement,
			IProgressMonitor progressMonitor) throws CoreException {
		JaxrsMetamodelDelta affectedMetamodel = new JaxrsMetamodelDelta(metamodel, CHANGED);
		affectedMetamodel.add(affectedElement);
		delegate.processAffectedMetamodel(affectedMetamodel, progressMonitor);
		return affectedMetamodel.getAffectedEndpoints();
	}

	@Test
	public void shouldConstructSimpleEndpoint() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		customerResource.addOrUpdateAnnotation(resolveAnnotation(customerResource.getJavaElement(), CONSUMES.qualifiedName));
		customerResource.addOrUpdateAnnotation(resolveAnnotation(customerResource.getJavaElement(), PRODUCES.qualifiedName));
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomerAsVCard", customerResource,
				GET);
		customerResourceMethod.addOrUpdateAnnotation(resolveAnnotation(customerResourceMethod.getJavaElement(),
				PRODUCES.qualifiedName));
		// operation
		JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		// verifications
		assertThat(endpoint.getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// @produces and @consumes annotations were explicitly declared
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/xml")));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("text/x-vcard")));
	}

	@Test
	public void shouldConstructEndpointFromSubresource() throws CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource producLocatorResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsResourceMethod productLocatorMethod = createResourceMethod("getProductResourceLocator",
				producLocatorResource, GET);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod subresourceMethod = createResourceMethod("getProduct", bookResource, GET);
		// operation
		JaxrsEndpoint endpoint = createEndpoint(httpMethod, productLocatorMethod, subresourceMethod);
		// verifications
		assertThat(endpoint.getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(endpoint.getUriPathTemplate(), equalTo("/products/{productType}/{id}"));
		// @produces and @consumes annotations were not declared in the setup(),
		// default values should be set
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("*/*")));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("*/*")));
	}

	@Test
	public void shouldConstructEndpointWithQueryParams() throws CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomers", customerResource,
				GET);
		// operation
		JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		// verifications
		assertThat(endpoint.getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers?start={start:int}&size={size:int=2}"));
	}

	@Test
	public void shoudCreateEndpointWhenAddingResourceMethodInRootResource() throws CoreException {
		// pre-conditions
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsBaseElement customerResourceMethod = createResourceMethod("getCustomers", customerResource,
				GET);
		// operation
		JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, ADDED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(ADDED));
	}

	@Test
	public void shoudCreateEndpointWhenAddingSubresourceMethodInRootResource() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsBaseElement customerSubresourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		// operation
		JaxrsElementDelta event = new JaxrsElementDelta(customerSubresourceMethod, ADDED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(ADDED));
	}

	@Test
	public void shoudCreateEndpointWhenAddingSubresourceLocatorMethodInRootResource() throws JavaModelException,
			CoreException {
		// pre-conditions
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		createResourceMethod("getProduct", bookResource, GET);
		// createEndpoint(httpMethod, bookResourceMethod);
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		createResourceMethod("getProduct", gameResource, GET);
		// createEndpoint(httpMethod, gameResourceMethod);

		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsBaseElement productResourceLocatorMethod = createResourceMethod("getProductResourceLocator",
				productResourceLocator, null);
		// operation
		JaxrsElementDelta event = new JaxrsElementDelta(productResourceLocatorMethod, ADDED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		assertThat(changes.get(0).getDeltaKind(), equalTo(ADDED));
		assertThat(changes.get(1).getDeltaKind(), equalTo(ADDED));
	}

	@Test
	public void shoudCreateEndpointWhenAddingResourceMethodInSubresource() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		createResourceMethod("getProductResourceLocator", productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsBaseElement bookResourceMethod = createResourceMethod("getAllProducts", bookResource, GET);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(bookResourceMethod, ADDED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(ADDED));
	}

	@Test
	public void shoudCreateEndpointWhenChangingSubresourceLocatorMethodIntoSubresourceMethod()
			throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerSubresourceMethod = createResourceMethod("getCustomer", customerResource,
				null);
		assertThat(customerSubresourceMethod.getElementKind(), equalTo(EnumElementKind.SUBRESOURCE_LOCATOR));
		// operation
		Annotation httpAnnotation = resolveAnnotation(customerSubresourceMethod.getJavaElement(), GET.qualifiedName);
		final int flags = customerSubresourceMethod.addOrUpdateAnnotation(httpAnnotation);
		JaxrsElementDelta event = new JaxrsElementDelta(customerSubresourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(ADDED));
	}

	@Test
	public void shoudCreateEndpointWhenAddingSubresourceMethodInSubresource() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		createResourceMethod("getProductResourceLocator", productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsBaseElement bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(bookResourceMethod, ADDED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(ADDED));
	}

	@Test
	@Ignore("deferred for now")
	public void shoudCreateEndpointWhenAddingSubresourceLocatorMethodInSubresource() {
	}

	@Test
	public void shoudChangeUriPathTemplateWhenAddingApplication() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		final JaxrsJavaApplication application = createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsElementDelta event = new JaxrsElementDelta(application, ADDED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(CHANGED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(changes.get(0).getEndpoint().getUriPathTemplate(), equalTo("/app/customers/{id}"));
	}
	
	@Test
	public void shoudChangeUriPathTemplateWhenRemovingApplicationType() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsJavaApplication application = createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/app/customers/{id}"));
		// operation
		metamodel.remove(application);
		final JaxrsElementDelta event = new JaxrsElementDelta(application, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(CHANGED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(changes.get(0).getEndpoint().getUriPathTemplate(), equalTo("/customers/{id}"));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenRemovingApplicationPathAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsJavaApplication application = createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/app/customers/{id}"));
		// operation
		final Annotation appPathAnnotation = application.getAnnotation(EnumJaxrsClassname.APPLICATION_PATH.qualifiedName);
		application.removeAnnotation(appPathAnnotation);
		final JaxrsElementDelta event = new JaxrsElementDelta(application, CHANGED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(CHANGED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(changes.get(0).getEndpoint().getUriPathTemplate(), equalTo("/customers/{id}"));
	}
	
	@Test
	public void shoudChangeUriPathTemplateWhenAddingResourcePathAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomers", customerResource,
				GET);
		final JaxrsEndpoint fakeEndpoint = createEndpoint(httpMethod, customerResourceMethod);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, CHANGED, F_ELEMENT_KIND
				+ F_PATH_VALUE);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		assertThat(changes.get(0).getDeltaKind(), equalTo(REMOVED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) fakeEndpoint));
		assertThat(changes.get(1).getDeltaKind(), equalTo(ADDED));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenAddingMethodPathAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final Annotation annotation = resolveAnnotation(customerResourceMethod.getJavaElement(), PATH.qualifiedName);
		customerResourceMethod.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers"));
		// operation
		customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED,
				F_PATH_VALUE);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/customers/{id}"));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenChangingApplicationPathAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsJavaApplication application = createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(metamodel, httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/app/customers/{id}"));
		// operation
		final Annotation annotation = changeAnnotation(application.getJavaElement(), APPLICATION_PATH.qualifiedName, "/foo");
		int flags = application.addOrUpdateAnnotation(annotation);
		final JaxrsElementDelta event = new JaxrsElementDelta(application, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/foo/customers/{id}"));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenSwitchingToWebxmlCoreApplication() throws JavaModelException, CoreException {
		// pre-conditions
		createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(metamodel, httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/app/customers/{id}"));
		// operation
		final JaxrsWebxmlApplication webxmlApplication = createWebxmlApplication("javax.ws.rs.core.Application", "/foo");
		final JaxrsElementDelta event = new JaxrsElementDelta(webxmlApplication, ADDED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/foo/customers/{id}"));
	}
	
	@Test
	public void shoudChangeUriPathTemplateWhenSwitchingBackToJavaApplication() throws JavaModelException, CoreException {
		// pre-conditions
		createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsWebxmlApplication webxmlApplication = createWebxmlApplication("javax.ws.rs.core.Application", "/foo");
		final JaxrsEndpoint endpoint = createEndpoint(metamodel, httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/foo/customers/{id}"));
		// operation
		metamodel.remove(webxmlApplication);
		final JaxrsElementDelta event = new JaxrsElementDelta(webxmlApplication, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/app/customers/{id}"));
	}
	
	@Test
	public void shoudChangeUriPathTemplateWhenOverridingApplication() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsJavaApplication javaApplication = createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(metamodel, httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/app/customers/{id}"));
		// operation
		final JaxrsWebxmlApplication webxmlApplication = createWebxmlApplication(javaApplication.getJavaClassName(), "/foo");
		List<JaxrsEndpointDelta> changes = processEvent(new JaxrsElementDelta(webxmlApplication, ADDED), progressMonitor);
		assertThat(changes.size(), equalTo(0));
		// (at the same time, the JavaApplication is changed since this is an override)
		javaApplication.setApplicationPathOverride("/foo");
		changes = processEvent(new JaxrsElementDelta(javaApplication, CHANGED), progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/foo/customers/{id}"));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenUnoverridingApplication() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsJavaApplication javaApplication = createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsWebxmlApplication webxmlApplication = createWebxmlApplication(javaApplication.getJavaClassName(), "/foo");
		javaApplication.setApplicationPathOverride("/foo");
		final JaxrsEndpoint endpoint = createEndpoint(metamodel, httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/foo/customers/{id}"));
		// operation
		List<JaxrsEndpointDelta> changes = processEvent(new JaxrsElementDelta(webxmlApplication, REMOVED), progressMonitor);
		assertThat(changes.size(), equalTo(1)); // FAKE change...
		// (at the same time, the JavaApplication is changed since this is an override)
		javaApplication.unsetApplicationPathOverride();
		changes = processEvent(new JaxrsElementDelta(javaApplication, CHANGED), progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/app/customers/{id}"));
	}
	
	@Test
	public void shoudChangeUriPathTemplateWhenChangingResourcePathAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		final Annotation annotation = changeAnnotation(customerResource.getJavaElement(), PATH.qualifiedName, "/foo");
		customerResource.addOrUpdateAnnotation(annotation);
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, CHANGED, F_PATH_VALUE);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/foo/{id}"));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenChangingMethodPathAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		final Annotation annotation = changeAnnotation(customerResourceMethod.getJavaElement(), PATH.qualifiedName, "{foo}");
		final int flags = customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(change.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change.getEndpoint().getUriPathTemplate(), equalTo("/customers/{foo}"));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenRemovingResourcePathAnnotationAndMatchingSubresourceLocatorFound()
			throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		// the subresource locator that will match the resourcemethod when the
		// rootresource becomes a subresource
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		createResourceMethod("getProductResourceLocator", productResourceLocator, null);
		// the root resource that will become a subresource
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		final Annotation annotation = resolveAnnotation(customerResource.getJavaElement(), PATH.qualifiedName);
		final int flags = customerResource.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		final JaxrsEndpointDelta change1 = changes.get(0);
		assertThat(change1.getDeltaKind(), equalTo(REMOVED));
		assertThat(change1.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		final JaxrsEndpointDelta change2 = changes.get(1);
		assertThat(change2.getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(change2.getEndpoint().getUriPathTemplate(), equalTo("/products/{productType}/{id}"));
	}

	@Test
	public void shoudChangeHttpVerbWhenChangingHttpMethodAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = createHttpMethod("org.jboss.tools.ws.jaxrs.sample.services.FOO");
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		int flags = httpMethod
				.addOrUpdateAnnotation(changeAnnotation(httpMethod.getJavaElement(), HTTP_METHOD.qualifiedName, "BAR"));
		final JaxrsElementDelta event = new JaxrsElementDelta(httpMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(CHANGED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(changes.get(0).getEndpoint().getHttpMethod().getHttpVerb(), equalTo("BAR"));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenRemovingMetamodelApplication() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsJavaApplication application = createJavaApplication("org.jboss.tools.ws.jaxrs.sample.services.RestApplication");
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(metamodel, httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/app/customers/{id}"));
		// operation : no 'application' left in the metamodel
		metamodel.remove(application);
		final JaxrsElementDelta event = new JaxrsElementDelta(application, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(CHANGED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(changes.get(0).getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(changes.get(0).getEndpoint().getUriPathTemplate(), equalTo("/customers/{id}"));
		assertThat(metamodel.getAllApplications().size(), equalTo(0));
	}

	@Test
	public void shoudChangeUriPathTemplateWhenRemovingMethodPathAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		final Annotation annotation = resolveAnnotation(customerResourceMethod.getJavaElement(), PATH.qualifiedName);
		final int flags = customerResourceMethod.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		assertThat(changes.get(0).getDeltaKind(), equalTo(REMOVED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(changes.get(1).getDeltaKind(), equalTo(ADDED));
		assertThat(changes.get(1).getEndpoint().getHttpMethod(), equalTo((IJaxrsHttpMethod) httpMethod));
		assertThat(changes.get(1).getEndpoint().getUriPathTemplate(), equalTo("/customers"));
	}

	@Test
	public void shoudChangeConsumedMediatypesWhenAddingResourceAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("createCustomer", customerResource,
				POST);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("*/*")));
		// operation
		final int flags = customerResource.addOrUpdateAnnotation(resolveAnnotation(customerResource.getJavaElement(),
				CONSUMES.qualifiedName));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeConsumedMediatypesWhenAddingResourceMethodAnnotation() throws JavaModelException,
			CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("createCustomer", customerResource,
				POST);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("*/*")));
		// operation
		final int flags = customerResourceMethod.addOrUpdateAnnotation(resolveAnnotation(
				customerResourceMethod.getJavaElement(), CONSUMES.qualifiedName));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeConsumedMediatypesWhenChangingResourceMethodAnnotation() throws JavaModelException,
			CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("createCustomer", customerResource,
				POST);
		final Annotation annotation = changeAnnotation(customerResourceMethod.getJavaElement(), CONSUMES.qualifiedName,
				"application/foo");
		customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResourceMethod.addOrUpdateAnnotation(resolveAnnotation(customerResourceMethod.getJavaElement(),
				CONSUMES.qualifiedName));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeConsumedMediatypesWhenChangingResourceAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final Annotation annotation = changeAnnotation(customerResource.getJavaElement(), CONSUMES.qualifiedName,
				"application/foo");
		customerResource.addOrUpdateAnnotation(annotation);
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("createCustomer", customerResource,
				POST);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResource.addOrUpdateAnnotation(resolveAnnotation(customerResource.getJavaElement(),
				CONSUMES.qualifiedName));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeConsumedMediatypesWhenRemovingMethodAnnotationWithResourceDefault()
			throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		customerResource.addOrUpdateAnnotation(changeAnnotation(customerResource.getJavaElement(), CONSUMES.qualifiedName,
				"application/xml"));
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("createCustomer", customerResource,
				POST);
		final Annotation annotation = changeAnnotation(customerResourceMethod.getJavaElement(), CONSUMES.qualifiedName,
				"application/foo");
		customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResourceMethod.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeConsumedMediatypesWhenRemovingMethodAnnotationWithoutResourceDefault()
			throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("createCustomer", customerResource,
				POST);
		final Annotation annotation = changeAnnotation(customerResourceMethod.getJavaElement(), CONSUMES.qualifiedName,
				"application/foo");
		customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResourceMethod.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("*/*")));
	}

	@Test
	public void shoudChangeProducedMediatypesWhenAddingResourceAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomerAsVCard", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("*/*")));
		// operation
		final int flags = customerResource.addOrUpdateAnnotation(changeAnnotation(customerResource.getJavaElement(),
				PRODUCES.qualifiedName, "application/xml"));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeProducedMediatypesWhenAddingResourceMethodAnnotation() throws JavaModelException,
			CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomerAsVCard", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("*/*")));
		// operation
		final int flags = customerResourceMethod.addOrUpdateAnnotation(resolveAnnotation(
				customerResourceMethod.getJavaElement(), PRODUCES.qualifiedName));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("text/x-vcard")));
	}

	@Test
	public void shoudChangeProducedMediatypesWhenChangingResourceAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final Annotation annotation = changeAnnotation(customerResource.getJavaElement(), PRODUCES.qualifiedName,
				"application/foo");
		customerResource.addOrUpdateAnnotation(annotation);
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomerAsVCard", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResource.addOrUpdateAnnotation(changeAnnotation(customerResource.getJavaElement(),
				PRODUCES.qualifiedName, "application/xml"));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeProducedMediatypesWhenChangingResourceMethodAnnotation() throws JavaModelException,
			CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomerAsVCard", customerResource,
				POST);
		final Annotation annotation = changeAnnotation(customerResourceMethod.getJavaElement(), PRODUCES.qualifiedName,
				"application/foo");
		customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResourceMethod.addOrUpdateAnnotation(resolveAnnotation(customerResourceMethod.getJavaElement(),
				PRODUCES.qualifiedName));
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("text/x-vcard")));
	}

	@Test
	public void shoudChangeProducedMediatypesWhenRemovingMethodAnnotationWithResourceDefault()
			throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		customerResource.addOrUpdateAnnotation(changeAnnotation(customerResource.getJavaElement(), PRODUCES.qualifiedName,
				"application/xml"));
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomerAsVCard", customerResource,
				POST);
		final Annotation annotation = changeAnnotation(customerResourceMethod.getJavaElement(), PRODUCES.qualifiedName,
				"application/foo");
		customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResourceMethod.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("application/xml")));
	}

	@Test
	public void shoudChangeProducedMediatypesWhenRemovingMethodAnnotationWithoutResourceDefault()
			throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.POST;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("createCustomer", customerResource,
				POST);
		final Annotation annotation = changeAnnotation(customerResourceMethod.getJavaElement(), CONSUMES.qualifiedName,
				"application/foo");
		customerResourceMethod.addOrUpdateAnnotation(annotation);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getConsumedMediaTypes(), equalTo(Arrays.asList("application/foo")));
		// operation
		int flags = customerResourceMethod.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(CHANGED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
		assertThat(endpoint.getProducedMediaTypes(), equalTo(Arrays.asList("*/*")));
	}

	@Test
	public void shoudRemoveEndpointWhenRemovingHttpMethodAnnotation() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = createHttpMethod("org.jboss.tools.ws.jaxrs.sample.services.FOO");
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(httpMethod, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(REMOVED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
	}

	@Test
	public void shoudRemoveEndpointWhenRemovingResourcePathAnnotationAndMatchingSubresourceLocatorNotFound()
			throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);

		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		assertThat(endpoint.getUriPathTemplate(), equalTo("/customers/{id}"));
		// operation
		final Annotation annotation = resolveAnnotation(customerResource.getJavaElement(), PATH.qualifiedName);
		final int flags = customerResource.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(REMOVED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
	}

	@Test
	public void shoudRemoveEndpointsWhenRemovingRootResource() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod1 = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint1 = createEndpoint(httpMethod, customerResourceMethod1);
		final JaxrsResourceMethod customerResourceMethod2 = createResourceMethod("getCustomers", customerResource,
				GET);
		final JaxrsEndpoint endpoint2 = createEndpoint(httpMethod, customerResourceMethod2);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResource, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		for (JaxrsEndpointDelta change : changes) {
			assertThat(change.getDeltaKind(), equalTo(REMOVED));
			assertThat(change.getEndpoint(), isOneOf((IJaxrsEndpoint) endpoint1, (IJaxrsEndpoint) endpoint2));
		}
	}

	@Test
	public void shoudRemoveEndpointsWhenRemovingSubresource() throws JavaModelException, CoreException {
		// pre-conditions
		JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsResourceMethod productResourceLocatorMethod = createResourceMethod("getProductResourceLocator",
				productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		final JaxrsEndpoint bookEndpoint = createEndpoint(httpMethod, productResourceLocatorMethod, bookResourceMethod);
		// adding an extra endpoint that shouldn't be affected
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		final JaxrsResourceMethod gameResourceMethod = createResourceMethod("getProduct", gameResource, GET);
		createEndpoint(httpMethod, gameResourceMethod);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(bookResource, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(REMOVED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) bookEndpoint));
	}

	@Test
	public void shoudRemoveEndpointsWhenRemovingHttpMethod() throws JavaModelException, CoreException {
		// pre-conditions
		JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		createResourceMethod("getProductResourceLocator", productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		final JaxrsEndpoint bookEndpoint = createEndpoint(httpMethod, bookResourceMethod);
		// adding an extra endpoint that shouldn't be affected
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		final JaxrsResourceMethod gameResourceMethod = createResourceMethod("getProduct", gameResource, GET);
		createEndpoint(httpMethod, gameResourceMethod);
		final Annotation httpAnnotation = bookResourceMethod.getHttpMethodAnnotation();
		final int flags = bookResourceMethod.removeAnnotation(httpAnnotation.getJavaAnnotation().getHandleIdentifier());
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(bookResourceMethod, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(REMOVED));
		assertThat(changes.get(0).getEndpoint(), equalTo((IJaxrsEndpoint) bookEndpoint));
	}

	@Test
	public void shoudAddEndpointsWhenChangingSubresourceLocatorReturnType() throws JavaModelException, CoreException {
		// pre-conditions
		JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsResourceMethod productResourceLocatorMethod = createResourceMethod("getProductResourceLocator",
				productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		productResourceLocatorMethod.update(new JavaMethodSignature(productResourceLocatorMethod.getJavaElement(),
				bookResource.getJavaElement(), productResourceLocatorMethod.getJavaMethodParameters()));
		createEndpoint(httpMethod, productResourceLocatorMethod, bookResourceMethod);
		// adding an extra subresource that should be affected later
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		final JaxrsBaseElement gameResourceMethod = createResourceMethod("getProduct", gameResource, GET);
		assertThat(metamodel.getAllEndpoints().size(), equalTo(1));
		// operation
		final IType objectType = JdtUtils.resolveType(Object.class.getName(), javaProject, progressMonitor);
		int flags = productResourceLocatorMethod.update(new JavaMethodSignature(productResourceLocatorMethod
				.getJavaElement(), objectType, productResourceLocatorMethod.getJavaMethodParameters()));
		final JaxrsElementDelta event = new JaxrsElementDelta(productResourceLocatorMethod, CHANGED,
				flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(ADDED));
		assertThat(changes.get(0).getEndpoint().getResourceMethods().contains(gameResourceMethod), is(true));
		assertThat(metamodel.getAllEndpoints().size(), equalTo(2));
	}

	@Test
	public void shoudRemoveEndpointsWhenChangingSubresourceLocatorReturnType() throws JavaModelException, CoreException {
		// pre-conditions
		JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsResourceMethod productResourceLocatorMethod = createResourceMethod("getProductResourceLocator",
				productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		createEndpoint(httpMethod, productResourceLocatorMethod, bookResourceMethod);
		// adding an extra subresource that should be affected later
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		final JaxrsResourceMethod gameResourceMethod = createResourceMethod("getProduct", gameResource, GET);
		createEndpoint(httpMethod, productResourceLocatorMethod, gameResourceMethod);
		assertThat(metamodel.getAllEndpoints().size(), equalTo(2));
		// operation
		final IType bookResourceType = bookResource.getJavaElement();
		int flags = productResourceLocatorMethod.update(new JavaMethodSignature(productResourceLocatorMethod
				.getJavaElement(), bookResourceType, productResourceLocatorMethod.getJavaMethodParameters()));
		final JaxrsElementDelta event = new JaxrsElementDelta(productResourceLocatorMethod, CHANGED,
				flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		assertThat(changes.get(0).getDeltaKind(), equalTo(REMOVED));
		assertThat(changes.get(0).getEndpoint().getResourceMethods().contains(gameResourceMethod), is(true));
		assertThat(metamodel.getAllEndpoints().size(), equalTo(1));

	}

	@Test
	public void shoudRemoveEndpointsWhenRemovingSubresourceLocatorResource() throws JavaModelException, CoreException {
		// pre-conditions
		JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsResourceMethod productResourceLocatorMethod = createResourceMethod("getProductResourceLocator",
				productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		final JaxrsEndpoint bookEndpoint = createEndpoint(httpMethod, productResourceLocatorMethod, bookResourceMethod);
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		final JaxrsResourceMethod gameResourceMethod = createResourceMethod("getProduct", gameResource, GET);
		final JaxrsEndpoint gameEndpoint = createEndpoint(httpMethod, productResourceLocatorMethod, gameResourceMethod);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(productResourceLocator, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		for (JaxrsEndpointDelta change : changes) {
			assertThat(change.getDeltaKind(), equalTo(REMOVED));
			assertThat(change.getEndpoint(), isOneOf((IJaxrsEndpoint) bookEndpoint, (IJaxrsEndpoint) gameEndpoint));
		}
	}

	@Test
	public void shoudRemoveEndpointWhenRemovingResourceMethodInRootResource() throws JavaModelException, CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomers", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(REMOVED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
	}

	@Test
	public void shoudRemoveEndpointWhenRemovingSubresourceMethodInRootResource() throws JavaModelException,
			CoreException {
		// pre-conditions
		final JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource customerResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.CustomerResource");
		final JaxrsResourceMethod customerResourceMethod = createResourceMethod("getCustomer", customerResource,
				GET);
		final JaxrsEndpoint endpoint = createEndpoint(httpMethod, customerResourceMethod);
		// operation
		final Annotation annotation = resolveAnnotation(customerResourceMethod.getJavaElement(), PATH.qualifiedName);
		final int flags = customerResourceMethod.removeAnnotation(annotation.getJavaAnnotation().getHandleIdentifier());
		final JaxrsElementDelta event = new JaxrsElementDelta(customerResourceMethod, REMOVED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(1));
		final JaxrsEndpointDelta change = changes.get(0);
		assertThat(change.getDeltaKind(), equalTo(REMOVED));
		assertThat(change.getEndpoint(), equalTo((IJaxrsEndpoint) endpoint));
	}

	@Test
	public void shoudRemoveEndpointWhenRemovingSubresourceLocatorMethod() throws JavaModelException, CoreException {
		// pre-conditions
		JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsResourceMethod productResourceLocatorMethod = createResourceMethod("getProductResourceLocator",
				productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		final JaxrsEndpoint bookEndpoint = createEndpoint(httpMethod, productResourceLocatorMethod, bookResourceMethod);
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		final JaxrsResourceMethod gameResourceMethod = createResourceMethod("getProduct", gameResource, GET);
		final JaxrsEndpoint gameEndpoint = createEndpoint(httpMethod, productResourceLocatorMethod, gameResourceMethod);
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(productResourceLocatorMethod, REMOVED);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		for (JaxrsEndpointDelta change : changes) {
			assertThat(change.getDeltaKind(), equalTo(REMOVED));
			assertThat(change.getEndpoint(), isOneOf((IJaxrsEndpoint) bookEndpoint, (IJaxrsEndpoint) gameEndpoint));
		}
	}

	@Test
	public void shoudRemoveEndpointWhenSubresourceLocatorRootResourceBecomesSubresource() throws JavaModelException,
			CoreException {
		// pre-conditions
		JaxrsHttpMethod httpMethod = JaxrsBuiltinHttpMethod.GET;
		final JaxrsResource productResourceLocator = createResource("org.jboss.tools.ws.jaxrs.sample.services.ProductResourceLocator");
		final JaxrsResourceMethod productResourceLocatorMethod = createResourceMethod("getProductResourceLocator",
				productResourceLocator, null);
		final JaxrsResource bookResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.BookResource");
		final JaxrsResourceMethod bookResourceMethod = createResourceMethod("getProduct", bookResource, GET);
		final JaxrsEndpoint bookEndpoint = createEndpoint(httpMethod, productResourceLocatorMethod, bookResourceMethod);
		final JaxrsResource gameResource = createResource("org.jboss.tools.ws.jaxrs.sample.services.GameResource");
		final JaxrsResourceMethod gameResourceMethod = createResourceMethod("getProduct", gameResource, GET);
		final JaxrsEndpoint gameEndpoint = createEndpoint(httpMethod, productResourceLocatorMethod, gameResourceMethod);
		final Annotation productResourceLocatorPathAnnotation = resolveAnnotation(productResourceLocator.getJavaElement(),
				PATH.qualifiedName);
		final int flags = productResourceLocator.removeAnnotation(productResourceLocatorPathAnnotation
				.getJavaAnnotation().getHandleIdentifier());
		// operation
		final JaxrsElementDelta event = new JaxrsElementDelta(productResourceLocator, CHANGED, flags);
		final List<JaxrsEndpointDelta> changes = processEvent(event, progressMonitor);
		// verifications
		assertThat(changes.size(), equalTo(2));
		for (JaxrsEndpointDelta change : changes) {
			assertThat(change.getDeltaKind(), equalTo(REMOVED));
			assertThat(change.getEndpoint(), isOneOf((IJaxrsEndpoint) bookEndpoint, (IJaxrsEndpoint) gameEndpoint));
		}
	}
	
	
}
