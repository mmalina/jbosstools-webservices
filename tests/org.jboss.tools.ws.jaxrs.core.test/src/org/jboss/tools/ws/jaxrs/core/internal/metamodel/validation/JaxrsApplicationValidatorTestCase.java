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
package org.jboss.tools.ws.jaxrs.core.internal.metamodel.validation;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.jboss.tools.ws.jaxrs.core.internal.metamodel.validation.MarkerUtils.deleteJaxrsMarkers;
import static org.jboss.tools.ws.jaxrs.core.internal.metamodel.validation.MarkerUtils.findJaxrsMarkers;
import static org.jboss.tools.ws.jaxrs.core.internal.metamodel.validation.MarkerUtils.hasPreferenceKey;
import static org.jboss.tools.ws.jaxrs.core.preferences.JaxrsPreferences.APPLICATION_NO_OCCURRENCE_FOUND;
import static org.jboss.tools.ws.jaxrs.core.preferences.JaxrsPreferences.APPLICATION_TOO_MANY_OCCURRENCES;
import static org.jboss.tools.ws.jaxrs.core.preferences.JaxrsPreferences.JAVA_APPLICATION_INVALID_TYPE_HIERARCHY;
import static org.jboss.tools.ws.jaxrs.core.preferences.JaxrsPreferences.JAVA_APPLICATION_MISSING_APPLICATION_PATH_ANNOTATION;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.wst.validation.ReporterHelper;
import org.eclipse.wst.validation.internal.core.ValidationException;
import org.eclipse.wst.validation.internal.provisional.core.IReporter;
import org.jboss.tools.common.validation.ContextValidationHelper;
import org.jboss.tools.common.validation.IProjectValidationContext;
import org.jboss.tools.common.validation.ValidatorManager;
import org.jboss.tools.common.validation.internal.ProjectValidationContext;
import org.jboss.tools.ws.jaxrs.core.WorkbenchUtils;
import org.jboss.tools.ws.jaxrs.core.builder.AbstractMetamodelBuilderTestCase;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsBaseElement;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsJavaApplication;
import org.jboss.tools.ws.jaxrs.core.internal.metamodel.domain.JaxrsWebxmlApplication;
import org.jboss.tools.ws.jaxrs.core.internal.utils.WtpUtils;
import org.jboss.tools.ws.jaxrs.core.jdt.Annotation;
import org.jboss.tools.ws.jaxrs.core.jdt.EnumJaxrsClassname;
import org.jboss.tools.ws.jaxrs.core.jdt.JdtUtils;
import org.jboss.tools.ws.jaxrs.core.metamodel.EnumElementKind;
import org.jboss.tools.ws.jaxrs.core.metamodel.IJaxrsApplication;
import org.junit.Test;

/**
 * @author Xi
 * 
 */
@SuppressWarnings("restriction")
public class JaxrsApplicationValidatorTestCase extends AbstractMetamodelBuilderTestCase {

	private final IReporter reporter = new ReporterHelper(new NullProgressMonitor());
	private final ContextValidationHelper validationHelper = new ContextValidationHelper();
	private final IProjectValidationContext context = new ProjectValidationContext();
	private final ValidatorManager validatorManager = new ValidatorManager();

	/**
	 * Creates a web.xml based JAX-RS Application element
	 * 
	 * @param applicationPath
	 * @return
	 * @throws JavaModelException
	 */
	private JaxrsWebxmlApplication createWebxmlApplication(final String applicationClassName,
			final String applicationPath) throws JavaModelException {
		final IResource webDeploymentDescriptor = WtpUtils.getWebDeploymentDescriptor(project);
		return new JaxrsWebxmlApplication(applicationClassName, applicationPath, webDeploymentDescriptor, metamodel);
	}

