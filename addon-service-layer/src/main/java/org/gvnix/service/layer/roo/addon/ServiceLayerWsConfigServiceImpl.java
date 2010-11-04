/*
 * gvNIX. Spring Roo based RAD tool for Conselleria d'Infraestructures
 * i Transport - Generalitat Valenciana
 * Copyright (C) 2010 CIT - Generalitat Valenciana
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gvnix.service.layer.roo.addon;

import japa.parser.JavaParser;
import japa.parser.ParseException;
import japa.parser.ast.*;
import japa.parser.ast.body.*;
import japa.parser.ast.expr.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.felix.scr.annotations.*;
import org.gvnix.service.layer.roo.addon.annotations.GvNIXXmlElement;
import org.springframework.roo.addon.maven.MavenOperations;
import org.springframework.roo.addon.web.mvc.controller.UrlRewriteOperations;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.details.*;
import org.springframework.roo.classpath.details.annotations.*;
import org.springframework.roo.classpath.javaparser.CompilationUnitServices;
import org.springframework.roo.classpath.javaparser.details.JavaParserFieldMetadata;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.model.*;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.*;
import org.springframework.roo.project.Property;
import org.springframework.roo.support.util.*;
import org.w3c.dom.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Utilities to manage the CXF web services library.
 * 
 * @author Ricardo García ( rgarcia at disid dot com ) at <a
 *         href="http://www.disid.com">DiSiD Technologies S.L.</a> made for <a
 *         href="http://www.cit.gva.es">Conselleria d'Infraestructures i
 *         Transport</a>
 * @author Mario Martínez Sánchez ( mmartinez at disid dot com ) at <a
 *         href="http://www.disid.com">DiSiD Technologies S.L.</a> made for <a
 *         href="http://www.cit.gva.es">Conselleria d'Infraestructures i
 *         Transport</a>
 */
