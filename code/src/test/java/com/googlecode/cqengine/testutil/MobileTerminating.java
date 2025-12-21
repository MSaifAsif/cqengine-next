/*
 * Copyright 2025 Saif Asif
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.cqengine.testutil;

import com.googlecode.cqengine.attribute.SimpleAttribute;
import com.googlecode.cqengine.query.option.QueryOptions;

public class MobileTerminating {

    private String prefix;
    private String operatorName;
    private String region;
    private String zone;
    
    public MobileTerminating(String prefix, String operatorName, String region, String zone) {
        super();
        this.prefix = prefix;
        this.operatorName = operatorName;
        this.region = region;
        this.zone = zone;
    }
    /**
     * @return the prefix
     */
    public String getPrefix() {
        return prefix;
    }
    /**
     * @param prefix the prefix to set
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    /**
     * @return the operatorName
     */
    public String getOperatorName() {
        return operatorName;
    }
    /**
     * @param operatorName the operatorName to set
     */
    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }
    /**
     * @return the region
     */
    public String getRegion() {
        return region;
    }
    /**
     * @param region the region to set
     */
    public void setRegion(String region) {
        this.region = region;
    }
    /**
     * @return the zone
     */
    public String getZone() {
        return zone;
    }
    /**
     * @param zone the zone to set
     */
    public void setZone(String zone) {
        this.zone = zone;
    }
    
    public static final SimpleAttribute<MobileTerminating, String> PREFIX = new SimpleAttribute<MobileTerminating, String>("prefix") {
        @Override
        public String getValue(MobileTerminating object, QueryOptions queryOptions) {
            return object.getPrefix();
        }
    };
    
    public static final SimpleAttribute<MobileTerminating, String> OPERATOR_NAME = new SimpleAttribute<MobileTerminating, String>("operatorName") {
        @Override
        public String getValue(MobileTerminating object, QueryOptions queryOptions) {
            return object.getOperatorName();
        }
    };
    
    public static final SimpleAttribute<MobileTerminating, String> REGION = new SimpleAttribute<MobileTerminating, String>("region") {
        @Override
        public String getValue(MobileTerminating object, QueryOptions queryOptions) {
            return object.getRegion();
        }
    };
    
    public static final SimpleAttribute<MobileTerminating, String> ZONE = new SimpleAttribute<MobileTerminating, String>("zone") {
        @Override
        public String getValue(MobileTerminating object, QueryOptions queryOptions) {
            return object.getZone();
        }
    };

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "MobileTerminating [prefix=" + prefix + ", operatorName=" + operatorName + ", region=" + region
                + ", zone=" + zone + "]";
    }
    
    
}
