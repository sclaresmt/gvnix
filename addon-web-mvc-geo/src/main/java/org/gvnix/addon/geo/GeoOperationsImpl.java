package org.gvnix.addon.geo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.gvnix.support.MessageBundleUtils;
import org.gvnix.support.OperationUtils;
import org.gvnix.support.WebProjectUtils;
import org.gvnix.web.i18n.roo.addon.ValencianCatalanLanguage;
import org.springframework.roo.addon.propfiles.PropFileOperations;
import org.springframework.roo.addon.web.mvc.controller.converter.RooConversionService;
import org.springframework.roo.addon.web.mvc.controller.scaffold.RooWebScaffold;
import org.springframework.roo.addon.web.mvc.jsp.i18n.I18n;
import org.springframework.roo.addon.web.mvc.jsp.i18n.I18nSupport;
import org.springframework.roo.addon.web.mvc.jsp.i18n.languages.SpanishLanguage;
import org.springframework.roo.classpath.PhysicalTypeCategory;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.TypeManagementService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.ArrayAttributeValue;
import org.springframework.roo.classpath.details.annotations.ClassAttributeValue;
import org.springframework.roo.classpath.details.annotations.StringAttributeValue;
import org.springframework.roo.classpath.operations.AbstractOperations;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.model.JavaPackage;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.JdkJavaType;
import org.springframework.roo.model.SpringJavaType;
import org.springframework.roo.process.manager.MutableFile;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PathResolver;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.support.logging.HandlerUtils;
import org.springframework.roo.support.util.FileUtils;
import org.springframework.roo.support.util.XmlRoundTripUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Implementation of GEO Addon operations
 * 
 * @author gvNIX Team
 * @since 1.4
 */
