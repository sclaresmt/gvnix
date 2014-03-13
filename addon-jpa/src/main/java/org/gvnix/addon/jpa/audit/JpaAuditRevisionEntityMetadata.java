/*
 * gvNIX. Spring Roo based RAD tool for Generalitat Valenciana
 * Copyright (C) 2013 Generalitat Valenciana
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
 * along with this program.  If not, see <http://www.gnu.org/copyleft/gpl.html>.
 */
package org.gvnix.addon.jpa.audit;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.gvnix.addon.jpa.audit.providers.RevisionLogRevisionEntityMetadataBuilder;
import org.gvnix.support.ItdBuilderHelper;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.LogicalPath;

/**
 * ITD generator for {@link GvNIXJpaAuditRevisionEntity} annotation.
 * 
 * @author gvNIX Team
 * @since 1.3.0
 */
public class JpaAuditRevisionEntityMetadata extends
        AbstractItdTypeDetailsProvidingMetadataItem {

    // Constants
    private static final String PROVIDES_TYPE_STRING = JpaAuditRevisionEntityMetadata.class
            .getName();
    private static final String PROVIDES_TYPE = MetadataIdentificationUtils
            .create(PROVIDES_TYPE_STRING);

    public static final String getMetadataIdentiferType() {
        return PROVIDES_TYPE;
    }

    public static final String createIdentifier(JavaType javaType,
            LogicalPath path) {
        return PhysicalTypeIdentifierNamingUtils.createIdentifier(
                PROVIDES_TYPE_STRING, javaType, path);
    }

    public static final JavaType getJavaType(String metadataIdentificationString) {
        return PhysicalTypeIdentifierNamingUtils.getJavaType(
                PROVIDES_TYPE_STRING, metadataIdentificationString);
    }

    public static final LogicalPath getPath(String metadataIdentificationString) {
        return PhysicalTypeIdentifierNamingUtils.getPath(PROVIDES_TYPE_STRING,
                metadataIdentificationString);
    }

    public static boolean isValid(String metadataIdentificationString) {
        return PhysicalTypeIdentifierNamingUtils.isValid(PROVIDES_TYPE_STRING,
                metadataIdentificationString);
    }

    /**
     * Itd builder herlper
     */
    private final ItdBuilderHelper helper;

    private final JpaAuditRevisionEntityAnnotationValues annotationValues;
    private final RevisionLogRevisionEntityMetadataBuilder revisionLogBuilder;

    private Context buildContext;

    public JpaAuditRevisionEntityMetadata(String identifier,
            JavaType aspectName,
            PhysicalTypeMetadata governorPhysicalTypeMetadata,
            JpaAuditRevisionEntityAnnotationValues annotationValues,
            RevisionLogRevisionEntityMetadataBuilder revisionLogBuilder) {
        super(identifier, aspectName, governorPhysicalTypeMetadata);
        Validate.isTrue(isValid(identifier), "Metadata identification string '"
                + identifier + "' does not appear to be a valid");

        // Helper itd generation
        this.helper = new ItdBuilderHelper(this, governorPhysicalTypeMetadata,
                builder.getImportRegistrationResolver());

        this.annotationValues = annotationValues;

        this.revisionLogBuilder = revisionLogBuilder;

        this.buildContext = new Context(getId(), helper, this.annotationValues,
                governorPhysicalTypeMetadata.getType());

        this.revisionLogBuilder.initialize(builder, buildContext);

        this.revisionLogBuilder.fillEntity();

        this.revisionLogBuilder.done();

        // Create a representation of the desired output ITD
        itdTypeDetails = builder.build();
    }

    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder(this);
        builder.append("identifier", getId());
        builder.append("valid", valid);
        builder.append("aspectName", aspectName);
        builder.append("destinationType", destination);
        builder.append("governor", governorPhysicalTypeMetadata.getId());
        builder.append("itdTypeDetails", itdTypeDetails);
        return builder.toString();
    }

    /**
     * Gets final names to use of a type in method body after import resolver.
     * 
     * @param type
     * @return name to use in method body
     */
    @SuppressWarnings("unused")
    private String getFinalTypeName(JavaType type) {
        return type.getNameIncludingTypeParameters(false,
                builder.getImportRegistrationResolver());
    }

    /**
     * @return annotation values of metadata
     */
    public JpaAuditRevisionEntityAnnotationValues getAnnotationValues() {
        return annotationValues;
    }

    /**
     * Class which contains generation time metadata information useful for
     * {@link RevisionLogRevisionEntityMetadataBuilder}
     * 
     * @author gvNIX Team
     * 
     */
    public static class Context {

        private final ItdBuilderHelper helper;

        private final JpaAuditRevisionEntityAnnotationValues annotationValues;

        private final String metadataId;

        private final JavaType entity;

        public Context(String metadataId, ItdBuilderHelper helper,
                JpaAuditRevisionEntityAnnotationValues annotationValues,
                JavaType entity) {
            super();
            this.metadataId = metadataId;
            this.helper = helper;
            this.annotationValues = annotationValues;
            this.entity = entity;
        }

        /**
         * @return the helper
         */
        public ItdBuilderHelper getHelper() {
            return helper;
        }

        /**
         * @return the annotationValues
         */
        public JpaAuditRevisionEntityAnnotationValues getAnnotationValues() {
            return annotationValues;
        }

        /**
         * @return metadataId
         */
        public String getMetadataId() {
            return metadataId;
        }

        /**
         * @return entity
         */
        public JavaType getEntity() {
            return entity;
        }
    }

    /**
     * @return revision entity JavaType
     */
    public JavaType getType() {
        return governorPhysicalTypeMetadata.getType();
    }

    /**
     * @return current RevisionLog builder
     */
    public RevisionLogRevisionEntityMetadataBuilder getRevisionLogBuilder() {
        return revisionLogBuilder;
    }
}
