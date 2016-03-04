/**
 * Copyright (C) 2011-2015 Incapture Technologies LLC
 *
 * This is an autogenerated license statement. When copyright notices appear below
 * this one that copyright supercedes this statement.
 *
 * Unless required by applicable law or agreed to in writing, software is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 *
 * Unless explicit permission obtained in writing this software cannot be distributed.
 */
package rapture.postgres.connection;

import rapture.common.exception.ExceptionToString;
import rapture.kernel.Kernel;
import rapture.metrics.MetricsService;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.log4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mchange.v2.c3p0.PooledDataSource;

import rapture.config.MultiValueConfigLoader;

/**
 * @author bardhi
 * @since 3/30/15.
 */
public class DataSourceMonitor {
    private static final Logger log = Logger.getLogger(DataSourceMonitor.class);
    private static final int DEFAULT_MONITOR_FREQUENCY = 300;

    private ScheduledExecutorService executor;
    private List<PooledDataSource> dataSources;

    public DataSourceMonitor() {
        this.dataSources = new LinkedList<>();
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("DataSourceMonitor").build());
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                for (PooledDataSource dataSource : dataSources) {
                    try {
                        reportDataSource(Kernel.getMetricsService(), dataSource);
                    } catch (Exception e) {
                        log.error(ExceptionToString.format(e));
                    }
                }
            }
        }, 10, getMonitoringFrequency(), TimeUnit.SECONDS);
    }

    private int getMonitoringFrequency() {
        return DEFAULT_MONITOR_FREQUENCY;
    }

    public void monitor(final PooledDataSource dataSource) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                dataSources.add(dataSource);
            }
        });
    }

    public void reportDataSource(MetricsService metricsService, PooledDataSource dataSource) {
        log.trace(String.format("will report on data source %s", dataSource.getDataSourceName()));
        String dataSourceName = dataSource.getDataSourceName();

        try {
            // connections
            metricsService.recordGaugeValue(parameterName(dataSourceName, "connections.numTotal"), (long) dataSource.getNumConnectionsDefaultUser());
            metricsService.recordGaugeValue(parameterName(dataSourceName, "connections.numBusy"), (long) dataSource.getNumBusyConnectionsDefaultUser());
            metricsService.recordGaugeValue(parameterName(dataSourceName, "connections.numIdle"), (long) dataSource.getNumIdleConnectionsDefaultUser());
            metricsService.recordGaugeValue(parameterName(dataSourceName, "connections.numUnclosedOrphaned"),
                    (long) dataSource.getNumUnclosedOrphanedConnectionsDefaultUser());

            // statements
            metricsService.recordGaugeValue(parameterName(dataSourceName, "statements.numTotal"), (long) dataSource.getStatementCacheNumStatementsDefaultUser());
            metricsService.recordGaugeValue(parameterName(dataSourceName, "statements.numCheckedOut"), (long) dataSource.getStatementCacheNumCheckedOutDefaultUser());
            metricsService.recordGaugeValue(parameterName(dataSourceName, "statements.numWithCached"),
                    (long) dataSource.getStatementCacheNumConnectionsWithCachedStatementsDefaultUser());

            metricsService.recordGaugeValue(parameterName(dataSourceName, "numFailedCheckouts"), dataSource.getNumFailedCheckoutsDefaultUser());
            metricsService.recordGaugeValue(parameterName(dataSourceName, "numFailedCheckins"), dataSource.getNumFailedCheckinsDefaultUser());

        } catch (SQLException e) {
            log.error(ExceptionToString.format(e));
        }
    }

    private String parameterName(String dataSourceName, String postfix) {
        return String.format("datasources.%s.%s", dataSourceName, postfix);
    }
}