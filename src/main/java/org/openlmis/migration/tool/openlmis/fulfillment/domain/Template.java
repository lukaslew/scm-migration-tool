/*
 * This program is part of the OpenLMIS logistics management information system platform software.
 * Copyright © 2017 VillageReach
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU Affero General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 * See the GNU Affero General Public License for more details. You should have received a copy of
 * the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses.  For additional information contact info@OpenLMIS.org. 
 */

package org.openlmis.migration.tool.openlmis.fulfillment.domain;


import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.openlmis.migration.tool.openlmis.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

@Entity
@Table(name = "templates", schema = "fulfillment")
@NoArgsConstructor
@AllArgsConstructor
public class Template extends BaseEntity {

  @Column(columnDefinition = TEXT_COLUMN_DEFINITION, unique = true, nullable = false)
  @Getter
  @Setter
  private String name;

  @Column
  @Getter
  @Setter
  private byte[] data;

  @OneToMany(
      mappedBy = "template",
      cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.REMOVE},
      fetch = FetchType.EAGER,
      orphanRemoval = true)
  @Fetch(FetchMode.SELECT)
  @Getter
  @Setter
  private List<TemplateParameter> templateParameters;

  @Column(columnDefinition = TEXT_COLUMN_DEFINITION)
  @Getter
  @Setter
  private String type;

  @Column(columnDefinition = TEXT_COLUMN_DEFINITION)
  @Getter
  @Setter
  private String description;

  @PrePersist
  private void prePersist() {
    forEachParameter(line -> line.setTemplate(this));
  }

  @PreUpdate
  private void preUpdate() {
    forEachParameter(line -> line.setTemplate(this));
  }

  /**
   * Copy values of attributes into new or updated Template.
   *
   * @param template Template with new values.
   */
  public void updateFrom(Template template) {
    this.name = template.getName();
    this.data = template.getData();
    this.templateParameters = template.getTemplateParameters();
    this.type = template.getType();
    this.description = template.getDescription();
  }

  public void forEachParameter(Consumer<TemplateParameter> consumer) {
    Optional.ofNullable(templateParameters)
        .ifPresent(list -> list.forEach(consumer));
  }

}
