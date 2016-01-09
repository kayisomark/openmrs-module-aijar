/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 * <p/>
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 * <p/>
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.aijar;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.ConceptName;
import org.openmrs.GlobalProperty;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.FormService;
import org.openmrs.api.context.Context;
import org.openmrs.module.Module;
import org.openmrs.module.ModuleActivator;
import org.openmrs.module.ModuleFactory;
import org.openmrs.module.aijar.api.deploy.bundle.CommonMetadataBundle;
import org.openmrs.module.aijar.api.deploy.bundle.EncounterTypeMetadataBundle;
import org.openmrs.module.aijar.api.deploy.bundle.UgandaAddressMetadataBundle;
import org.openmrs.module.aijar.api.reporting.builder.common.SetupMissedAppointmentsReport;
import org.openmrs.module.aijar.metadata.core.PatientIdentifierTypes;
import org.openmrs.module.appframework.service.AppFrameworkService;
import org.openmrs.module.dataexchange.DataImporter;
import org.openmrs.module.emrapi.EmrApiConstants;
import org.openmrs.module.htmlformentry.HtmlFormEntryService;
import org.openmrs.module.htmlformentryui.HtmlFormUtil;
import org.openmrs.module.metadatadeploy.api.MetadataDeployService;
import org.openmrs.ui.framework.resource.ResourceFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class AijarActivator extends org.openmrs.module.BaseModuleActivator {

    protected Log log = LogFactory.getLog(getClass());

    /**
     * @see ModuleActivator#willRefreshContext()
     */
    public void willRefreshContext() {
        log.info("Refreshing aijar Module");
    }

    /**
     * @see ModuleActivator#contextRefreshed()
     */
    public void contextRefreshed() {
        log.info("aijar Module refreshed");
    }

    /**
     * @see ModuleActivator#willStart()
     */
    public void willStart() {
        log.info("Starting aijar Module");
    }

    /**
     * @see ModuleActivator#started()
     */
    public void started() {
        AdministrationService administrationService = Context.getAdministrationService();
        AppFrameworkService appFrameworkService = Context.getService(AppFrameworkService.class);
        MetadataDeployService deployService = Context.getService(MetadataDeployService.class);

        try {


            // disable the reference app registration page
            appFrameworkService.disableApp("referenceapplication.registrationapp.registerPatient");

            // install HTML Forms
            setupHtmlForms();

            // install commonly used metadata
            installCommonMetadata(deployService);

            // set the HIV care number as an additional identifier that needs to be displayed
            administrationService.setGlobalProperty(new GlobalProperty(EmrApiConstants.GP_EXTRA_PATIENT_IDENTIFIER_TYPES, PatientIdentifierTypes.HIV_CARE_NUMBER.uuid()));

        } catch (Exception e) {
            Module mod = ModuleFactory.getModuleById("aijar");
            ModuleFactory.stopModule(mod);
            throw new RuntimeException("failed to setup the required HTML forms ", e);
        }

        log.info("aijar Module started");
    }

    private void installCommonMetadata(MetadataDeployService deployService) {
        try {
            log.info("Installing metadata");
            log.info("Installing commonly used metadata");
            deployService.installBundle(Context.getRegisteredComponents(CommonMetadataBundle.class).get(0));
            log.info("Finished installing commonly used metadata");
            log.info("Installing encounter types");
            deployService.installBundle(Context.getRegisteredComponents(EncounterTypeMetadataBundle.class).get(0));
            log.info("Finished installing encounter types");
            log.info("Installing address hierarchy");
            deployService.installBundle(Context.getRegisteredComponents(UgandaAddressMetadataBundle.class).get(0));
            log.info("Finished installing addresshierarchy");

        } catch (Exception e) {
            Module mod = ModuleFactory.getModuleById("aijar");
            ModuleFactory.stopModule(mod);
            throw new RuntimeException("failed to install the common metadata ", e);
        }
    }

    private void installConcepts() {
        DataImporter dataImporter = Context.getRegisteredComponent("dataImporter", DataImporter.class);
        dataImporter.importData("metadata/concepts.xml");

        //1.11 requires building the index for the newly added concepts.
        //Without doing so, cs.getConceptByClassName() will return an empty list.
        //We use reflection such that we do not blow up versions before 1.11
        try {
            Method method = Context.class.getMethod("updateSearchIndexForType", new Class[]{Class.class});
            method.invoke(null, new Object[]{ConceptName.class});
        } catch (NoSuchMethodException ex) {
            //this must be a version before 1.11
        } catch (InvocationTargetException ex) {
            log.error("Failed to update search index", ex);
        } catch (IllegalAccessException ex) {
            log.error("Failed to update search index", ex);
        }

    }

    private void setupHtmlForms() throws Exception {
        try {
            ResourceFactory resourceFactory = ResourceFactory.getInstance();
            FormService formService = Context.getFormService();
            HtmlFormEntryService htmlFormEntryService = Context.getService(HtmlFormEntryService.class);

            List<String> htmlforms = Arrays.asList("aijar:htmlforms/122a-HIVCare_ARTCard-SummaryPage.xml",
                    "aijar:htmlforms/122a-HIVCare_ARTCard-HealthEducationPage.xml",
                    "aijar:htmlforms/122a-HIVCare_ARTCard-EncounterPage.xml",
                    "aijar:htmlforms/082a-ExposedInfantClinicalChart-SummaryPage.xml",
                    "aijar:htmlforms/082a-ExposedInfantClinicalChart-EncounterPage.xml");

            if (htmlforms != null) {
                for (String htmlform : htmlforms) {
                    HtmlFormUtil.getHtmlFormFromUiResource(resourceFactory, formService, htmlFormEntryService, htmlform);
                }
            }


        } catch (Exception e) {
            log.error("Error loading the HTML forms " + e.toString());
            // this is a hack to get component test to pass until we find the proper way to mock this
            if (ResourceFactory.getInstance().getResourceProviders() == null) {
                log.error("Unable to load HTML forms--this error is expected when running component tests");
            } else {
                throw e;
            }
        }

    }

    /**
     * Allows to automatically register report definitions at when the
     * module is started
     *
     * @throws Exception
     */
    public void registerReports() throws Exception {
        //Register Missed Appointments Report
        SetupMissedAppointmentsReport mal = new SetupMissedAppointmentsReport();
        mal.setup();
    }

    /**
     * @see ModuleActivator#willStop()
     */
    public void willStop() {
        log.info("Stopping aijar Module");
    }

    /**
     * @see ModuleActivator#stopped()
     */
    public void stopped() {

        log.info("aijar Module stopped");
    }
}
