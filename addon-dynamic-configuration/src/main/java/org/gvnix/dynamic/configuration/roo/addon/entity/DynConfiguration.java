package org.gvnix.dynamic.configuration.roo.addon.entity;

import java.util.ArrayList;
import java.util.List;

public class DynConfiguration {

  private DynComponent component;
  private List<DynProperty> properties;
  
  
  public DynConfiguration() {
    
    super();
    this.component = new DynComponent();
    this.properties = new ArrayList<DynProperty>();
  }

  public DynComponent getComponent() {
    return component;
  }

  public void setComponent(DynComponent dynComponent) {
    this.component = dynComponent;
  }

  public List<DynProperty> getProperties() {
    return properties;
  }

  public void setProperties(List<DynProperty> dynProperties) {
    this.properties = dynProperties;
  }

}