@Component
@Service
public class GeoOperationsImpl extends AbstractOperations implements
        GeoOperations {

    private static final JavaType GVNIX_WEB_ENTITY_MAP_LAYER_ANNOTATION = new JavaType(
            "org.gvnix.addon.geo.GvNIXWebEntityMapLayer");

    private static final JavaType ROO_WEB_SCAFFOLD_ANNOTATION = new JavaType(
            "org.springframework.roo.addon.web.mvc.controller.scaffold.RooWebScaffold");

    @Reference
    private PathResolver pathResolver;

    @Reference
    private TypeLocationService typeLocationService;

    @Reference
    private MetadataService metadataService;

    @Reference
    private I18nSupport i18nSupport;

    @Reference
    private PropFileOperations propFileOperations;

    @Reference
    private ProjectOperations projectOperations;

    @Reference
    private TypeManagementService typeManagementService;

    private static final JavaType SCAFFOLD_ANNOTATION = new JavaType(
            RooWebScaffold.class.getName());

    private static final JavaType CONVERSION_SERVICE_ANNOTATION = new JavaType(
            RooConversionService.class.getName());

    private static final JavaType GEO_CONVERSION_SERVICE_ANNOTATION = new JavaType(
            GvNIXGeoConversionService.class.getName());

    private static final JavaType MAP_VIEWER_ANNOTATION = new JavaType(
            GvNIXMapViewer.class.getName());

    private static final Logger LOGGER = HandlerUtils
            .getLogger(GeoOperationsImpl.class);

    /**
     * This method checks if setup command is available
     * 
     * @return true if setup command is available
     */
    @Override
    public boolean isSetupCommandAvailable() {
        return projectOperations
                .isFeatureInstalledInFocusedModule("gvnix-geo-persistence")
                && projectOperations
                        .isFeatureInstalledInFocusedModule("gvnix-jquery");
    }

    /**
     * This method checks if add map command is available
     * 
     * @return true if add map command is available
     */
    @Override
    public boolean isMapCommandAvailable() {
        return isSetupCommandAvailable();
    }

    /**
     * This method checks if web mvc geo all command is available
     * 
     * @return true if web nvc geo all command is available
     */
    @Override
    public boolean isAllCommandAvailable() {
        return isSetupCommandAvailable();
    }

    /**
     * This method checks if web mvc geo add command is available
     * 
     * @return true if web nvc geo add command is available
     */
    @Override
    public boolean isAddCommandAvailable() {
        return isSetupCommandAvailable();
    }

    /**
     * This method imports all necessary element to build a gvNIX GEO
     * application
     */
    @Override
    public void setup() {
        // Adding project dependencies
        updatePomDependencies();
        // Locate all ApplicationConversionServiceFactoryBean and annotate it
        annotateApplicationConversionService();
        // Installing all necessary components
        installComponents();
    }

    /**
     * This method adds all necessary components to display a map view
     */
    @Override
    public void addMap(JavaType controller, JavaSymbolName path) {
        String filePackage = controller.getPackage()
                .getFullyQualifiedPackageName();
        // Doing a previous setup to install necessary components and annotate
        // ApplicationConversionService
        if (!projectOperations
                .isFeatureInstalledInFocusedModule(FEATURE_NAME_GVNIX_GEO_WEB_MVC)) {
            setup();
        }
        // Adding new controller with annotated class
        addMapViewerController(controller, path);
        // Adding new show.jspx and views.xml
        createViews(filePackage, path);
        // Add new component labels to application.properties
        addI18nComponentsProperties();
        // Add new mapController view to application.properties
        addI18nControllerProperties(filePackage, path.getReadableSymbolName()
                .toLowerCase());
    }

    /**
     * This method adds all GEO entities to all available maps or specific map
     */
    @Override
    public void all(JavaSymbolName path) {

        // Checking if exists map element before to generate geo web layer
        if (!checkExistsMapElement()) {
            throw new RuntimeException(
                    "ERROR. Is necesary to create new map element using \"web mvc geo map\" command before generate geo web layer");
        }

        List<String> paths = new ArrayList<String>();
        // Checks if path is null or not. If is null, add all entities to all
        // available maps, if not, add all entities to specified map.
        if (path != null) {
            String pathList = path.toString();
            String[] pathsToAdd = pathList.split(",");

            for (String currentPath : pathsToAdd) {
                currentPath = currentPath.replaceAll("/", "").trim();

                // Getting map controller
                ClassOrInterfaceTypeDetails mapController = GeoUtils
                        .getMapControllerByPath(typeLocationService,
                                currentPath);
                // If mapController is null show an error
                Validate.notNull(
                        mapController,
                        String.format(
                                "Controller annotated with @GvNIXMapViewer and with path \"%s\" doesn't found. Use \"web mvc geo map\" to generate new map view.",
                                currentPath));
                paths.add(currentPath);
            }
        }
        else {
            // If path is null, entity will be added to all maps
            paths.add("");
        }

        // Looking for entities with GEO components and annotate his
        // controllers
        annotateAllGeoEntityControllers(paths);
    }

    /**
     * This method adds specific GEO entities to all available maps or specific
     * map
     */
    @Override
    public void add(JavaType controller, JavaSymbolName path) {

        // Checking if exists map element before to generate geo web layer
        if (!checkExistsMapElement()) {
            throw new RuntimeException(
                    "ERROR. Is necesary to create new map element using \"web mvc geo map\" command before generate geo web layer");
        }

        Validate.notNull(controller,
                "Controller is necessary to execute this operation");

        // Checking that the specified controller is a valid controller
        ClassOrInterfaceTypeDetails controllerDetails = typeLocationService
                .getTypeDetails(controller);

        // Getting scaffold annotation
        AnnotationMetadata scaffoldAnnotation = controllerDetails
                .getAnnotation(ROO_WEB_SCAFFOLD_ANNOTATION);

        Validate.notNull(
                scaffoldAnnotation,
                String.format(
                        "%s is not a valid controller. Controller must be annotated with @RooWebScaffold",
                        controller.getFullyQualifiedTypeName()));

        // Check if is valid GEO Entity
        boolean isValidEntity = GeoUtils.isGeoEntity(scaffoldAnnotation,
                typeLocationService);

        Validate.isTrue(isValidEntity, String
                .format("Specified entity \"%s\" has not GEO fields",
                        scaffoldAnnotation.getAttribute("formBackingObject")
                                .getValue()));

        List<String> paths = new ArrayList<String>();
        // Add annotation to controller
        if (path != null) {
            String pathList = path.toString();
            String[] pathsToAdd = pathList.split(",");

            for (String currentPath : pathsToAdd) {
                currentPath = currentPath.replaceAll("/", "").trim();

                // Getting map controller
                ClassOrInterfaceTypeDetails mapController = GeoUtils
                        .getMapControllerByPath(typeLocationService,
                                currentPath);
                // If mapController is null show an error
                Validate.notNull(
                        mapController,
                        String.format(
                                "Controller annotated with @GvNIXMapViewer doesn't found. Use \"web mvc geo map\" to generate new map view.",
                                currentPath));
                paths.add(currentPath);
            }
        }
        else {
            // If path is null, entity will be added to all maps
            paths.add("");
        }

        annotateGeoEntityController(controller, paths);

    }

    /**
     * This method annotate controller with @GvNIXEntityMapLayer
     * 
     * @param controller
     * @param paths
     */
    public void annotateGeoEntityController(JavaType controller,
            List<String> paths) {

        // Obtain all map controllers
        List<JavaType> mapControllers = GeoUtils
                .getAllMapsControllers(typeLocationService);

        ClassOrInterfaceTypeDetails controllerDetails = typeLocationService
                .getTypeDetails(controller);

        // Generating annotation
        ClassOrInterfaceTypeDetailsBuilder detailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                controllerDetails);

        AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(
                GVNIX_WEB_ENTITY_MAP_LAYER_ANNOTATION);

        // Add annotation to target type
        detailsBuilder.updateTypeAnnotation(annotationBuilder.build());

        // Save changes to disk
        typeManagementService.createOrUpdateTypeOnDisk(detailsBuilder.build());

        // / Update necessary map controllers with current entity
        // If developer specify map path add on it
        if (!(paths.size() == 1 && paths.get(0).equals(""))) {
            Iterator<String> pathIterator = paths.iterator();
            while (pathIterator.hasNext()) {
                // Getting path
                String currentPath = pathIterator.next();
                // Getting map controller for current path
                JavaType mapController = GeoUtils.getMapControllerByPath(
                        currentPath, typeLocationService);

                // Annotate map controllers adding current entity
                annotateMapController(mapController, typeLocationService,
                        typeManagementService, controllerDetails.getType());

            }
        }
        else {
            // If no path selected, annotate all mapControllers with
            // current entityController
            for (JavaType mapController : mapControllers) {
                // Annotate map controllers adding current entity
                annotateMapController(mapController, typeLocationService,
                        typeManagementService, controllerDetails.getType());
            }
        }

    }

    /**
     * This method annotate all controllers if has Geo Fields
     * 
     * @param path
     */
    public void annotateAllGeoEntityControllers(List<String> paths) {
        // Getting all entity controllers annotated with @RooWebScaffold
        Set<ClassOrInterfaceTypeDetails> entityControllers = typeLocationService
                .findClassesOrInterfaceDetailsWithAnnotation(ROO_WEB_SCAFFOLD_ANNOTATION);

        Validate.notNull(entityControllers,
                "Controllers with @RooWebScaffold annotation doesn't found");

        // Obtain all map controllers
        List<JavaType> mapControllers = GeoUtils
                .getAllMapsControllers(typeLocationService);

        Iterator<ClassOrInterfaceTypeDetails> it = entityControllers.iterator();
        while (it.hasNext()) {
            ClassOrInterfaceTypeDetails entityController = it.next();

            // Getting scaffold annotation
            AnnotationMetadata scaffoldAnnotation = entityController
                    .getAnnotation(ROO_WEB_SCAFFOLD_ANNOTATION);

            // Getting entity asociated
            Object entity = scaffoldAnnotation
                    .getAttribute("formBackingObject").getValue();
            ClassOrInterfaceTypeDetails entityDetails = typeLocationService
                    .getTypeDetails((JavaType) entity);

            // Getting all fields of current entity
            List<? extends FieldMetadata> entityFields = entityDetails
                    .getDeclaredFields();

            Iterator<? extends FieldMetadata> fieldsIterator = entityFields
                    .iterator();

            while (fieldsIterator.hasNext()) {
                // Getting field
                FieldMetadata field = fieldsIterator.next();

                // Getting field type to get package
                JavaType fieldType = field.getFieldType();
                JavaPackage fieldPackage = fieldType.getPackage();

                // If has jts field, annotate controller
                if (fieldPackage.toString().equals(
                        "com.vividsolutions.jts.geom")) {

                    // Generating annotation
                    ClassOrInterfaceTypeDetailsBuilder detailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                            entityController);
                    AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(
                            GVNIX_WEB_ENTITY_MAP_LAYER_ANNOTATION);

                    // Add annotation to target type
                    detailsBuilder.updateTypeAnnotation(annotationBuilder
                            .build());

                    // Save changes to disk
                    typeManagementService
                            .createOrUpdateTypeOnDisk(detailsBuilder.build());

                    // Update necessary map controllers with current entity
                    // If developer specify map path add on it
                    if (!(paths.size() == 1 && paths.get(0).equals(""))) {
                        Iterator<String> pathIterator = paths.iterator();
                        while (pathIterator.hasNext()) {
                            // Getting path
                            String currentPath = pathIterator.next();
                            // Getting map controller for current path
                            JavaType mapController = GeoUtils
                                    .getMapControllerByPath(currentPath,
                                            typeLocationService);

                            // Annotate map controllers adding current entity
                            annotateMapController(mapController,
                                    typeLocationService, typeManagementService,
                                    entityController.getType());

                        }
                    }
                    else {
                        // If no path selected, annotate all mapControllers with
                        // current entityController
                        for (JavaType mapController : mapControllers) {
                            // Annotate map controllers adding current entity
                            annotateMapController(mapController,
                                    typeLocationService, typeManagementService,
                                    entityController.getType());
                        }
                    }

                    break;
                }
            }
        }
    }

    /**
     * This method create necessary views to visualize map
     * 
     * @param path
     */
    public void createViews(String controllerPackage, JavaSymbolName path) {
        PathResolver pathResolver = projectOperations.getPathResolver();

        String finalPath = path.getReadableSymbolName().toLowerCase();

        // Modifying views.xml to add show.jspx view
        final String viewsPath = pathResolver.getFocusedIdentifier(
                Path.SRC_MAIN_WEBAPP,
                String.format("WEB-INF/views/%s/views.xml", finalPath));
        final String showPath = pathResolver.getFocusedIdentifier(
                Path.SRC_MAIN_WEBAPP,
                String.format("WEB-INF/views/%s/show.jspx", finalPath));

        // Copying views.xml
        if (!fileManager.exists(viewsPath)) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                inputStream = FileUtils.getInputStream(getClass(),
                        "views/views.xml");
                outputStream = fileManager.createFile(viewsPath)
                        .getOutputStream();

                // Doing this to solve problems with <!DOCTYPE element
                // ////////////////////////////////////////////////
                PrintWriter writer = new PrintWriter(outputStream);
                writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
                writer.println("<!DOCTYPE tiles-definitions PUBLIC \"-//Apache Software Foundation//DTD Tiles Configuration 2.1//EN\" \"http://tiles.apache.org/dtds/tiles-config_2_1.dtd\">");
                writer.println("<tiles-definitions>");
                writer.println(String.format(
                        "   <definition extends=\"default\" name=\"%s/show\">",
                        finalPath));
                writer.println(String
                        .format("      <put-attribute name=\"body\" value=\"/WEB-INF/views/%s/show.jspx\"/>",
                                finalPath));
                writer.println("   </definition>");
                writer.println("</tiles-definitions>");

                writer.flush();
                writer.close();
                // ////////////////////////////////////////////

                IOUtils.copy(inputStream, outputStream);
            }
            catch (final IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            finally {
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(outputStream);
            }
        }

        // Copying show.jspx
        if (!fileManager.exists(showPath)) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                inputStream = FileUtils.getInputStream(getClass(),
                        "views/show.jspx");
                outputStream = fileManager.createFile(showPath)
                        .getOutputStream();

                IOUtils.copy(inputStream, outputStream);
            }
            catch (final IOException ioe) {
                throw new IllegalStateException(ioe);
            }
            finally {
                IOUtils.closeQuietly(inputStream);
                IOUtils.closeQuietly(outputStream);
            }
        }

        // If show.jspx file doesn't exists, show an error
        if (!fileManager.exists(showPath)) {
            throw new RuntimeException(String.format(
                    "ERROR. Not exists show.jspx file on 'views/%s' folder",
                    finalPath));
        }
        else {
            // Getting document and adding definition

            Document docXml = WebProjectUtils.loadXmlDocument(showPath,
                    fileManager);

            Element docRoot = docXml.getDocumentElement();

            // Creating geo:map element
            String mapId = String.format("ps_%s_%s",
                    controllerPackage.replaceAll("[.]", "_"),
                    path.getSymbolNameCapitalisedFirstLetter());

            Element map = docXml.createElement("geo:map");
            map.setAttribute("id", mapId);
            map.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(map));

            // Creating geo:toc element and adding to map
            Element toc = docXml.createElement("geo:toc");
            toc.setAttribute("id", String.format("%s_toc", mapId));
            toc.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(toc));
            map.appendChild(toc);

            // Creating geo:toolbar element and adding to map
            Element toolbar = docXml.createElement("geo:toolbar");
            toolbar.setAttribute("id", String.format("%s_toolbar", mapId));
            toolbar.setAttribute("z",
                    XmlRoundTripUtils.calculateUniqueKeyFor(toolbar));
            map.appendChild(toolbar);

            // Adding childs to mainDiv
            docRoot.appendChild(map);

            fileManager.createOrUpdateTextFileIfRequired(showPath,
                    XmlUtils.nodeToString(docXml), true);

        }

    }

    /**
     * This method generates a new class annotated with @GvNIXMapViewer
     * 
     * @param controller
     * @param path
     */
    public void addMapViewerController(JavaType controller, JavaSymbolName path) {
        // Getting all classes with @GvNIXMapViewer annotation
        // and checking that not exists another with the specified path
        for (JavaType mapViewer : typeLocationService
                .findTypesWithAnnotation(MAP_VIEWER_ANNOTATION)) {

            Validate.notNull(mapViewer, "@GvNIXMapViewer required");

            ClassOrInterfaceTypeDetails mapViewerController = typeLocationService
                    .getTypeDetails(mapViewer);

            // Getting RequestMapping annotations
            final AnnotationMetadata requestMappingAnnotation = MemberFindingUtils
                    .getAnnotationOfType(mapViewerController.getAnnotations(),
                            SpringJavaType.REQUEST_MAPPING);

            Validate.notNull(mapViewer, String.format(
                    "Error on %s getting @RequestMapping value", mapViewer));

            String requestMappingPath = requestMappingAnnotation
                    .getAttribute("value").getValue().toString();
            // If exists some path like the selected, shows an error
            String finalPath = String.format("/%s", path.toString());
            if (finalPath.equals(requestMappingPath)) {
                throw new RuntimeException(
                        String.format(
                                "ERROR. There's other class annotated with @GvNIXMapViewer and path \"%s\"",
                                finalPath));
            }
        }

        // Create new class
        createNewController(controller, generateJavaType(controller), path);
    }

    /**
     * This method creates a controller using specified configuration
     * 
     * @param controller
     * @param target
     * @param path
     */
    public void createNewController(JavaType controller, JavaType target,
            JavaSymbolName path) {
        Validate.notNull(controller, "Entity required");
        if (target == null) {
            target = generateJavaType(controller);
        }

        Validate.isTrue(
                !JdkJavaType.isPartOfJavaLang(target.getSimpleTypeName()),
                "Target name '%s' must not be part of java.lang",
                target.getSimpleTypeName());

        int modifier = Modifier.PUBLIC;

        final String declaredByMetadataId = PhysicalTypeIdentifier
                .createIdentifier(target,
                        pathResolver.getFocusedPath(Path.SRC_MAIN_JAVA));
        File targetFile = new File(
                typeLocationService
                        .getPhysicalTypeCanonicalPath(declaredByMetadataId));
        Validate.isTrue(!targetFile.exists(), "Type '%s' already exists",
                target);

        // Prepare class builder
        final ClassOrInterfaceTypeDetailsBuilder cidBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                declaredByMetadataId, modifier, target,
                PhysicalTypeCategory.CLASS);

        // Prepare annotations array
        List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>(
                2);

        // Add @Controller annotations
        annotations
                .add(new AnnotationMetadataBuilder(SpringJavaType.CONTROLLER));

        // Add @RequestMapping annotation
        AnnotationMetadataBuilder requestMappingAnnotation = new AnnotationMetadataBuilder(
                SpringJavaType.REQUEST_MAPPING);
        requestMappingAnnotation.addStringAttribute("value",
                String.format("/%s", path.toString()));
        annotations.add(requestMappingAnnotation);

        // Add @GvNIXMapViewer annotation with list of all entities annotated
        // with @GvNIXEntityMapLayer
        AnnotationMetadataBuilder mapViewerAnnotation = new AnnotationMetadataBuilder(
                MAP_VIEWER_ANNOTATION);

        // Generating empty class attribute value
        final List<ClassAttributeValue> entityAttributes = new ArrayList<ClassAttributeValue>();

        // Looking for all entities annotated with @GvNIXEntityMapLayer
        for (ClassOrInterfaceTypeDetails entity : typeLocationService
                .findClassesOrInterfaceDetailsWithAnnotation(GVNIX_WEB_ENTITY_MAP_LAYER_ANNOTATION)) {

            // Getting map layer annotation
            AnnotationMetadata mapLayerAnnotation = entity
                    .getAnnotation(GVNIX_WEB_ENTITY_MAP_LAYER_ANNOTATION);

            // Getting maps where will be displayed
            @SuppressWarnings({ "unchecked", "rawtypes" })
            ArrayAttributeValue<StringAttributeValue> mapLayerAttributes = (ArrayAttributeValue) mapLayerAnnotation
                    .getAttribute("maps");

            boolean addEntityToMap = false;

            if (mapLayerAttributes == null) {
                addEntityToMap = true;
            }
            else {

                List<StringAttributeValue> mapLayerAttributesValues = mapLayerAttributes
                        .getValue();

                for (StringAttributeValue map : mapLayerAttributesValues) {
                    if (map.getValue() == path.toString()) {
                        addEntityToMap = true;
                        break;
                    }
                }
            }

            if (addEntityToMap) {
                final ClassAttributeValue entityAttribute = new ClassAttributeValue(
                        new JavaSymbolName("value"), entity.getType());

                entityAttributes.add(entityAttribute);

            }

        }

        // Create "entityLayers" attributes array from string attributes
        // list
        ArrayAttributeValue<ClassAttributeValue> entityLayersArray = new ArrayAttributeValue<ClassAttributeValue>(
                new JavaSymbolName("entityLayers"), entityAttributes);
        // Adding controller class list to MapViewer Controller
        mapViewerAnnotation.addAttribute(entityLayersArray);

        annotations.add(mapViewerAnnotation);

        // Set annotations
        cidBuilder.setAnnotations(annotations);

        typeManagementService.createOrUpdateTypeOnDisk(cidBuilder.build());
    }

    /**
     * Generates new JavaType based on <code>controller</code> class name.
     * 
     * @param controller
     * @param targetPackage if null uses <code>controller</code> package
     * @return
     */
    private JavaType generateJavaType(JavaType controller) {
        return new JavaType(
                String.format("%s.%s", controller.getPackage()
                        .getFullyQualifiedPackageName(), controller
                        .getSimpleTypeName()));
    }

    /**
     * This method annotate ApplicationConversionServices classes to transform
     * GEO elements
     */
    public void annotateApplicationConversionService() {
        // Validate that exists web layer
        Set<JavaType> controllers = typeLocationService
                .findTypesWithAnnotation(SCAFFOLD_ANNOTATION);

        Validate.notEmpty(
                controllers,
                "There's not exists any web layer on this gvNIX application. Execute 'web mvc all --package ~.web' to create web layer.");

        // Getting all classes with @RooConversionService annotation
        for (JavaType conversorService : typeLocationService
                .findTypesWithAnnotation(CONVERSION_SERVICE_ANNOTATION)) {

            Validate.notNull(conversorService, "RooConversionService required");

            ClassOrInterfaceTypeDetails applicationConversionService = typeLocationService
                    .getTypeDetails(conversorService);

            // Only for @RooConversionService annotated controllers
            final AnnotationMetadata rooConversionServiceAnnotation = MemberFindingUtils
                    .getAnnotationOfType(
                            applicationConversionService.getAnnotations(),
                            CONVERSION_SERVICE_ANNOTATION);

            Validate.isTrue(rooConversionServiceAnnotation != null,
                    "Operation for @RooConversionService annotated classes only.");

            final boolean isGeoConversionServiceAnnotated = MemberFindingUtils
                    .getAnnotationOfType(
                            applicationConversionService.getAnnotations(),
                            GEO_CONVERSION_SERVICE_ANNOTATION) != null;

            // If annotation already exists on the target type do nothing
            if (isGeoConversionServiceAnnotated) {
                return;
            }

            ClassOrInterfaceTypeDetailsBuilder detailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                    applicationConversionService);

            AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(
                    GEO_CONVERSION_SERVICE_ANNOTATION);

            // Add annotation to target type
            detailsBuilder.addAnnotation(annotationBuilder.build());

            // Save changes to disk
            typeManagementService.createOrUpdateTypeOnDisk(detailsBuilder
                    .build());
        }
    }

    /**
     * This method updates Pom dependencies and repositories
     */
    public void updatePomDependencies() {
        final Element configuration = XmlUtils.getConfiguration(getClass());
        GeoUtils.updatePom(configuration, projectOperations, metadataService);
    }

    /**
     * This method install necessary components on correct folders
     */
    public void installComponents() {
        PathResolver pathResolver = projectOperations.getPathResolver();
        LogicalPath webappPath = getWebappPath();

        // Copy Javascript files and related resources
        OperationUtils.updateDirectoryContents("scripts/leaflet/*.js",
                pathResolver.getIdentifier(webappPath, "/scripts/leaflet"),
                fileManager, context, getClass());
        OperationUtils.updateDirectoryContents("scripts/leaflet/images/*.png",
                pathResolver.getIdentifier(webappPath,
                        "/scripts/leaflet/images"), fileManager, context,
                getClass());
        // Copy Styles files and related resources
        OperationUtils.updateDirectoryContents("styles/leaflet/*.css",
                pathResolver.getIdentifier(webappPath, "/styles/leaflet"),
                fileManager, context, getClass());
        OperationUtils.updateDirectoryContents("styles/leaflet/images/*.png",
                pathResolver
                        .getIdentifier(webappPath, "/styles/leaflet/images"),
                fileManager, context, getClass());
        // Copy necessary fonts
        OperationUtils.updateDirectoryContents("styles/fonts/*.*",
                pathResolver.getIdentifier(webappPath, "/styles/fonts"),
                fileManager, context, getClass());
        // Copy images into images folder
        OperationUtils.updateDirectoryContents("images/*.*",
                pathResolver.getIdentifier(webappPath, "/images"), fileManager,
                context, getClass());
        // Copy tags into tags folder
        OperationUtils.updateDirectoryContents("tags/geo/*.tagx",
                pathResolver.getIdentifier(webappPath, "/WEB-INF/tags/geo"),
                fileManager, context, getClass());
        OperationUtils.updateDirectoryContents("tags/geo/layers/*.tagx",
                pathResolver.getIdentifier(webappPath,
                        "/WEB-INF/tags/geo/layers"), fileManager, context,
                getClass());
        OperationUtils.updateDirectoryContents("tags/geo/tools/*.tagx",
                pathResolver.getIdentifier(webappPath,
                        "/WEB-INF/tags/geo/tools"), fileManager, context,
                getClass());

        // Add sources to loadScripts
        addToLoadScripts("js_leaflet_geo_js",
                "/resources/scripts/leaflet/leaflet.js", false);
        addToLoadScripts("js_leaflet_ext_gvnix_url",
                "/resources/scripts/leaflet/leaflet.ext.gvnix.map.js", false);
        addToLoadScripts(
                "js_leaflet_ext_gvnix_measure_tool_url",
                "/resources/scripts/leaflet/leaflet.ext.gvnix.map.measure.tool.js",
                false);
        addToLoadScripts(
                "js_leaflet_ext_gvnix_generic_tool_url",
                "/resources/scripts/leaflet/leaflet.ext.gvnix.map.generic.tool.js",
                false);
        addToLoadScripts("js_leaflet_geo_omnivore_js",
                "/resources/scripts/leaflet/leaflet-omnivore.min.js", false);
        addToLoadScripts("js_leaflet_geo_awesome_markers_js",
                "/resources/scripts/leaflet/leaflet.awesome-markers.min.js",
                false);
        addToLoadScripts("js_leaflet_geo_marker_cluster_js",
                "/resources/scripts/leaflet/leaflet.markercluster-src.js",
                false);
        addToLoadScripts("js_leaflet_zoom_slider_js",
                "/resources/scripts/leaflet/L.Control.Zoomslider.js", false);
        addToLoadScripts("js_leaflet_measuring_tool_js",
                "/resources/scripts/leaflet/L.MeasuringTool.js", false);
        addToLoadScripts("styles_leaflet_geo_css",
                "/resources/styles/leaflet/leaflet.css", true);
        addToLoadScripts("styles_gvnix_leaflet_geo_css",
                "/resources/styles/leaflet/gvnix.leaflet.css", true);
        addToLoadScripts("styles_leaflet_font_css",
                "/resources/styles/leaflet/font-awesome.min.css", true);
        addToLoadScripts("styles_leaflet_markers_css",
                "/resources/styles/leaflet/leaflet.awesome-markers.css", true);
        addToLoadScripts("styles_marker_cluster_css",
                "/resources/styles/leaflet/MarkerCluster.css", true);
        addToLoadScripts("styles_marker_cluster_default_css",
                "/resources/styles/leaflet/MarkerCluster.Default.css", true);
        addToLoadScripts("styles_zoom_slider_css",
                "/resources/styles/leaflet/L.Control.Zoomslider.css", true);
    }

    /**
     * This method adds reference in laod-script.tagx to use
     * jquery.loupeField.ext.gvnix.js
     */
    public void addToLoadScripts(String varName, String url, boolean isCSS) {
        // Modify Roo load-scripts.tagx
        String docTagxPath = pathResolver.getIdentifier(getWebappPath(),
                "WEB-INF/tags/util/load-scripts.tagx");

        Validate.isTrue(fileManager.exists(docTagxPath),
                "load-script.tagx not found: ".concat(docTagxPath));

        MutableFile docTagxMutableFile = null;
        Document docTagx;

        try {
            docTagxMutableFile = fileManager.updateFile(docTagxPath);
            docTagx = XmlUtils.getDocumentBuilder().parse(
                    docTagxMutableFile.getInputStream());
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Element root = docTagx.getDocumentElement();

        boolean modified = false;

        if (isCSS) {
            modified = WebProjectUtils.addCssToTag(docTagx, root, varName, url)
                    || modified;
        }
        else {
            modified = WebProjectUtils.addJSToTag(docTagx, root, varName, url)
                    || modified;
        }

        if (modified) {
            XmlUtils.writeXml(docTagxMutableFile.getOutputStream(), docTagx);
        }

    }

    /**
     * This method add necessary properties to messages.properties
     */
    public void addI18nComponentsProperties() {
        // Check if Valencian_Catalan language is supported and add properties
        // if so
        Set<I18n> supportedLanguages = i18nSupport.getSupportedLanguages();
        for (I18n i18n : supportedLanguages) {
            if (i18n.getLocale().equals(new Locale("ca"))) {
                MessageBundleUtils.installI18nMessages(
                        new ValencianCatalanLanguage(), projectOperations,
                        fileManager);
                MessageBundleUtils.addPropertiesToMessageBundle("ca",
                        getClass(), propFileOperations, projectOperations,
                        fileManager);
                break;
            }
        }
        // Add properties to Spanish messageBundle
        MessageBundleUtils.installI18nMessages(new SpanishLanguage(),
                projectOperations, fileManager);
        MessageBundleUtils.addPropertiesToMessageBundle("es", getClass(),
                propFileOperations, projectOperations, fileManager);

        // Add properties to default messageBundle
        MessageBundleUtils.addPropertiesToMessageBundle("en", getClass(),
                propFileOperations, projectOperations, fileManager);
    }

    /**
     * This method add necessary properties to messages.properties for
     * Controller
     */
    public void addI18nControllerProperties(String controllerPackage,
            String path) {

        Map<String, String> propertyList = new HashMap<String, String>();
        propertyList.put(
                String.format("label_%s_%s",
                        controllerPackage.replaceAll("[.]", "_"), path),
                "Entity Map Viewer");
        propertyList.put(
                String.format("label_%s_%s_toc",
                        controllerPackage.replaceAll("[.]", "_"), path),
                "Layers");

        propFileOperations.addProperties(getWebappPath(),
                "WEB-INF/i18n/application.properties", propertyList, true,
                false);

    }

    /**
     * This method annotate MapControllers with entities to represent
     * 
     * @param mapControllersToAnnotate
     * @param typeLocationService
     * @param typeManagementService
     * @param entity
     */
    private void annotateMapController(JavaType mapController,
            TypeLocationService typeLocationService,
            TypeManagementService typeManagementService, JavaType controller) {

        ClassOrInterfaceTypeDetails mapControllerDetails = typeLocationService
                .getTypeDetails(mapController);

        // Getting @GvNIXMapViewer Annotation
        AnnotationMetadata mapViewerAnnotation = mapControllerDetails
                .getAnnotation(MAP_VIEWER_ANNOTATION);

        // Generating new annotation
        ClassOrInterfaceTypeDetailsBuilder classOrInterfaceTypeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                mapControllerDetails);
        AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(
                MAP_VIEWER_ANNOTATION);

        // Getting current entities
        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArrayAttributeValue<ClassAttributeValue> mapViewerAttributesOld = (ArrayAttributeValue) mapViewerAnnotation
                .getAttribute("entityLayers");

        // Initialize class attributes list for detail fields
        final List<ClassAttributeValue> entityAttributes = new ArrayList<ClassAttributeValue>();
        boolean notIncluded = true;

        if (mapViewerAttributesOld != null) {
            // Adding by default old entities
            entityAttributes.addAll(mapViewerAttributesOld.getValue());
            // Checking that current entity is not included yet
            List<ClassAttributeValue> mapViewerAttributesOldValues = mapViewerAttributesOld
                    .getValue();
            for (ClassAttributeValue currentEntity : mapViewerAttributesOldValues) {
                if (currentEntity.getValue().equals(controller)) {
                    notIncluded = false;
                    break;
                }
            }
        }

        // If current entity is not included in old values, include to new
        // annotation
        if (notIncluded) {
            // Create a class attribute for property
            final ClassAttributeValue entityAttribute = new ClassAttributeValue(
                    new JavaSymbolName("value"), controller);
            entityAttributes.add(entityAttribute);
        }

        // Create "entityLayers" attributes array from string attributes
        // list
        ArrayAttributeValue<ClassAttributeValue> entityLayersArray = new ArrayAttributeValue<ClassAttributeValue>(
                new JavaSymbolName("entityLayers"), entityAttributes);

        annotationBuilder.addAttribute(entityLayersArray);
        annotationBuilder.build();

        // Update annotation into controller
        classOrInterfaceTypeDetailsBuilder
                .updateTypeAnnotation(annotationBuilder);

        // Save controller changes to disk
        typeManagementService
                .createOrUpdateTypeOnDisk(classOrInterfaceTypeDetailsBuilder
                        .build());

    }

    /**
     * This method checks if exists some map element
     * 
     * @return
     */
    public boolean checkExistsMapElement() {
        // If not exists any class with @GvNIXMapViewer annotation, return false
        return !typeLocationService
                .findClassesOrInterfaceDetailsWithAnnotation(
                        MAP_VIEWER_ANNOTATION).isEmpty();
    }

    /**
     * Creates an instance with the {@code src/main/webapp} path in the current
     * module
     * 
     * @return
     */
    public LogicalPath getWebappPath() {
        return WebProjectUtils.getWebappPath(projectOperations);
    }

    // Feature methods -----

    /**
     * Gets the feature name managed by this operations class.
     * 
     * @return feature name
     */
    @Override
    public String getName() {
        return FEATURE_NAME_GVNIX_GEO_WEB_MVC;
    }

    /**
     * Returns true if GEO is installed
     */
    @Override
    public boolean isInstalledInModule(String moduleName) {
        String dirPath = pathResolver.getIdentifier(getWebappPath(),
                "scripts/leaflet/leaflet.js");
        return fileManager.exists(dirPath);
    }

}