@Component(immediate = true)
@Service
public class ServiceLayerWsConfigServiceImpl implements
        ServiceLayerWsConfigService {

    @Reference
    private MetadataService metadataService;
    @Reference
    private FileManager fileManager;
    @Reference
    private PathResolver pathResolver;
    @Reference
    private ProjectOperations projectOperations;
    @Reference
    private UrlRewriteOperations urlRewriteOperations;
    @Reference
    private AnnotationsService annotationsService;
    @Reference
    private MavenOperations mavenOperations;
    @Reference
    private JavaParserService javaParserService;

    private static final String CXF_WSDL2JAVA_EXECUTION_ID = "generate-sources-cxf-server";

    private List<File> gVNIXXmlElementList;
    private List<File> gVNIXWebFaultList;
    private List<File> gVNIXXmlWebServiceList;

    protected static Logger logger = Logger
            .getLogger(ServiceLayerWsConfigService.class.getName());

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Check if Cxf is set in the project.
     * </p>
     * <p>
     * If is not set, then installs dependencies to the pom.xml and creates the
     * cxf configuration file.
     * </p>
     * 
     * @param type
     *            Communication type
     */
    public void install(CommunicationSense type) {

        // Check if properties are set in pom.xml
        addProjectProperties(type);

        // Check if it's already installed.
        if (isLibraryInstalled(type)) {

            // Nothing to do
            return;
        }

        // Add dependencies to project
        installDependencies(type);

        if (type == CommunicationSense.EXPORT) {

            // Create CXF config file src/main/webapp/WEB-INF/cxf-PROJECT_ID.xml
            installCxfConfigurationFile();

            // Update src/main/webapp/WEB-INF/web.xml :
            // - Add CXFServlet and map it to /services/*
            // - Add cxf-PROJECT_NAME.xml to Spring Context Loader
            installCxfWebConfigurationFile();

            // TODO: comprobar si ya se ha actualizado el fichero urlrewrite.
            // Setup URL rewrite to avoid to filter requests to WebServices
            installCxfUrlRewriteConfigurationFile();
        }
    }

    /**
     * 
     * {@inheritDoc}
     * 
     * <p>
     * Checks these types:
     * </p>
     * <ul>
     * <li>
     * Cxf Dependencies in pom.xml</li>
     * <li>
     * Cxf configuration file exists</li>
     * </ul>
     * 
     */
    public boolean isLibraryInstalled(CommunicationSense type) {

        // TODO Check Web and Url Rewrite configuration files on IMPORT ?

        boolean cxfInstalled = isDependenciesInstalled(type);

        if (type == CommunicationSense.EXPORT) {

            cxfInstalled = cxfInstalled
                    && fileManager.exists(getCxfConfigurationFilePath());
        }

        return cxfInstalled;
    }

    /**
     * Returns CXF absolute configuration file path in the project.
     * 
     * <p>
     * Creates the cxf config file using project name.
     * </p>
     * 
     * @return Path to the Cxf configuration file or null if not exists
     */
    private String getCxfConfigurationFilePath() {

        String cxfFile = "WEB-INF/cxf-".concat(getProjectName()).concat(".xml");

        // Checks for src/main/webapp/WEB-INF/cxf-PROJECT_ID.xml
        String cxfXmlPath = pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP,
                cxfFile);

        return cxfXmlPath;
    }

    /**
     * Returns project name to set CXF configuration file.
     * 
     * @return Project Name.
     */
    private String getProjectName() {
        // Project ID
        String prjId = ProjectMetadata.getProjectIdentifier();
        ProjectMetadata projectMetadata = (ProjectMetadata) metadataService
                .get(prjId);
        Assert.isTrue(projectMetadata != null, "Project metadata required");

        String projectName = projectMetadata.getProjectName();

        return projectName;
    }

    /**
     * Add the file <code>src/main/webapp/WEB-INF/cxf-PROJECT_ID.xml</code> from
     * <code>cxf-template.xml</code> if not exists.
     */
    private void installCxfConfigurationFile() {

        String cxfXmlPath = getCxfConfigurationFilePath();

        if (fileManager.exists(cxfXmlPath)) {

            // File exists, nothing to do
            return;
        }

        InputStream templateInputStream = TemplateUtils.getTemplate(getClass(),
                "cxf-template.xml");
        MutableFile cxfXmlMutableFile = fileManager.createFile(cxfXmlPath);

        try {

            FileCopyUtils.copy(templateInputStream, cxfXmlMutableFile
                    .getOutputStream());
        } catch (Exception e) {

            throw new IllegalStateException(e);
        }

        fileManager.scan();
    }

    /**
     * Check if dependencies are set in project's pom.xml.
     * 
     * <p>
     * Search if the dependencies defined in addon sense type xml file
     * (dependencies-*.xml) are set in pom.xml.
     * </p>
     * 
     * @param type
     *            Communication type
     * @return true if all dependencies are set in pom.xml
     */
    protected boolean isDependenciesInstalled(CommunicationSense type) {

        boolean cxfDependenciesExists = true;

        ProjectMetadata project = (ProjectMetadata) metadataService
                .get(ProjectMetadata.getProjectIdentifier());
        if (project == null) {
            return false;
        }

        // Dependencies elements are defined as:
        // <dependency org="org.apache.cxf" name="cxf-rt-bindings-soap"
        // rev="2.2.6" />
        List<Element> cxfDependenciesList = getRequiredDependencies(type);

        Dependency cxfDependency;

        for (Element element : cxfDependenciesList) {

            cxfDependency = new Dependency(element);
            cxfDependenciesExists = cxfDependenciesExists
                    && project.isDependencyRegistered(cxfDependency);
        }

        return cxfDependenciesExists;
    }

    /**
     * Get the file name of the Cxf required dependencies of certain type.
     * 
     * @param type
     *            Type of required dependencies
     * @return File name
     */
    private String getCxfRequiredDependenciesFileName(CommunicationSense type) {

        StringBuffer name = new StringBuffer("dependencies-");

        switch (type) {
        case EXPORT:
            name.append("export");
            break;
        case IMPORT:
            name.append("import");
            break;
        case IMPORT_RPC_ENCODED:
            name.append("import-axis");
            break;
        }

        name.append(".xml");

        return name.toString();
    }

    /**
     * Get Addon dependencies list to install.
     * 
     * <p>
     * Get addon dependencies defined in dependencies-XXXX.xml
     * </p>
     * 
     * @param type
     *            Communication type
     * @return List of addon dependencies as xml elements
     */
    protected List<Element> getRequiredDependencies(CommunicationSense type) {

        // TODO Unify distinct dependencies files in only one

        InputStream templateInputStream = TemplateUtils.getTemplate(getClass(),
                getCxfRequiredDependenciesFileName(type));
        Assert.notNull(templateInputStream, "Can't adquire dependencies file "
                + type);

        Document dependencyDoc;
        try {

            dependencyDoc = XmlUtils.getDocumentBuilder().parse(
                    templateInputStream);
        } catch (Exception e) {

            throw new IllegalStateException(e);
        }

        Element dependencies = (Element) dependencyDoc.getFirstChild();

        // TODO If only one dependencies file: /dependencies/XXXXX/dependency
        return XmlUtils.findElements("/dependencies/dependency", dependencies);
    }

    /**
     * Add addon dependencies to project dependencies if necessary.
     * 
     * @param type
     *            Communication type
     */
    private void installDependencies(CommunicationSense type) {

        // If dependencies are installed.
        boolean isInstalled = isDependenciesInstalled(type);

        // Add project properties values.
        addProjectProperties(type);

        if (!isInstalled) {
            List<Element> cxfDependencies = getRequiredDependencies(type);
            for (Element dependency : cxfDependencies) {
                projectOperations.dependencyUpdate(new Dependency(dependency));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void addProjectProperties(CommunicationSense type) {

        // Add project properties, as versions
        List<Element> projectProperties = new ArrayList<Element>();

        switch (type) {

        case IMPORT:

            projectProperties = XmlUtils
                    .findElements("/configuration/gvnix/properties/*",
                            XmlUtils.getConfiguration(this.getClass(),
                                    "properties.xml"));
            break;

        case EXPORT:

            projectProperties = XmlUtils
                    .findElements("/configuration/gvnix/properties/*",
                            XmlUtils.getConfiguration(this.getClass(),
                                    "properties.xml"));
            break;

        case EXPORT_WSDL:

            projectProperties = XmlUtils
                    .findElements("/configuration/gvnix/properties/*",
                            XmlUtils.getConfiguration(this.getClass(),
                                    "properties.xml"));
            break;

        case IMPORT_RPC_ENCODED:

            // TODO Check cxf version property before ?
            projectProperties = XmlUtils.findElements(
                    "/configuration/gvnix/properties/*", XmlUtils
                            .getConfiguration(this.getClass(),
                                    "properties-axis.xml"));
            break;
        }

        for (Element property : projectProperties) {
            projectOperations.addProperty(new Property(property));
        }
    }

    /**
     * Update WEB-INF/web.xml.
     * 
     * <ul>
     * <li>Create the CXF servlet declaration and mapping</li>
     * <li>Configure ContextLoader to load cxf-PROJECT_ID.xml</li>
     * </ul>
     */
    private void installCxfWebConfigurationFile() {

        String webXmlPath = pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP,
                "WEB-INF/web.xml");
        Assert.isTrue(fileManager.exists(webXmlPath), "web.xml not found");

        MutableFile webXmlMutableFile = null;
        Document webXml;
        try {

            webXmlMutableFile = fileManager.updateFile(webXmlPath);
            webXml = XmlUtils.getDocumentBuilder().parse(
                    webXmlMutableFile.getInputStream());

        } catch (Exception e) {

            throw new IllegalStateException(e);
        }

        Element root = webXml.getDocumentElement();

        if (null != XmlUtils
                .findFirstElement(
                        "/web-app/servlet[servlet-class='org.apache.cxf.transport.servlet.CXFServlet']",
                        root)) {
            // cxf servlet already installed, nothing to do
            return;
        }

        // Insert servlet def
        Element firstServletMapping = XmlUtils.findRequiredElement(
                "/web-app/servlet-mapping", root);

        Element servlet = webXml.createElement("servlet");
        Element servletName = webXml.createElement("servlet-name");

        // TODO: Create command parameter to set the servlet name
        servletName.setTextContent("CXFServlet");
        servlet.appendChild(servletName);
        Element servletClass = webXml.createElement("servlet-class");
        servletClass
                .setTextContent("org.apache.cxf.transport.servlet.CXFServlet");
        servlet.appendChild(servletClass);
        root.insertBefore(servlet, firstServletMapping.getPreviousSibling());

        // Insert servlet mapping
        Element servletMapping = webXml.createElement("servlet-mapping");
        Element servletName2 = webXml.createElement("servlet-name");
        servletName2.setTextContent("CXFServlet");
        servletMapping.appendChild(servletName2);

        // TODO: Create command parameter to set the servlet mapping
        Element urlMapping = webXml.createElement("url-pattern");
        urlMapping.setTextContent("/services/*");
        servletMapping.appendChild(urlMapping);
        root.insertBefore(servletMapping, firstServletMapping);

        // Project Name
        String prjName = getProjectName();

        String cxfFile = "WEB-INF/cxf-".concat(prjName).concat(".xml");

        Element contextConfigLocation = XmlUtils
                .findFirstElement(
                        "/web-app/context-param[param-name='contextConfigLocation']/param-value",
                        root);
        String paramValueContent = contextConfigLocation.getTextContent();
        contextConfigLocation.setTextContent(cxfFile.concat(" ").concat(
                paramValueContent));

        XmlUtils.writeXml(webXmlMutableFile.getOutputStream(), webXml);
    }

    /**
     * Update url rewrite rules.
     */
    private void installCxfUrlRewriteConfigurationFile() {
        List<Element> rules = getCxfUrlRewriteRequiredRules();

        // Open file and append rules before the first element
        String xmlPath = pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP,
                "WEB-INF/urlrewrite.xml");
        Assert.isTrue(fileManager.exists(xmlPath), "urlrewrite.xml not found");

        Document urlRewriteDoc = urlRewriteOperations.getUrlRewriteDocument();

        Element root = urlRewriteDoc.getDocumentElement();

        for (Element rule : rules) {

            // Create rule in dest doc
            Element rewRule = (Element) urlRewriteDoc.adoptNode(rule);

            root.insertBefore(rewRule, root.getFirstChild());
        }

        urlRewriteOperations.writeUrlRewriteDocument(urlRewriteDoc);
    }

    /**
     * Get addon rewrite rules.
     * 
     * @return List of addon rewrite rules
     */
    private List<Element> getCxfUrlRewriteRequiredRules() {

        InputStream templateInputStream = TemplateUtils.getTemplate(getClass(),
                "urlrewrite-rules.xml");
        Assert.notNull(templateInputStream,
                "Could not adquire urlrewrite-rules.xml file");

        Document dependencyDoc;
        try {
            dependencyDoc = XmlUtils.getDocumentBuilder().parse(
                    templateInputStream);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Element root = (Element) dependencyDoc.getFirstChild();

        return XmlUtils.findElements("/urlrewrite-rules/cxf/rule", root);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Define a Web Service class in cxf configuration file to be published.
     * <p>
     * <p>
     * Update cxf file if its necessary to avoid changes in WSDL contract
     * checking type annotation values from service class.
     * </p>
     * <p>
     * Update annotation class if Class name or package changes.
     * </p>
     */
    public boolean exportClass(JavaType className,
            AnnotationMetadata annotationMetadata) {

        Assert.isTrue(annotationMetadata != null, "Annotation '"
                + annotationMetadata.getAnnotationType()
                        .getFullyQualifiedTypeName() + "' in class '"
                + className.getFullyQualifiedTypeName()
                + "'must not be null to check cxf xml configuration file.");

        // Update web service configuration file.
        boolean updateGvNIXWebServiceAnnotation = updateConfiguration(
                className, annotationMetadata);

        return updateGvNIXWebServiceAnnotation;
    }

    /**
     * Updates web services configuration file.
     * 
     * @param className
     *            to export.
     * @param annotationMetadata
     *            values from web service class to set in configuration file.
     * 
     * @return true if annotation from className has to be updated because of
     *         changes in package or class name.
     * 
     */
    private boolean updateConfiguration(JavaType className,
            AnnotationMetadata annotationMetadata) {

        StringAttributeValue serviceName = (StringAttributeValue) annotationMetadata
                .getAttribute(new JavaSymbolName("serviceName"));

        Assert.isTrue(serviceName != null
                && StringUtils.hasText(serviceName.getValue()),
                "Annotation attribute 'serviceName' in "
                        + className.getFullyQualifiedTypeName()
                        + "' must be defined.");

        StringAttributeValue address = (StringAttributeValue) annotationMetadata
                .getAttribute(new JavaSymbolName("address"));

        Assert.isTrue(address != null
                && StringUtils.hasText(address.getValue()),
                "Annotation attribute 'address' in "
                        + className.getFullyQualifiedTypeName()
                        + "' must be defined.");

        StringAttributeValue fullyQualifiedTypeName = (StringAttributeValue) annotationMetadata
                .getAttribute(new JavaSymbolName("fullyQualifiedTypeName"));

        Assert.isTrue(fullyQualifiedTypeName != null
                && StringUtils.hasText(fullyQualifiedTypeName.getValue()),
                "Annotation attribute 'fullyQualifiedTypeName' in "
                        + className.getFullyQualifiedTypeName()
                        + "' must be defined.");

        String cxfXmlPath = getCxfConfigurationFilePath();
        Assert.isTrue(fileManager.exists(cxfXmlPath),
                "Cxf configuration file not found, export again the service.");

        MutableFile cxfXmlMutableFile = null;
        Document cxfXml;
        try {
            cxfXmlMutableFile = fileManager.updateFile(cxfXmlPath);
            cxfXml = XmlUtils.getDocumentBuilder().parse(
                    cxfXmlMutableFile.getInputStream());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Element root = cxfXml.getDocumentElement();

        boolean updateFullyQualifiedTypeName = false;

        // Check if class name and annotation class name are different.
        if (!className.getFullyQualifiedTypeName().contentEquals(
                fullyQualifiedTypeName.getValue())) {
            updateFullyQualifiedTypeName = true;
        }

        // Check if service exists in configuration file.
        boolean updateService = true;

        // 1) Check if class and id exists in bean.
        Element classAndIdService = XmlUtils.findFirstElement(
                "/beans/bean[@id='" + serviceName.getValue().concat("Impl")
                        + "' and @class='"
                        + className.getFullyQualifiedTypeName() + "']", root);

        // Service is already published.
        if (classAndIdService != null) {
            logger.log(Level.FINE, "The service '" + serviceName.getValue()
                    + "' is already set in cxf config file.");
            updateService = false;
        }

        if (updateService) {

            // 2) Check if class exists or it hasn't changed.
            Element classService = null;

            if (updateFullyQualifiedTypeName) {

                // Check if exists with class name.
                classService = XmlUtils.findFirstElement("/beans/bean[@class='"
                        + className.getFullyQualifiedTypeName() + "']", root);

                if (classService != null) {

                    // Update bean with new Id attribute.
                    Element updateClassService = classService;
                    String idValue = classService.getAttribute("id");

                    if (!StringUtils.hasText(idValue)
                            || !idValue.contentEquals(serviceName.getValue()
                                    .concat("Impl"))) {
                        updateClassService.setAttribute("id", serviceName
                                .getValue().concat("Impl"));

                        classService.getParentNode().replaceChild(
                                updateClassService, classService);
                        logger
                                .log(
                                        Level.INFO,
                                        "The service '"
                                                + serviceName.getValue()
                                                + "' has updated 'id' attribute in cxf config file.");
                    }

                } else {

                    // Check if exists with fullyQualifiedTypeName.
                    classService = XmlUtils.findFirstElement(
                            "/beans/bean[@class='"
                                    + fullyQualifiedTypeName.getValue() + "']",
                            root);

                    if (classService != null) {

                        Element updateClassService = classService;
                        String idValue = classService.getAttribute("id");

                        updateClassService.setAttribute("class", className
                                .getFullyQualifiedTypeName());

                        if (!StringUtils.hasText(idValue)
                                || !idValue.contentEquals(serviceName
                                        .getValue().concat("Impl"))) {
                            updateClassService.setAttribute("id", serviceName
                                    .getValue().concat("Impl"));

                            logger
                                    .log(
                                            Level.INFO,
                                            "The service '"
                                                    + serviceName.getValue()
                                                    + "' has updated 'id' attribute in cxf config file.");
                        }

                        classService.getParentNode().replaceChild(
                                updateClassService, classService);
                        logger
                                .log(
                                        Level.INFO,
                                        "The service '"
                                                + serviceName.getValue()
                                                + "' has updated 'class' attribute in cxf config file.");
                    }

                }
            } else {

                // Check if exists with class name.
                classService = XmlUtils.findFirstElement("/beans/bean[@class='"
                        + className.getFullyQualifiedTypeName() + "']", root);

                if (classService != null) {

                    // Update bean with new Id attribute.
                    Element updateClassService = classService;
                    String idValue = classService.getAttribute("id");

                    if (!StringUtils.hasText(idValue)
                            || !idValue.contentEquals(serviceName.getValue()
                                    .concat("Impl"))) {
                        updateClassService.setAttribute("id", serviceName
                                .getValue().concat("Impl"));

                        classService.getParentNode().replaceChild(
                                updateClassService, classService);
                        logger
                                .log(
                                        Level.INFO,
                                        "The service '"
                                                + serviceName.getValue()
                                                + "' has updated 'id' attribute in cxf config file.");
                    }
                }
            }

            // 3) Check if id exists.
            Element idService = XmlUtils.findFirstElement("/beans/bean[@id='"
                    + serviceName.getValue().concat("Impl") + "']", root);

            if (idService != null) {

                // Update bean with new class attribute.
                Element updateIdService = idService;
                String classNameAttribute = idService.getAttribute("class");

                if (!StringUtils.hasText(classNameAttribute)
                        || !classNameAttribute.contentEquals(className
                                .getFullyQualifiedTypeName())) {
                    updateIdService.setAttribute("class", className
                            .getFullyQualifiedTypeName());
                    idService.getParentNode().replaceChild(updateIdService,
                            idService);
                    logger
                            .log(
                                    Level.INFO,
                                    "The service '"
                                            + serviceName.getValue()
                                            + "' has updated 'class' attribute in cxf config file.");
                }

            }

            Element bean;
            // Check id and class values to create a new bean.
            if (classService == null && idService == null) {

                bean = cxfXml.createElement("bean");
                bean.setAttribute("id", serviceName.getValue().concat("Impl"));
                bean.setAttribute("class", className
                        .getFullyQualifiedTypeName());

                root.appendChild(bean);
            }
        }

        boolean updateEndpoint = true;

        // Check if endpoint exists in the configuration file.
        Element jaxwsBean = XmlUtils.findFirstElement(
                "/beans/endpoint[@address='/" + address.getValue()
                        + "' and @id='" + serviceName.getValue() + "']", root);

        // 1) Check if exists with id and address.
        if (jaxwsBean != null) {

            logger.log(Level.FINE, "The endpoint '" + serviceName.getValue()
                    + "' is already set in cxf config file.");
            updateEndpoint = false;
        }

        if (updateEndpoint) {

            // 2) Check if exists a bean with annotation address value and
            // updates id attribute with annotation serviceName value.
            Element addressEndpoint = XmlUtils.findFirstElement(
                    "/beans/endpoint[@address='/" + address.getValue() + "']",
                    root);

            if (addressEndpoint != null) {

                // Update bean with new Id attribute.
                Element updateAddressEndpoint = addressEndpoint;
                String idAttribute = addressEndpoint.getAttribute("id");

                if (!StringUtils.hasText(idAttribute)
                        || !idAttribute.contentEquals(serviceName.getValue())) {

                    updateAddressEndpoint.setAttribute("id", serviceName
                            .getValue());
                    updateAddressEndpoint.setAttribute("implementor", "#"
                            .concat(serviceName.getValue()).concat("Impl"));

                    addressEndpoint.getParentNode().replaceChild(
                            updateAddressEndpoint, addressEndpoint);
                    logger
                            .log(
                                    Level.INFO,
                                    "The endpoint bean '"
                                            + serviceName.getValue()
                                            + "' has updated 'id' attribute in cxf config file.");

                }

            }

            Element idEndpoint = XmlUtils.findFirstElement(
                    "/beans/endpoint[@id='" + serviceName.getValue() + "']",
                    root);

            // 3) Check if exists a bean with annotation address value in id
            // attribute and updates address attribute with annotation address
            // value.
            if (idEndpoint != null) {

                // Update bean with new Id attribute.
                Element updateIdEndpoint = idEndpoint;

                String addressAttribute = idEndpoint.getAttribute("address");

                if (!StringUtils.hasText(addressAttribute)
                        || !addressAttribute.contentEquals("/".concat(address
                                .getValue()))) {

                    updateIdEndpoint.setAttribute("address", "/".concat(address
                            .getValue()));

                    idEndpoint.getParentNode().replaceChild(updateIdEndpoint,
                            idEndpoint);
                    logger
                            .log(
                                    Level.INFO,
                                    "The endpoint bean '"
                                            + serviceName.getValue()
                                            + "' has updated 'address' attribute in cxf config file.");

                }

            }

            Element endpoint;
            // Check values to create new endpoint bean.
            if (addressEndpoint == null && idEndpoint == null) {

                endpoint = cxfXml.createElement("jaxws:endpoint");
                endpoint.setAttribute("id", serviceName.getValue());
                endpoint.setAttribute("implementor", "#".concat(
                        serviceName.getValue()).concat("Impl"));
                endpoint
                        .setAttribute("address", "/".concat(address.getValue()));
                root.appendChild(endpoint);
            }

        }

        // Update configuration file.
        if (updateService || updateEndpoint) {
            XmlUtils.writeXml(cxfXmlMutableFile.getOutputStream(), cxfXml);
        }

        return updateFullyQualifiedTypeName;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Reverts the order of the package name split with dots.
     * </p>
     * 
     */
    public String convertPackageToTargetNamespace(String packageName) {

        // If there isn't package name in the class, return a blank String.
        if (!StringUtils.hasText(packageName)) {
            return "";
        }

        String[] delimitedString = StringUtils.delimitedListToStringArray(
                packageName, ".");
        List<String> revertedList = new ArrayList<String>();

        String revertedString;

        for (int i = delimitedString.length - 1; i >= 0; i--) {
            revertedList.add(delimitedString[i]);
        }

        revertedString = StringUtils.collectionToDelimitedString(revertedList,
                ".");

        revertedString = "http://".concat(revertedString).concat("/");

        return revertedString;

    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Adds the plugin configuration from a file.
     * </p>
     * <p>
     * Defines an execution for the serviceClass with the serviceName to
     * generate in maven compile goal.
     * </p>
     */
    public void jaxwsBuildPlugin(JavaType serviceClass, String serviceName,
            String addressName, String fullyQualifiedTypeName) {

        // Update plugin with execution configuration.
        String pomPath = getPomFilePath();
        Assert.isTrue(pomPath != null,
                "Cxf configuration file not found, export again the service.");

        MutableFile pomMutableFile = null;
        Document pom;
        try {
            pomMutableFile = fileManager.updateFile(pomPath);
            pom = XmlUtils.getDocumentBuilder().parse(
                    pomMutableFile.getInputStream());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Element root = pom.getDocumentElement();

        Element jaxWsPlugin = XmlUtils
                .findFirstElement(
                        "/project/build/plugins/plugin[groupId='org.apache.cxf' and artifactId='cxf-java2ws-plugin']",
                        root);

        if (jaxWsPlugin == null) {

            logger
                    .log(Level.INFO,
                            "Jax-Ws plugin is not defined in the pom.xml. Installing in project.");
            // Installs jax2ws plugin.
            installJaxwsBuildPlugin();
        }

        boolean classNameChanged = false;

        if (!serviceClass.getFullyQualifiedTypeName().contentEquals(
                fullyQualifiedTypeName)) {
            classNameChanged = true;
        }

        // Checks if already exists the execution.
        Element serviceExecution = XmlUtils
                .findFirstElement(
                        "/project/build/plugins/plugin/executions/execution/configuration[className='"
                                + serviceClass.getFullyQualifiedTypeName()
                                + "']", root);

        if (serviceExecution != null) {
            logger.log(Level.FINE, "Wsdl generation with CXF plugin for '"
                    + serviceName + " service, has been configured before.");
            return;
        }

        if (classNameChanged) {
            serviceExecution = XmlUtils.findFirstElement(
                    "/project/build/plugins/plugin/executions/execution/configuration[className='"
                            + fullyQualifiedTypeName + "']", root);

            // Update with serviceClass.getFullyQualifiedTypeName().
            if (serviceExecution != null && serviceExecution.hasChildNodes()) {

                Node updateServiceExecution;
                updateServiceExecution = (serviceExecution.getFirstChild() != null) ? serviceExecution
                        .getFirstChild().getNextSibling()
                        : null;

                while (updateServiceExecution != null) {

                    if (updateServiceExecution.getNodeName().contentEquals(
                            "className")) {
                        updateServiceExecution.setTextContent(serviceClass
                                .getFullyQualifiedTypeName());
                        XmlUtils
                                .writeXml(pomMutableFile.getOutputStream(), pom);
                        logger
                                .log(
                                        Level.INFO,
                                        "Wsdl generation with CXF plugin for '"
                                                + serviceName
                                                + " service, updated className attribute for '"
                                                + serviceClass
                                                        .getFullyQualifiedTypeName()
                                                + "'.");
                        return;
                    }

                    // Check next node.
                    updateServiceExecution = updateServiceExecution
                            .getNextSibling();

                }

            }
        }

        // Execution
        serviceExecution = pom.createElement("execution");

        String gerenateServiceName = StringUtils.uncapitalize(serviceName);

        Element id = pom.createElement("id");
        id.setTextContent("generate-gvnix-service-".concat(gerenateServiceName)
                .concat("-wsdl"));

        serviceExecution.appendChild(id);
        Element phase = pom.createElement("phase");
        phase.setTextContent("compile");

        serviceExecution.appendChild(phase);

        // Configuration
        Element configuration = pom.createElement("configuration");
        Element className = pom.createElement("className");
        className.setTextContent(serviceClass.getFullyQualifiedTypeName());
        Element outputFile = pom.createElement("outputFile");
        outputFile
                .setTextContent("${project.basedir}/src/test/resources/generated/wsdl/"
                        .concat(addressName).concat(".wsdl"));
        Element genWsdl = pom.createElement("genWsdl");
        genWsdl.setTextContent("true");
        Element verbose = pom.createElement("verbose");
        verbose.setTextContent("true");

        configuration.appendChild(className);
        configuration.appendChild(outputFile);
        configuration.appendChild(genWsdl);
        configuration.appendChild(verbose);

        serviceExecution.appendChild(configuration);

        // Goals
        Element goals = pom.createElement("goals");
        Element goal = pom.createElement("goal");
        goal.setTextContent("java2ws");
        goals.appendChild(goal);

        serviceExecution.appendChild(goals);

        // Checks if already exists the execution.
        Element oldExecutions = XmlUtils.findFirstElementByName("executions",
                jaxWsPlugin);

        Element newExecutions;

        // To Update execution definitions It must be replaced in pom.xml to
        // maintain the format.
        if (oldExecutions != null) {
            newExecutions = oldExecutions;
            newExecutions.appendChild(serviceExecution);
            oldExecutions.getParentNode().replaceChild(oldExecutions,
                    newExecutions);
        } else {
            newExecutions = pom.createElement("executions");
            newExecutions.appendChild(serviceExecution);

            jaxWsPlugin.appendChild(newExecutions);
        }

        XmlUtils.writeXml(pomMutableFile.getOutputStream(), pom);
    }

    /**
     * {@inheritDoc}
     */
    public void installJaxwsBuildPlugin() {
        Element pluginElement = XmlUtils.findFirstElement(
                "/jaxws-plugin/plugin", XmlUtils.getConfiguration(this
                        .getClass(), "dependencies-export-jaxws-plugin.xml"));

        projectOperations.buildPluginUpdate(new Plugin(pluginElement));

    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Adds a wsdl location to the plugin configuration. If code generation
     * plugin configuration not exists, it will be created.
     * </p>
     */
    public void addImportLocation(String wsdlLocation, CommunicationSense type) {

        // Project properties to pom.xml
        addProjectProperties(type);

        switch (type) {

        case IMPORT:

            addImportLocationDocument(wsdlLocation);
            break;

        case IMPORT_RPC_ENCODED:

            addImportLocationRpc(wsdlLocation);
            break;

        case EXPORT:
            // TODO: Refactor method name to use for all CommunicationSense to
            // set each plugin.
            break;

        case EXPORT_WSDL:
            // TODO: Export Wsdl2Java
            addExportLocationDocument(wsdlLocation);
            break;
        }
    }

    /**
     * Add a wsdl location to export wsdl of document type.
     * 
     * <p>
     * Adds a wsdl location to the codegen plugin configuration. If code
     * generation plugin configuration not exists, it will be created.
     * </p>
     * 
     * @param wsdlLocation
     *            WSDL file location
     */
    private void addExportLocationDocument(String wsdlLocation) {

        // Get plugin template
        Element plugin = XmlUtils.findFirstElement(
                "/cxf-codegen/cxf-codegen-plugin/plugin", XmlUtils
                        .getConfiguration(this.getClass(),
                                "dependencies-export-wsdl2java-plugin.xml"));

        // Add plugin
        projectOperations.buildPluginUpdate(new Plugin(plugin));

        // Get pom.xml
        String pomPath = getPomFilePath();
        Assert.notNull(pomPath, "pom.xml configuration file not found.");

        // Get a mutable pom.xml reference to modify it
        MutableFile pomMutableFile = null;
        Document pom;
        try {
            pomMutableFile = fileManager.updateFile(pomPath);
            pom = XmlUtils.getDocumentBuilder().parse(
                    pomMutableFile.getInputStream());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Element root = pom.getDocumentElement();

        // Get plugin element
        Element codegenWsPlugin = XmlUtils
                .findFirstElement(
                        "/project/build/plugins/plugin[groupId='org.apache.cxf' and artifactId='cxf-codegen-plugin']",
                        root);

        // If plugin element not exists, message error
        Assert
                .notNull(codegenWsPlugin,
                        "Codegen plugin is not defined in the pom.xml, relaunch again this command.");

        // Checks if already exists the execution.
        Element oldGenerateSourcesCxfServer = XmlUtils
                .findFirstElement(
                        "/project/build/plugins/plugin/executions/execution[id='"+CXF_WSDL2JAVA_EXECUTION_ID+"']", root);

        // Access executions > execution.
        Element newGenerateSourcesCxfServer = pom.createElement("execution");

        // Create name for id.
        Element id = pom.createElement("id");
        id.setTextContent(CXF_WSDL2JAVA_EXECUTION_ID);
        newGenerateSourcesCxfServer.appendChild(id);

        Element phase = pom.createElement("phase");
        phase.setTextContent("generate-sources");
        newGenerateSourcesCxfServer.appendChild(phase);

        Element goals = pom.createElement("goals");
        Element goal = pom.createElement("goal");
        goal.setTextContent("wsdl2java");
        goals.appendChild(goal);
        newGenerateSourcesCxfServer.appendChild(goals);

        Element configuration = pom.createElement("configuration");
        newGenerateSourcesCxfServer.appendChild(configuration);

        Element sourceRoot = pom.createElement("sourceRoot");
        sourceRoot.setTextContent("${basedir}/target/generated-sources/cxf/server");
        configuration.appendChild(sourceRoot);

        Element wsdlOptions = pom.createElement("wsdlOptions");
        configuration.appendChild(wsdlOptions);

        Element wsdlOption = pom.createElement("wsdlOption");

        Element wsdl = pom.createElement("wsdl");
        wsdl.setTextContent(wsdlLocation);

        Element extraArgs = pom.createElement("extraargs");
        Element extraArg = pom.createElement("extraarg");
        extraArg.setTextContent("-impl");
        extraArgs.appendChild(extraArg);

        wsdlOption.appendChild(wsdl);
        wsdlOption.appendChild(extraArgs);

        wsdlOptions.appendChild(wsdlOption);

        // Checks if exists executions.
        Element oldExecutions = XmlUtils.findFirstElementByName("executions",
                codegenWsPlugin);

        Element newExecutions;

        // To Update execution definitions It must be replaced in pom.xml to
        // maintain the format.
        if (oldGenerateSourcesCxfServer != null) {

            oldGenerateSourcesCxfServer.getParentNode().replaceChild(oldGenerateSourcesCxfServer,
                    newGenerateSourcesCxfServer);
        } else {
            
            if (oldExecutions == null) {
            newExecutions = pom.createElement("executions");
            newExecutions.appendChild(newGenerateSourcesCxfServer);

            codegenWsPlugin.appendChild(newExecutions);

            } else {

                newExecutions = oldExecutions;
                newExecutions.appendChild(newGenerateSourcesCxfServer);
                oldExecutions.getParentNode().replaceChild(newExecutions, oldExecutions);
            }
        }

        // Write new XML to disk
        XmlUtils.writeXml(pomMutableFile.getOutputStream(), pom);

    }

    /**
     * Add a wsdl location to import of document type.
     * 
     * <p>
     * Adds a wsdl location to the codegen plugin configuration. If code
     * generation plugin configuration not exists, it will be created.
     * </p>
     * 
     * @param wsdlLocation
     *            WSDL file location
     */
    private void addImportLocationDocument(String wsdlLocation) {

        // Get plugin template
        Element plugin = XmlUtils.findFirstElement("/codegen-plugin/plugin",
                XmlUtils.getConfiguration(this.getClass(),
                        "dependencies-import-codegen-plugin.xml"));

        // Add plugin
        projectOperations.buildPluginUpdate(new Plugin(plugin));

        // Get pom.xml
        String pomPath = getPomFilePath();
        Assert.notNull(pomPath, "pom.xml configuration file not found.");

        // Get a mutable pom.xml reference to modify it
        MutableFile pomMutableFile = null;
        Document pom;
        try {
            pomMutableFile = fileManager.updateFile(pomPath);
            pom = XmlUtils.getDocumentBuilder().parse(
                    pomMutableFile.getInputStream());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Element root = pom.getDocumentElement();

        // Get plugin element
        Element codegenWsPlugin = XmlUtils
                .findFirstElement(
                        "/project/build/plugins/plugin[groupId='org.apache.cxf' and artifactId='cxf-codegen-plugin']",
                        root);

        // If plugin element not exists, message error
        Assert
                .notNull(codegenWsPlugin,
                        "Codegen plugin is not defined in the pom.xml, relaunch again this command.");

        // Access executions > execution > configuration > wsdlOptions element.
        // Configuration and wsdlOptions are created if not exists.
        Element executions = XmlUtils.findFirstElementByName("executions",
                codegenWsPlugin);
        Element execution = XmlUtils.findFirstElementByName("execution",
                executions);
        Element configuration = XmlUtils.findFirstElementByName(
                "configuration", execution);
        if (configuration == null) {

            configuration = pom.createElement("configuration");
            execution.appendChild(configuration);
        }
        Element wsdlOptions = XmlUtils.findFirstElementByName("wsdlOptions",
                configuration);
        if (wsdlOptions == null) {

            wsdlOptions = pom.createElement("wsdlOptions");
            configuration.appendChild(wsdlOptions);
        }

        // Create new wsdl element and append it to the XML tree
        Element wsdlOption = pom.createElement("wsdlOption");
        Element wsdl = pom.createElement("wsdl");
        wsdl.setTextContent(wsdlLocation);
        wsdlOption.appendChild(wsdl);
        wsdlOptions.appendChild(wsdlOption);

        // Write new XML to disk
        XmlUtils.writeXml(pomMutableFile.getOutputStream(), pom);
    }

    /**
     * Add a wsdl location to import of document type.
     * 
     * <p>
     * Adds a wsdl location to the axistools plugin configuration. If code
     * generation plugin configuration not exists, it will be created.
     * </p>
     * 
     * @param wsdlLocation
     *            WSDL file location
     */
    private void addImportLocationRpc(String wsdlLocation) {

        // Get plugin template
        Element plugin = XmlUtils.findFirstElement("/axistools-plugin/plugin",
                XmlUtils.getConfiguration(this.getClass(),
                        "dependencies-import-axistools-plugin.xml"));

        // Add plugin
        projectOperations.buildPluginUpdate(new Plugin(plugin));

        // Get pom.xml
        String pomPath = getPomFilePath();
        Assert.notNull(pomPath, "pom.xml configuration file not found.");

        // Get a mutable pom.xml reference to modify it
        MutableFile pomMutableFile = null;
        Document pom;
        try {
            pomMutableFile = fileManager.updateFile(pomPath);
            pom = XmlUtils.getDocumentBuilder().parse(
                    pomMutableFile.getInputStream());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Element root = pom.getDocumentElement();

        // Get plugin element
        Element axistoolsPlugin = XmlUtils
                .findFirstElement(
                        "/project/build/plugins/plugin[groupId='org.codehaus.mojo' and artifactId='axistools-maven-plugin']",
                        root);

        // If plugin element not exists, message error
        Assert
                .notNull(axistoolsPlugin,
                        "Axistools plugin is not defined in the pom.xml, relaunch again this command.");

        // Access configuration > urls element.
        // Configuration and urls are created if not exists.
        Element configuration = XmlUtils.findFirstElementByName(
                "configuration", axistoolsPlugin);
        if (configuration == null) {

            configuration = pom.createElement("configuration");
            axistoolsPlugin.appendChild(configuration);
        }
        Element urls = XmlUtils.findFirstElementByName("urls", configuration);
        if (urls == null) {

            urls = pom.createElement("urls");
            configuration.appendChild(urls);
        }

        // Create new url element and append it to the XML tree
        Element url = pom.createElement("url");
        url.setTextContent(wsdlLocation);
        urls.appendChild(url);

        // Write new XML to disk
        XmlUtils.writeXml(pomMutableFile.getOutputStream(), pom);
    }

    /**
     * {@inheritDoc}
     */
    public void importService(JavaType serviceClass, String wsdlLocation,
            CommunicationSense type) {

        // Install import WS configuration requirements, if not installed
        install(type);

        // Add wsdl location to pom.xml
        addImportLocation(wsdlLocation, type);

        // Add GvNixAnnotations to the project.
        annotationsService.addGvNIXAnnotationsDependency();
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Check correct WSDL format
     * </p>
     * <p>
     * Configure plugin to generate sources
     * </p>
     * <p>
     * Generate java sources
     * </p>
     */
    public void exportWSDLWebService(String wsdlLocation,
            CommunicationSense type) {

        // 1) Check if WSDL is RPC enconded and copy file to project.
        Document wsdl = checkWSDLFile(wsdlLocation);

        // 2) Configure plugin cxf to generate java code using WSDL.
        addImportLocation(wsdlLocation, type);

        // 3) Reset File List
        gVNIXXmlElementList = new ArrayList<File>();
        gVNIXWebFaultList = new ArrayList<File>();
        gVNIXXmlWebServiceList = new ArrayList<File>();

        // 3) Run maven generate-sources command.
        try {
            mvn(GENERATE_SOURCES);
        } catch (IOException e) {
            Assert.state(false,
                    "There is an error generating java sources with '"
                            + wsdlLocation + "'.\n" + e.getMessage());
        }
        
        // Remove plugin execution
        removeCxfWsdl2JavaPluginExecution();
        
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Check if WSDL is RPC Encoded.
     * </p>
     * 
     * <p>
     * If WSDL is Document/Literal return Xml Document from WSDl.
     * </p>
     */
    public Document checkWSDLFile(String url) {

        Document wsdl = null;
        try {

            // Parse the wsdl location to a DOM document
            wsdl = XmlUtils.getDocumentBuilder().parse(url);
            Element root = wsdl.getDocumentElement();
            Assert.notNull(root, "No valid document format");

            Assert.isTrue(!WsdlParserUtils.isRpcEncoded(root), "This Wsdl '"
                    + url
                    + "' is RPC Encoded and is not supported by the Add-on.");

        } catch (SAXException e) {

            Assert.state(false, "The format of the web service '" + url
                    + "' to export has errors.");

        } catch (IOException e) {

            Assert.state(false, "There is no connection to the web service '"
                    + url + "' to export.");
        }

        return wsdl;
    }

    /**
     * {@inheritDoc}
     */
    public void mvn(String parameters) throws IOException {

        File root = new File(mavenOperations.getProjectRoot());
        Assert.isTrue(root.isDirectory() && root.exists(),
                "Project root does not currently exist as a directory ('"
                        + root.getCanonicalPath() + "')");

        String cmd = null;
        if (File.separatorChar == '\\') {
            cmd = "mvn.bat " + parameters;
        } else {
            cmd = "mvn " + parameters;
        }

        Process p = Runtime.getRuntime().exec(cmd, null, root);

        // Ensure separate threads are used for logging, as per ROO-652
        LoggingInputStream input = new LoggingInputStream(p.getInputStream());
        LoggingInputStream errors = new LoggingInputStream(p.getErrorStream());

        input.start();
        errors.start();

        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private class LoggingInputStream extends Thread {

        private BufferedReader inputStream;

        public LoggingInputStream(InputStream inputStream) {
            this.inputStream = new BufferedReader(new InputStreamReader(
                    inputStream));
        }

        @Override
        public void run() {
            String line;
            try {
                while ((line = inputStream.readLine()) != null) {
                    if (line.startsWith("[ERROR]")) {
                        logger.severe(line);
                    } else if (line.startsWith("[WARNING]")) {
                        logger.warning(line);
                    } else {
                        logger.info(line);
                    }
                }
            } catch (IOException ioe) {
                if (ioe.getMessage().contains("No such file or directory") || // for
                        // *nix/Mac
                        ioe.getMessage().contains("CreateProcess error=2")) // for
                // Windows
                {
                    logger
                            .severe("Could not locate Maven executable; please ensure mvn command is in your path");
                }
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException ignore) {
                    }
                }
            }

        }

    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Search the execution element using id defined in CXF_WSDL2JAVA_EXECUTION_ID
     * field.
     * </p>
     */
    public void removeCxfWsdl2JavaPluginExecution() {

        // Get pom.xml
        String pomPath = getPomFilePath();
        Assert.notNull(pomPath, "pom.xml configuration file not found.");

        // Get a mutable pom.xml reference to modify it
        MutableFile pomMutableFile = null;
        Document pom;
        try {
            pomMutableFile = fileManager.updateFile(pomPath);
            pom = XmlUtils.getDocumentBuilder().parse(
                    pomMutableFile.getInputStream());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        Element root = pom.getDocumentElement();

        // Get plugin element
        Element codegenWsPlugin = XmlUtils
                .findFirstElement(
                        "/project/build/plugins/plugin[groupId='org.apache.cxf' and artifactId='cxf-codegen-plugin']",
                        root);

        // If plugin element not exists, message error
        Assert
                .notNull(codegenWsPlugin,
                        "Codegen plugin is not defined in the pom.xml, relaunch again this command.");

        // Checks if already exists the execution.
        Element oldGenerateSourcesCxfServer = XmlUtils.findFirstElement(
                "/project/build/plugins/plugin/executions/execution[id='"
                        + CXF_WSDL2JAVA_EXECUTION_ID + "']", root);

        if (oldGenerateSourcesCxfServer != null) {

            // Remove existing execution.
            oldGenerateSourcesCxfServer.getParentNode().removeChild(
                    oldGenerateSourcesCxfServer);

            // Write new XML to disk.
            XmlUtils.writeXml(pomMutableFile.getOutputStream(), pom);

        }

    }

    /**
     * {@inheritDoc}
     * 
     */
    public void addFileToUpdateAnnotation(File file,
            GvNIXAnnotationType gvNIXAnnotationType) {

        switch (gvNIXAnnotationType) {

        case XML_ELEMENT:
            gVNIXXmlElementList.add(file);
            break;

        case WEB_FAULT:
            gVNIXWebFaultList.add(file);
            break;

        case WEB_SERVICE:
            gVNIXXmlWebServiceList.add(file);
            break;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Create files from {@link File} lists:
     * </p>
     * <ul>
     * <li>gVNIXXmlElementList</li>
     * <li>gVNIXWebFaultList</li>
     * <li>gVNIXXmlWebServiceList</li>
     * </ul>
     * 
     * <p>
     * Creates GvNIX annotations attributes from defined attributes in files.
     * </p>
     */
    public void generateGvNIXWebServiceFiles() {

        // TODO: Create @GvNIXXmlElement files.
        generateGvNIXXmlElements();

        // TODO: Create @GvNIXWebFault files.

        // TODO: Create @GvNIXWebService files.
    }

    /**
     * Generates java files with '@GvNIXXmlElement' values.
     */
    protected void generateGvNIXXmlElements() {

        AnnotationMetadata rooEntityAnnotationMetadata = new DefaultAnnotationMetadata(
                new JavaType(
                        "org.springframework.roo.addon.javabean.RooJavaBean"),
                new ArrayList<AnnotationAttributeValue<?>>());

        List<AnnotationMetadata> gvNixAnnoationList;

        // GvNIXXmlElement annotation.
        AnnotationMetadata gvNixXmlElementAnnotation;

        for (File xmlElementFile : gVNIXXmlElementList) {

            // Parse Java file.
            CompilationUnit compilationUnit;
            PackageDeclaration packageDeclaration;
            JavaType javaType;
            String declaredByMetadataId;
            // CompilationUnitServices to create the class in fileSystem.
            ServiceLayerWSCompilationUnit compilationUnitServices;

            gvNixAnnoationList = new ArrayList<AnnotationMetadata>();
            try {
                compilationUnit = JavaParser.parse(xmlElementFile);
                packageDeclaration = compilationUnit.getPackage();

                String packageName = packageDeclaration.getName().toString();

                // Get the first class or interface Java type
                List<TypeDeclaration> types = compilationUnit.getTypes();
                if (types != null) {
                    TypeDeclaration type = types.get(0);
                    ClassOrInterfaceDeclaration classOrInterfaceDeclaration;
                    if (type instanceof ClassOrInterfaceDeclaration) {

                        javaType = new JavaType(packageName.concat(".").concat(
                                type.getName()));

                        classOrInterfaceDeclaration = (ClassOrInterfaceDeclaration) type;

                        declaredByMetadataId = PhysicalTypeIdentifier
                                .createIdentifier(javaType, Path.SRC_MAIN_JAVA);

                        // TODO: Retrieve correct values.
                        // Get field declarations.
                        List<FieldMetadata> fieldMetadataList = new ArrayList<FieldMetadata>();
                        FieldMetadata fieldMetadata;
                        FieldDeclaration tmpFieldDeclaration;
                        FieldDeclaration fieldDeclaration;

                        // CompilationUnitServices to create the class.
                        compilationUnitServices = new ServiceLayerWSCompilationUnit(
                                new JavaPackage(compilationUnit.getPackage()
                                        .getName().getName()), javaType,
                                compilationUnit.getImports(),
                                new ArrayList<TypeDeclaration>());

                        for (BodyDeclaration bodyDeclaration : classOrInterfaceDeclaration
                                .getMembers()) {

                            if (bodyDeclaration instanceof FieldDeclaration) {

                                tmpFieldDeclaration = (FieldDeclaration) bodyDeclaration;
                                fieldDeclaration = new FieldDeclaration(
                                        tmpFieldDeclaration.getJavaDoc(),
                                        tmpFieldDeclaration.getModifiers(),
                                        new ArrayList<AnnotationExpr>(),
                                        tmpFieldDeclaration.getType(),
                                        tmpFieldDeclaration.getVariables());

                                for (VariableDeclarator var : fieldDeclaration
                                        .getVariables()) {

                                    fieldMetadata = new JavaParserFieldMetadata(
                                            declaredByMetadataId,
                                            fieldDeclaration, var,
                                            compilationUnitServices, null);

                                    fieldMetadataList.add(fieldMetadata);

                                }
                            }
                        }

                        // ROO entity to generate getters and setters methods.
                        gvNixAnnoationList.add(rooEntityAnnotationMetadata);

                        // TODO: Get all annotations.
                        gvNixXmlElementAnnotation = getGvNIXXmlElementAnnotations(
                                classOrInterfaceDeclaration, javaType,
                                packageDeclaration);
                        gvNixAnnoationList.add(gvNixXmlElementAnnotation);

                        javaParserService.createGvNIXWebServiceClass(javaType,
                                gvNixAnnoationList,
                                GvNIXAnnotationType.XML_ELEMENT,
                                fieldMetadataList, null);

                    }
                }

            } catch (ParseException e) {
                // TODO Auto-generated catch block
                Assert.state(false,
                        "Generated web service java file has errors:\n"
                                + e.getMessage());

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                Assert.state(false,
                        "Generated web service java file has errors:\n"
                                + e.getMessage());

            }

        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Searches for Jaxb annotations in {@link ClassOrInterfaceDeclaration} to
     * convert values to {@link GvNIXXmlElement}.
     * </p>
     */
    public AnnotationMetadata getGvNIXXmlElementAnnotations(
            ClassOrInterfaceDeclaration classOrInterfaceDeclaration,
            JavaType javaType, PackageDeclaration packageDeclaration) {

        AnnotationMetadata gvNIXXmlElementAnnotationMetadata;

        List<AnnotationExpr> annotationExprList = classOrInterfaceDeclaration
                .getAnnotations();

        // Attribute value list.
        List<AnnotationAttributeValue<?>> annotationAttributeValues = new ArrayList<AnnotationAttributeValue<?>>();

        // name
        StringAttributeValue nameStringAttributeValue = null;
        StringAttributeValue xmlTypeNameStringAttributeValue = null;
        // namespace
        StringAttributeValue namespaceStringAttributeValue = null;
        // element list values
        List<StringAttributeValue> elementListStringAttributeValues = new ArrayList<StringAttributeValue>();
        ArrayAttributeValue<StringAttributeValue> elementListArrayAttributeValue;

        boolean existsNameInXmlElement = false;
        boolean existsNamespace = false;
        boolean existsPropOrder = false;

        for (AnnotationExpr annotationExpr : annotationExprList) {

            if (annotationExpr instanceof NormalAnnotationExpr) {

                NormalAnnotationExpr normalAnnotationExpr = (NormalAnnotationExpr) annotationExpr;

                /*
                 * @XmlType(name = "", propOrder = { "fahrenheit" })
                 * 
                 * @XmlRootElement(name = "FahrenheitToCelsius")
                 */
                if (normalAnnotationExpr.getName().getName().contains(
                        ServiceLayerWSExportWSDLListener.xmlRootElement)) {

                    // TODO: Retrieve values.
                    for (MemberValuePair pair : normalAnnotationExpr.getPairs()) {

                        if (pair.getName().contentEquals("name")) {
                            nameStringAttributeValue = new StringAttributeValue(
                                    new JavaSymbolName("name"),
                                    ((StringLiteralExpr) pair.getValue())
                                            .getValue());

                            annotationAttributeValues
                                    .add(nameStringAttributeValue);
                            break;
                        }

                    }

                } else if (normalAnnotationExpr.getName().getName().contains(
                        ServiceLayerWSExportWSDLListener.xmlType)) {

                    for (MemberValuePair pair : normalAnnotationExpr.getPairs()) {

                        // if (pair.getName().contentEquals("name")
                        // && !existsNameInXmlElement) {
                        //
                        // if (StringUtils.hasText(pair.getValue().toString()))
                        // {
                        //
                        // nameStringAttributeValue = new StringAttributeValue(
                        // new JavaSymbolName("name"),
                        // ((StringLiteralExpr) pair.getValue())
                        // .getValue());
                        //
                        // annotationAttributeValues
                        // .add(nameStringAttributeValue);
                        // break;
                        // }
                        // } else

                        if (pair.getName().contentEquals("propOrder")) {

                            // Arraye pair.getValue();
                            ArrayInitializerExpr arrayInitializerExpr = (ArrayInitializerExpr) pair
                                    .getValue();

                            for (Expression expression : arrayInitializerExpr
                                    .getValues()) {

                                StringAttributeValue stringAttributeValue = new StringAttributeValue(
                                        new JavaSymbolName("ignored"),
                                        ((StringLiteralExpr) expression)
                                                .getValue());

                                elementListStringAttributeValues
                                        .add(stringAttributeValue);
                            }

                            elementListArrayAttributeValue = new ArrayAttributeValue<StringAttributeValue>(
                                    new JavaSymbolName("elementList"),
                                    elementListStringAttributeValues);

                            annotationAttributeValues
                                    .add(elementListArrayAttributeValue);

                            existsPropOrder = true;
                            break;

                        } else if (pair.getName().contentEquals("namespace")) {

                            namespaceStringAttributeValue = new StringAttributeValue(
                                    new JavaSymbolName("namespace"), pair
                                            .getValue().toString());

                            annotationAttributeValues
                                    .add(namespaceStringAttributeValue);

                            existsNamespace = true;
                        }

                    }

                }

            } else if (annotationExpr instanceof SingleMemberAnnotationExpr) {

                SingleMemberAnnotationExpr singleMemberAnnotationExpr = (SingleMemberAnnotationExpr) annotationExpr;

                if (singleMemberAnnotationExpr.getName().getName().contains(
                        ServiceLayerWSExportWSDLListener.xmlAccessorType)) {

                }
            }

        }

        // TODO: Check correct values for @GvNIXXmlElement.
        if (!existsPropOrder) {

            StringAttributeValue stringAttributeValue = new StringAttributeValue(
                    new JavaSymbolName("ignored"), "");

            elementListStringAttributeValues = new ArrayList<StringAttributeValue>();
            elementListStringAttributeValues.add(stringAttributeValue);

            elementListArrayAttributeValue = new ArrayAttributeValue<StringAttributeValue>(
                    new JavaSymbolName("elementList"),
                    elementListStringAttributeValues);

            annotationAttributeValues.add(elementListArrayAttributeValue);
        }

        if (!existsNamespace) {

            QualifiedNameExpr projectQualifiedNameExpr = (QualifiedNameExpr) packageDeclaration
                    .getName();

            String packageName = "";
            String baseName = "";

            if (projectQualifiedNameExpr.getQualifier() instanceof NameExpr) {

                NameExpr baseNameExpr = (NameExpr) projectQualifiedNameExpr
                        .getQualifier();

                packageName = baseNameExpr.getName();

                baseName = projectQualifiedNameExpr.getName();

            } else if (projectQualifiedNameExpr.getQualifier() instanceof QualifiedNameExpr) {

                QualifiedNameExpr baseQualifiedNameExpr = (QualifiedNameExpr) projectQualifiedNameExpr
                        .getQualifier();

                packageName = baseQualifiedNameExpr.getQualifier().toString();

                baseName = baseQualifiedNameExpr.getName();
            }

            String namespace = convertPackageToTargetNamespace(packageName);

            namespace = namespace.concat(baseName).concat("/").concat(
                    packageDeclaration.getName().getName());

            namespaceStringAttributeValue = new StringAttributeValue(
                    new JavaSymbolName("namespace"), namespace);

            annotationAttributeValues.add(namespaceStringAttributeValue);
        }

        // Create annotation.
        gvNIXXmlElementAnnotationMetadata = new DefaultAnnotationMetadata(
                new JavaType(GvNIXXmlElement.class.getName()),
                annotationAttributeValues);

        return gvNIXXmlElementAnnotationMetadata;
    }

    // Check correct values for @GvNIXXmlElement

    /**
     * Check if pom.xml file exists in the project and return the path.
     * 
     * <p>
     * Checks if exists pom.xml config file. If not exists, null will be
     * returned.
     * </p>
     * 
     * @return Path to the pom.xml file or null if not exists.
     */
    private String getPomFilePath() {

        // Project ID
        String prjId = ProjectMetadata.getProjectIdentifier();
        ProjectMetadata projectMetadata = (ProjectMetadata) metadataService
                .get(prjId);
        Assert.isTrue(projectMetadata != null, "Project metadata required");

        String pomFileName = "pom.xml";

        // Checks for pom.xml
        String pomPath = pathResolver.getIdentifier(Path.ROOT, pomFileName);

        boolean pomInstalled = fileManager.exists(pomPath);

        if (pomInstalled) {

            return pomPath;
        } else {

            return null;
        }
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Check if exists a project and if it has web.xml configuration file.
     * </p>
     */
    public boolean isProjectWebAvailable() {

        if (getPathResolver() == null) {

            return false;
        }

        String webXmlPath = pathResolver.getIdentifier(Path.SRC_MAIN_WEBAPP,
                "/WEB-INF/web.xml");
        if (!fileManager.exists(webXmlPath)) {

            return false;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Check if exists a project.
     * </p>
     */
    public boolean isProjectAvailable() {

        return getPathResolver() != null;
    }

    /**
     * @return the path resolver or null if there is no user project
     */
    private PathResolver getPathResolver() {

        ProjectMetadata projectMetadata = (ProjectMetadata) metadataService
                .get(ProjectMetadata.getProjectIdentifier());
        if (projectMetadata == null) {

            return null;
        }

        return projectMetadata.getPathResolver();
    }

}
