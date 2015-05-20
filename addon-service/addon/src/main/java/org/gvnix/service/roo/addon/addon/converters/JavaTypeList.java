/*
 * gvNIX is an open source tool for rapid application development (RAD).
 * Copyright (C) 2010 Generalitat Valenciana
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.gvnix.service.roo.addon.addon.converters;

import java.util.ArrayList;
import java.util.List;

import org.springframework.roo.model.JavaType;

/**
 * @author <a href="http://www.disid.com">DISID Corporation S.L.</a> made for <a
 *         href="http://www.dgti.gva.es">General Directorate for Information
 *         Technologies (DGTI)</a>
 */
public class JavaTypeList {

    private List<JavaType> javaTypes;

    public JavaTypeList() {
        super();
        javaTypes = new ArrayList<JavaType>();
    }

    public List<JavaType> getJavaTypes() {
        return javaTypes;
    }

    public void setJavaTypes(List<JavaType> javaTypes) {
        this.javaTypes = javaTypes;
    }

}
