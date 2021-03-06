package org.openlmis.migration.tool.batch;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.openlmis.migration.tool.openlmis.requisition.domain.LineItemFieldsCalculator.calculateTotalLossesAndAdjustments;

import com.google.common.collect.Lists;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.openlmis.migration.tool.openlmis.fulfillment.domain.Order;
import org.openlmis.migration.tool.openlmis.fulfillment.domain.OrderStatus;
import org.openlmis.migration.tool.openlmis.fulfillment.domain.ProofOfDelivery;
import org.openlmis.migration.tool.openlmis.fulfillment.repository.OrderRepository;
import org.openlmis.migration.tool.openlmis.fulfillment.repository.ProofOfDeliveryRepository;
import org.openlmis.migration.tool.openlmis.referencedata.domain.Facility;
import org.openlmis.migration.tool.openlmis.referencedata.domain.FacilityTypeApprovedProduct;
import org.openlmis.migration.tool.openlmis.referencedata.domain.Orderable;
import org.openlmis.migration.tool.openlmis.referencedata.domain.ProcessingPeriod;
import org.openlmis.migration.tool.openlmis.referencedata.domain.Program;
import org.openlmis.migration.tool.openlmis.referencedata.domain.StockAdjustmentReason;
import org.openlmis.migration.tool.openlmis.referencedata.domain.User;
import org.openlmis.migration.tool.openlmis.referencedata.repository.OlmisFacilityRepository;
import org.openlmis.migration.tool.openlmis.referencedata.repository.OlmisFacilityTypeApprovedProductRepository;
import org.openlmis.migration.tool.openlmis.referencedata.repository.OlmisOrderableRepository;
import org.openlmis.migration.tool.openlmis.referencedata.repository.OlmisProcessingPeriodRepository;
import org.openlmis.migration.tool.openlmis.referencedata.repository.OlmisProgramRepository;
import org.openlmis.migration.tool.openlmis.referencedata.repository.OlmisStockAdjustmentReasonRepository;
import org.openlmis.migration.tool.openlmis.referencedata.repository.OlmisUserRepository;
import org.openlmis.migration.tool.openlmis.requisition.domain.Requisition;
import org.openlmis.migration.tool.openlmis.requisition.domain.RequisitionLineItem;
import org.openlmis.migration.tool.openlmis.requisition.domain.RequisitionStatus;
import org.openlmis.migration.tool.openlmis.requisition.domain.RequisitionTemplate;
import org.openlmis.migration.tool.openlmis.requisition.domain.StatusMessage;
import org.openlmis.migration.tool.openlmis.requisition.domain.StockAdjustment;
import org.openlmis.migration.tool.openlmis.requisition.repository.OlmisRequisitionRepository;
import org.openlmis.migration.tool.openlmis.requisition.repository.OlmisRequisitionTemplateRepository;
import org.openlmis.migration.tool.openlmis.requisition.repository.OlmisStatusMessageRepository;
import org.openlmis.migration.tool.scm.domain.Adjustment;
import org.openlmis.migration.tool.scm.domain.Item;
import org.openlmis.migration.tool.scm.domain.Main;
import org.openlmis.migration.tool.scm.repository.ItemRepository;
import org.openlmis.migration.tool.scm.util.Grouping;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class MainProcessor implements ItemProcessor<Main, List<Requisition>> {
  private static final String USERNAME = "supply chain manager";

  @Autowired
  private ItemRepository itemRepository;

  @Autowired
  private OlmisFacilityRepository olmisFacilityRepository;

  @Autowired
  private OlmisProgramRepository olmisProgramRepository;

  @Autowired
  private OlmisProcessingPeriodRepository olmisProcessingPeriodRepository;

  @Autowired
  private OlmisRequisitionTemplateRepository olmisRequisitionTemplateRepository;

  @Autowired
  private OlmisStockAdjustmentReasonRepository olmisStockAdjustmentReasonRepository;

  @Autowired
  private OlmisOrderableRepository olmisOrderableRepository;

  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private ProofOfDeliveryRepository proofOfDeliveryRepository;

  @Autowired
  private OlmisRequisitionRepository olmisRequisitionRepository;

  @Autowired
  private OlmisFacilityTypeApprovedProductRepository olmisFacilityTypeApprovedProductRepository;

  @Autowired
  private OlmisUserRepository olmisUserRepository;

  @Autowired
  private OlmisStatusMessageRepository olmisStatusMessageRepository;

  /**
   * Converts the given {@link Main} object into {@link Requisition} object.
   */
  @Override
  public List<Requisition> process(Main main) {
    List<Item> items = itemRepository.findByProcessingDateAndFacility(
        main.getId().getProcessingDate(), main.getId().getFacility()
    );

    return Grouping
        .groupByCategoryName(items, item -> item.getCategoryProduct().getProgram().getName())
        .asMap()
        .entrySet()
        .stream()
        .map(entry -> createRequisition(entry.getKey(), entry.getValue(), main))
        .collect(Collectors.toList());
  }

  private Requisition createRequisition(String programCode, Collection<Item> items, Main main) {
    org.openlmis.migration.tool.scm.domain.Facility mainFacility = main.getId().getFacility();
    Facility facility = olmisFacilityRepository.findByCode(mainFacility.getCode());

    Program program = olmisProgramRepository.findByName(programCode);

    Requisition requisition = new Requisition();
    requisition.setFacilityId(facility.getId());
    requisition.setProgramId(program.getId());
    // TODO: each product tracking form should be treated as a standard requsition?
    // if there are emergency requisitions how to handle them?
    // where is the difference?
    requisition.setEmergency(false);
    requisition.setStatus(RequisitionStatus.INITIATED);

    ProcessingPeriod period = olmisProcessingPeriodRepository
        .findByStartDate(main.getId().getProcessingDate().toLocalDate().with(firstDayOfMonth()));

    requisition.setProcessingPeriodId(period.getId());
    requisition.setNumberOfMonthsInPeriod(period.getDurationInMonths());

    Collection<FacilityTypeApprovedProduct> approvedProducts =
        olmisFacilityTypeApprovedProductRepository.searchProducts(
            facility.getId(), program.getId(), true);

    RequisitionTemplate template = olmisRequisitionTemplateRepository
        .findByProgramId(program.getId());

    int numberOfPreviousPeriodsToAverage;
    List<Requisition> previousRequisitions;
    if (template.getNumberOfPeriodsToAverage() == null) {
      numberOfPreviousPeriodsToAverage = 0;
      previousRequisitions = getRecentRequisitions(requisition, 1);
    } else {
      numberOfPreviousPeriodsToAverage = template.getNumberOfPeriodsToAverage() - 1;
      previousRequisitions =
          getRecentRequisitions(requisition, numberOfPreviousPeriodsToAverage);
    }

    if (numberOfPreviousPeriodsToAverage > previousRequisitions.size()) {
      numberOfPreviousPeriodsToAverage = previousRequisitions.size();
    }

    //    ProofOfDeliveryDto pod = getProofOfDeliveryDto(emergency, requisition);

    requisition.initiate(template, approvedProducts, previousRequisitions,
        numberOfPreviousPeriodsToAverage, null);

    // TODO: should we handle non full supply products?
    // requisition.setAvailableNonFullSupplyProducts(approvedProductReferenceDataService
    //     .getApprovedProducts(facility.getId(), program.getId(), false)
    //     .stream()
    //     .map(ap -> ap.getProgramOrderable().getOrderableId())
    //     .collect(Collectors.toSet()));

    requisition.setCreatedDate(safeNull(main.getCreatedDate()));
    requisition.setModifiedDate(safeNull(main.getModifiedDate()));

    requisition
        .getRequisitionLineItems()
        .forEach(line -> updateLine(line, requisition, items));

    List<Orderable> products = Lists.newArrayList(olmisOrderableRepository.findAll());

    // TODO: who create, submit, authorize, approve and convert to order?
    requisition.submit(products, null);
    requisition.authorize(products, null);
    saveStatusMessage(requisition, main, items);

    requisition.approve(null, products);

    convertToOrder(requisition);

    return requisition;
  }

  private void updateLine(RequisitionLineItem line, Requisition requisition,
                          Collection<Item> items) {
    Orderable orderable = olmisOrderableRepository.findOne(line.getOrderableId());
    Item item = items
        .stream()
        .filter(elem -> elem.getProduct().getName().equals(orderable.getName()))
        .findFirst()
        .orElse(null);

    RequisitionLineItem requisitionLineItem = new RequisitionLineItem();
    requisitionLineItem.setSkipped(null == item);

    if (null != item) {
      requisitionLineItem.setTotalReceivedQuantity(item.getReceipts());
      requisitionLineItem.setTotalConsumedQuantity(item.getDispensedQuantity());

      Program program = olmisProgramRepository.findOne(requisition.getProgramId());
      List<StockAdjustment> stockAdjustments = Lists.newArrayList();
      for (Adjustment adjustment : item.getAdjustments()) {
        StockAdjustmentReason stockAdjustmentReasonDto = olmisStockAdjustmentReasonRepository
            .findByProgramAndName(program, adjustment.getType().getCode());

        StockAdjustment stockAdjustment = new StockAdjustment();
        stockAdjustment.setReasonId(stockAdjustmentReasonDto.getId());
        stockAdjustment.setQuantity(adjustment.getQuantity());

        stockAdjustments.add(stockAdjustment);
      }

      requisitionLineItem.setStockAdjustments(stockAdjustments);
      requisitionLineItem.setTotalLossesAndAdjustments(
          calculateTotalLossesAndAdjustments(
              requisitionLineItem,
              Lists.newArrayList(olmisStockAdjustmentReasonRepository.findAll())
          )
      );
      requisitionLineItem.setTotalStockoutDays(item.getStockedOutDays().intValue());
      requisitionLineItem.setStockOnHand(item.getClosingBalance());
      requisitionLineItem.setCalculatedOrderQuantity(item.getCalculatedRequiredQuantity());
      requisitionLineItem.setRequestedQuantity(item.getRequiredQuantity());
      requisitionLineItem.setRequestedQuantityExplanation("migrated from SCM");
      requisitionLineItem.setAdjustedConsumption(item.getAdjustedDispensedQuantity());
    }

    line.updateFrom(requisitionLineItem);
  }

  private ZonedDateTime safeNull(LocalDateTime dateTime) {
    // TODO: what shoule be zone used? UTC? SAST (UTC+2)?
    return null == dateTime
        ? null
        : dateTime.atZone(ZoneId.of("UTC"));
  }

  private void convertToOrder(Requisition requisition) {
    // TODO: change that (or validate this is correct)
    requisition.setSupplyingFacilityId(requisition.getFacilityId());
    requisition.setStatus(RequisitionStatus.RELEASED);

    Order order = Order.newOrder(requisition);
    order.setStatus(OrderStatus.RECEIVED);
    // TODO: how to set order code without requisition ID
    order.setOrderCode("O" + requisition.getId() + "R" + RandomStringUtils.random(10));

    // TODO: determine proper values for those properties
    ProofOfDelivery proofOfDelivery = new ProofOfDelivery(order);
    proofOfDelivery.setDeliveredBy(null);
    proofOfDelivery.setReceivedBy(null);
    proofOfDelivery.setReceivedDate(null);

    proofOfDelivery
        .getProofOfDeliveryLineItems()
        .forEach(line -> line.setQuantityReceived(null));

    orderRepository.save(order);
    proofOfDeliveryRepository.save(proofOfDelivery);
  }

  private List<Requisition> getRecentRequisitions(Requisition requisition, int amount) {
    List<ProcessingPeriod> previousPeriods =
        findPreviousPeriods(requisition.getProcessingPeriodId(), amount);

    List<Requisition> recentRequisitions = new ArrayList<>();
    for (ProcessingPeriod period : previousPeriods) {
      List<Requisition> requisitionsByPeriod = getRequisitionsByPeriod(requisition, period);
      if (!requisitionsByPeriod.isEmpty()) {
        Requisition requisitionByPeriod = requisitionsByPeriod.get(0);
        recentRequisitions.add(requisitionByPeriod);
      }
    }
    return recentRequisitions;
  }

  private List<ProcessingPeriod> findPreviousPeriods(UUID periodId, int amount) {
    ProcessingPeriod period = olmisProcessingPeriodRepository.findOne(periodId);

    if (null == period) {
      return Collections.emptyList();
    }

    Collection<ProcessingPeriod> collection = olmisProcessingPeriodRepository
        .findByProcessingScheduleAndStartDate(
            period.getProcessingSchedule(),
            period.getStartDate()
        );

    if (null == collection || collection.isEmpty()) {
      return Collections.emptyList();
    }

    // create a list...
    List<ProcessingPeriod> list = new ArrayList<>(collection);
    // ...remove the latest period from the list...
    list.removeIf(p -> p.getId().equals(periodId));
    // .. and sort elements by startDate property DESC.
    list.sort(Comparator.comparing(ProcessingPeriod::getStartDate).reversed());

    if (amount > list.size()) {
      return list;
    }

    return list.subList(0, amount);
  }

  private List<Requisition> getRequisitionsByPeriod(Requisition requisition,
                                                    ProcessingPeriod period) {
    return olmisRequisitionRepository.findByFacilityIdAndProgramIdAndProcessingPeriodId(
        requisition.getFacilityId(), requisition.getProgramId(), period.getId()
    );
  }

  private void saveStatusMessage(Requisition requisition, Main main, Collection<Item> items) {
    List<String> notes = Lists.newArrayList();
    notes.add(main.getNotes());

    items
        .forEach(item -> {
          notes.add(item.getNote());

          item
              .getNotes()
              .forEach(comment -> notes.add(
                  comment.getType().getName() + ": " + comment.getComment()
              ));
        });

    notes.removeIf(StringUtils::isBlank);

    String message = notes.stream().collect(Collectors.joining("; "));

    if (isNotBlank(message)) {
      User user = olmisUserRepository.findByUsername(USERNAME);

      StatusMessage newStatusMessage = StatusMessage.newStatusMessage(
          requisition,
          user.getId(),
          user.getFirstName(),
          user.getLastName(),
          message);

      olmisStatusMessageRepository.save(newStatusMessage);
    }
  }


}
