/* This file is part of VoltDB.
 * Copyright (C) 2008-2011 VoltDB Inc.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

/* WARNING: THIS FILE IS AUTO-GENERATED
            DO NOT MODIFY THIS SOURCE
            ALL CHANGES MUST BE MADE IN THE CATALOG GENERATOR */

package org.voltdb.catalog;

/**
 * Container for deployment systemsettings element
 */
public class Systemsettings extends CatalogType {

    int m_maxtemptablesize;

    void setBaseValues(Catalog catalog, CatalogType parent, String path, String name) {
        super.setBaseValues(catalog, parent, path, name);
        m_fields.put("maxtemptablesize", m_maxtemptablesize);
    }

    void update() {
        m_maxtemptablesize = (Integer) m_fields.get("maxtemptablesize");
    }

    /** GETTER: The maximum allocation size for temp tables in the EE */
    public int getMaxtemptablesize() {
        return m_maxtemptablesize;
    }

    /** SETTER: The maximum allocation size for temp tables in the EE */
    public void setMaxtemptablesize(int value) {
        m_maxtemptablesize = value; m_fields.put("maxtemptablesize", value);
    }

}