	@Test
	public void shouldNotReportProblemIfOneJavaApplicationExists() throws CoreException, ValidationException {
		// preconditions
		final List<IJaxrsApplication> applications = metamodel.getAllApplications();
		for (IJaxrsApplication application : applications) {
			if (application.getElementKind() == EnumElementKind.APPLICATION_WEBXML) {
				metamodel.remove((JaxrsBaseElement) application);
			}
		}
		deleteJaxrsMarkers(project);
		assertThat(metamodel.getAllApplications().size(), equalTo(1));
		assertThat(metamodel.getApplication().getElementKind(), equalTo(EnumElementKind.APPLICATION_JAVA));
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] markers = findJaxrsMarkers(project);
		assertThat(markers.length, equalTo(0));
	}
	
	@Test
	public void shouldNotReportProblemIfOneWebxmlApplicationExists() throws CoreException, ValidationException {
		// preconditions
		final List<IJaxrsApplication> applications = metamodel.getAllApplications();
		for (IJaxrsApplication application : applications) {
			if (application.getElementKind() == EnumElementKind.APPLICATION_JAVA) {
				metamodel.remove((JaxrsBaseElement) application);
			}
		}
		assertThat(metamodel.getAllApplications().size(), equalTo(1));
		assertThat(metamodel.getApplication().getElementKind(), equalTo(EnumElementKind.APPLICATION_WEBXML));
		deleteJaxrsMarkers(project);
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] merkers = findJaxrsMarkers(project);
		assertThat(merkers.length, equalTo(0));
	}

	@Test
	public void shouldReportProblemOnProjectIfNoApplicationExists() throws CoreException, ValidationException {
		// preconditions
		final List<IJaxrsApplication> applications = metamodel.getAllApplications();
		for (IJaxrsApplication application : applications) {
			metamodel.remove((JaxrsBaseElement) application);
		}
		deleteJaxrsMarkers(project);
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] markers = findJaxrsMarkers(project);
		assertThat(markers.length, equalTo(1));
		assertThat(markers, hasPreferenceKey(APPLICATION_NO_OCCURRENCE_FOUND));
	}

	@Test
	public void shouldReportProblemOnApplicationsIfMultipleOnesExist() throws CoreException, ValidationException {
		// preconditions
		final List<IJaxrsApplication> applications = metamodel.getAllApplications();
		assertThat(applications, hasSize(2));
		deleteJaxrsMarkers(project);
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] markers = findJaxrsMarkers(project);
		assertThat(markers.length, equalTo(0));
		for (IJaxrsApplication application : metamodel.getAllApplications()) {
			final IMarker[] appMarkers = findJaxrsMarkers(application);
			assertThat(appMarkers, hasPreferenceKey(APPLICATION_TOO_MANY_OCCURRENCES));
			assertThat(appMarkers.length, equalTo(1));
		}
	}

	@Test
	public void shouldReportProblemOnJavaApplicationIfMissingApplicationPathAnnotationWithoutOverride()
			throws CoreException, ValidationException {
		// preconditions
		final List<IJaxrsApplication> applications = metamodel.getAllApplications();
		assertThat(applications, hasSize(2));
		deleteJaxrsMarkers(project);
		JaxrsJavaApplication javaApplication = null;
		// remove web.xml-based application and remove @ApplicationPath annotation on java-based application
		for (IJaxrsApplication application : applications) {
			if (application.getElementKind() == EnumElementKind.APPLICATION_WEBXML) {
				metamodel.remove((JaxrsBaseElement) application);
			} else {
				javaApplication = (JaxrsJavaApplication) application;
				final Annotation appPathAnnotation = javaApplication
						.getAnnotation(EnumJaxrsClassname.APPLICATION_PATH.qualifiedName);
				javaApplication.removeAnnotation(appPathAnnotation);
			}
		}
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] markers = findJaxrsMarkers(javaApplication);
		assertThat(markers.length, equalTo(1));
		assertThat(markers, hasPreferenceKey(JAVA_APPLICATION_MISSING_APPLICATION_PATH_ANNOTATION));
	}

	@Test
	public void shouldReportProblemOnJavaApplicationIfInvalidTypeHierarchy() throws CoreException, ValidationException {
		// preconditions
		final IType type = JdtUtils.resolveType("org.jboss.tools.ws.jaxrs.sample.services.RestApplication",
				javaProject, new NullProgressMonitor());
		WorkbenchUtils.replaceAllOccurrencesOfCode(type.getCompilationUnit(), "extends Application", "", false);
		final JaxrsJavaApplication javaApplication = metamodel.getJavaApplications().get(0);
		deleteJaxrsMarkers(project);
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] markers = findJaxrsMarkers(javaApplication);
		assertThat(markers, hasPreferenceKey(JAVA_APPLICATION_INVALID_TYPE_HIERARCHY));
	}

	@Test
	public void shouldNotReportProblemOnApplicationIfMissingApplicationPathAnnotationWithOverride() throws Exception {
		// preconditions
		final JaxrsJavaApplication javaApplication = metamodel.getJavaApplications().get(0);
		final Annotation appPathAnnotation = javaApplication
				.getAnnotation(EnumJaxrsClassname.APPLICATION_PATH.qualifiedName);
		javaApplication.removeAnnotation(appPathAnnotation);
		final JaxrsWebxmlApplication webxmlDefaultApplication = (JaxrsWebxmlApplication) metamodel.getApplication();
		metamodel.remove(webxmlDefaultApplication);
		final JaxrsWebxmlApplication webxmlApplication = createWebxmlApplication(javaApplication.getJavaClassName(),
				"/foo");
		metamodel.add(webxmlApplication);
		deleteJaxrsMarkers(project);
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] markers = findJaxrsMarkers(javaApplication);
		assertThat(markers.length, equalTo(0));
	}

	@Test
	public void shouldNotReportProblemOnApplicationIfAnnotationExistsAndHierarchyValid() throws CoreException,
			ValidationException {
		// preconditions
		final JaxrsJavaApplication javaApplication = metamodel.getJavaApplications().get(0);
		final JaxrsWebxmlApplication webxmlDefaultApplication = (JaxrsWebxmlApplication) metamodel.getApplication();
		metamodel.remove(webxmlDefaultApplication);
		deleteJaxrsMarkers(project);
		// operation
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation
		final IMarker[] markers = findJaxrsMarkers(javaApplication);
		assertThat(markers.length, equalTo(0));
	}

	@Test
	public void shouldNotReportProblemOnApplicationWhenAnnotationRemovedAndSuperclassExtensionRemoved()
			throws CoreException, ValidationException {
		// preconditions operation #1
		final JaxrsJavaApplication javaApplication = metamodel.getJavaApplications().get(0);
		final JaxrsWebxmlApplication webxmlDefaultApplication = (JaxrsWebxmlApplication) metamodel.getApplication();
		metamodel.remove(webxmlDefaultApplication);
		deleteJaxrsMarkers(project);
		// operation #1: remove annotation and validate
		LOGGER.warn("*** Operation #1 ***");
		WorkbenchUtils.replaceAllOccurrencesOfCode(javaApplication.getJavaElement().getCompilationUnit(),
				"@ApplicationPath(\"/app\")", "", false);
		LOGGER.warn("*** Validating after Operation #1 ***");
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation after operation #1
		IMarker[] markers = findJaxrsMarkers(javaApplication);
		assertThat(markers, hasPreferenceKey(JAVA_APPLICATION_MISSING_APPLICATION_PATH_ANNOTATION));
		assertThat(markers.length, equalTo(1));
		// preconditions operation #2
		deleteJaxrsMarkers(project);
		// operation #2: remove 'extends Application'
		LOGGER.warn("*** Operation #2 ***");
		WorkbenchUtils.replaceAllOccurrencesOfCode(javaApplication.getJavaElement().getCompilationUnit(),
				"extends Application", "", false);
		LOGGER.warn("*** Validating after Operation #2 ***");
		new JaxrsMetamodelValidator().validateAll(project, validationHelper, context, validatorManager, reporter);
		// validation after operation #2
		markers = findJaxrsMarkers(javaApplication);
		assertThat(markers.length, equalTo(0));
	}
}
