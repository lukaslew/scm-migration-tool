package org.openlmis.migration.tool.batch;

import org.springframework.boot.autoconfigure.batch.BatchDatabaseInitializer;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.context.ApplicationContext;

public class AppBatchDatabaseInitializer extends BatchDatabaseInitializer {

  public AppBatchDatabaseInitializer(AppBatchConfigurer batchConfigurer,
                                     ApplicationContext applicationContext,
                                     BatchProperties batchProperties) {
    super(batchConfigurer.getDataSource(), applicationContext, batchProperties);
  }

}
