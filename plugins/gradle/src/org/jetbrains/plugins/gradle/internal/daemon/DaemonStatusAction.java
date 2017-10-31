// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.internal.daemon;

import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.client.DaemonClientConnection;
import org.gradle.launcher.daemon.client.DaemonClientFactory;
import org.gradle.launcher.daemon.client.DaemonConnector;
import org.gradle.launcher.daemon.client.ReportStatusDispatcher;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.protocol.ReportStatus;
import org.gradle.launcher.daemon.protocol.Status;
import org.gradle.launcher.daemon.registry.DaemonInfo;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.DaemonStopEvent;
import org.gradle.launcher.daemon.registry.DaemonStopEvents;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Vladislav.Soroka
 */
public class DaemonStatusAction {
  public List<DaemonState> run(DaemonClientFactory daemonClientFactory) {
    OutputEventListener outputEventListener = new OutputEventListener() {
      @Override
      public void onOutput(OutputEvent event) { }
    };
    ServiceRegistry daemonServices = daemonClientFactory.createStopDaemonServices(
      outputEventListener, new DaemonParameters(new BuildLayoutParameters()));
    DaemonConnector daemonConnector = daemonServices.get(DaemonConnector.class);
    DaemonRegistry daemonRegistry = daemonServices.get(DaemonRegistry.class);
    IdGenerator<?> idGenerator = daemonServices.get(IdGenerator.class);
    return new ReportDaemonStatusClient(daemonRegistry, daemonConnector, idGenerator).get();
  }

  static class ReportDaemonStatusClient {
    private final DaemonRegistry daemonRegistry;
    private final DaemonConnector connector;
    private final IdGenerator<?> idGenerator;
    private final ReportStatusDispatcher reportStatusDispatcher;

    public ReportDaemonStatusClient(DaemonRegistry daemonRegistry,
                                    DaemonConnector connector,
                                    IdGenerator<?> idGenerator) {
      this.daemonRegistry = daemonRegistry;
      this.connector = connector;
      this.idGenerator = idGenerator;
      this.reportStatusDispatcher = new ReportStatusDispatcher();
    }

    public List<DaemonState> get() {
      List<DaemonState> daemons = new ArrayList<>();
      for (DaemonInfo daemon : this.daemonRegistry.getAll()) {
        DaemonClientConnection connection = this.connector.maybeConnect(daemon);
        if (connection != null) {
          DaemonInfo connectionDaemon = connection.getDaemon() instanceof DaemonInfo ? (DaemonInfo)connection.getDaemon() : daemon;
          try {
            List<String> daemonOpts = connectionDaemon.getContext().getDaemonOpts();
            File javaHome = connectionDaemon.getContext().getJavaHome();
            Integer idleTimeout = connectionDaemon.getContext().getIdleTimeout();
            File registryDir = connectionDaemon.getContext().getDaemonRegistryDir();

            ReportStatus statusCommand = new ReportStatus(this.idGenerator.generateId(), daemon.getToken());
            Status status = this.reportStatusDispatcher.dispatch(connection, statusCommand);
            if (status != null) {
              daemons.add(new DaemonState(connectionDaemon.getPid(),
                                          connectionDaemon.getToken(),
                                          status.getVersion(),
                                          status.getStatus(),
                                          null,
                                          connectionDaemon.getLastBusy().getTime(),
                                          null,
                                          daemonOpts,
                                          javaHome,
                                          idleTimeout,
                                          registryDir));
            }
            else {
              daemons.add(new DaemonState(connectionDaemon.getPid(),
                                          connectionDaemon.getToken(),
                                          "UNKNOWN",
                                          "UNKNOWN",
                                          null,
                                          connectionDaemon.getLastBusy().getTime(),
                                          null,
                                          daemonOpts,
                                          javaHome,
                                          idleTimeout,
                                          registryDir));
            }
          }
          finally {
            connection.stop();
          }
        }
      }

      List<DaemonStopEvent> stopEvents = DaemonStopEvents.uniqueRecentDaemonStopEvents(this.daemonRegistry.getStopEvents());
      for (DaemonStopEvent stopEvent : stopEvents) {
        DaemonExpirationStatus expirationStatus = stopEvent.getStatus();
        String daemonExpirationStatus =
          expirationStatus != null ? expirationStatus.name().replace("_", " ").toLowerCase(Locale.ENGLISH) : "";
        Long stopEventPid;
        if (GradleVersion.current().compareTo(GradleVersion.version("3.0")) <= 0) {
          try {
            Field pidField = stopEvent.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            stopEventPid = pidField.getLong(stopEvent);
          }
          catch (Exception ignore) {
            stopEventPid = -1L;
          }
        }
        else {
          stopEventPid = stopEvent.getPid();
        }
        daemons.add(new DaemonState(stopEventPid,
                                    null,
                                    null,
                                    "Stopped",
                                    stopEvent.getReason(),
                                    stopEvent.getTimestamp().getTime(),
                                    daemonExpirationStatus, null, null, null, null));
      }


      //if (daemonStatuses.isEmpty()) {
      //LOGGER.quiet("No Gradle daemons are running.");
      //}

      //if (!daemonStatuses.isEmpty() || !stopEvents.isEmpty()) {
      //LOGGER.quiet(String.format("%1$6s %2$-8s %3$s", "PID", "STATUS", "INFO"));
      //}

      //this.printRunningDaemons(daemonStatuses);
      //this.printStoppedDaemons(stopEvents);
      //LOGGER.quiet("");
      //LOGGER.quiet("Only Daemons for the current Gradle version are displayed. See " +
      //             this.documentationRegistry.getDocumentationFor("gradle_daemon", "sec:status"));

      return daemons;
    }

    //void printRunningDaemons(List<Status> statuses) {
    //  if (!statuses.isEmpty()) {
    //    Iterator i$ = statuses.iterator();
    //
    //    while (i$.hasNext()) {
    //      Status status = (Status)i$.next();
    //      Long pid = status.getPid();
    //      LOGGER.quiet(String.format("%1$6s %2$-8s %3$s", pid == null ? "PID unknown" : pid, status.getStatus(), status.getVersion()));
    //    }
    //  }
    //}
    //
    //void printStoppedDaemons(List<DaemonStopEvent> stopEvents) {
    //  if (!stopEvents.isEmpty()) {
    //    Iterator i$ = stopEvents.iterator();
    //
    //    while (i$.hasNext()) {
    //      DaemonStopEvent event = (DaemonStopEvent)i$.next();
    //      Long pid = event.getPid();
    //      LOGGER.quiet(String.format("%1$6s %2$-8s %3$s", pid == null ? "PID unknown" : pid, "STOPPED", "(" + event.getReason() + ")"));
    //    }
    //  }
    //}
  }
}
