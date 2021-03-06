package org.openlmis.migration.tool.openlmis.requisition.repository;

import org.openlmis.migration.tool.openlmis.requisition.domain.Requisition;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface OlmisRequisitionRepository extends CrudRepository<Requisition, UUID> {

  List<Requisition> findByFacilityIdAndProgramIdAndProcessingPeriodId(UUID facilityId,
                                                                      UUID programId,
                                                                      UUID processingPeriodId);

}
