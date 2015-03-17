// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.util.SystemLog;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

@Singleton
class ImportLog implements LifecycleListener {
  private static final String IMPORT_LOG_NAME = "import_log";
  private static final Logger log = LogManager.getLogger(IMPORT_LOG_NAME);

  public static String ACCOUNT_ID = "accountId";
  public static String USER_NAME = "userName";
  public static String FROM = "from";
  public static String SRC_PROJECT_NAME = "srcProjectName";
  public static String TARGET_PROJECT_NAME = "targetProjectName";
  public static String ERROR = "error";

  private final SystemLog systemLog;
  private final ServerInformation serverInfo;
  private final AuditService auditService;
  private boolean started;

  @Inject
  public ImportLog(SystemLog systemLog,
      ServerInformation serverInfo,
      AuditService auditService) {
    this.systemLog = systemLog;
    this.serverInfo = serverInfo;
    this.auditService = auditService;
  }

  public void onImport(IdentifiedUser user, Project.NameKey srcProject,
      Project.NameKey targetProject, String from) {
    onImport(user, srcProject, targetProject, from, null);
  }

  public void onImport(IdentifiedUser user, Project.NameKey srcProject,
      Project.NameKey targetProject, String from, Exception ex) {
    long ts = TimeUtil.nowMs();
    LoggingEvent event = new LoggingEvent( //
        Logger.class.getName(), // fqnOfCategoryClass
        log, // logger
        ts, // when
        ex == null // level
            ? Level.INFO
            : Level.ERROR,
        ex == null // message text
            ? "OK"
            : "FAIL",
        Thread.currentThread().getName(), // thread name
        null, // exception information
        null, // current NDC string
        null, // caller location
        null // MDC properties
        );

    event.setProperty(ACCOUNT_ID, user.getAccountId().toString());
    event.setProperty(USER_NAME, user.getUserName());
    event.setProperty(FROM, from);
    event.setProperty(SRC_PROJECT_NAME, srcProject.get());
    event.setProperty(TARGET_PROJECT_NAME, targetProject.get());

    if (ex != null) {
      event.setProperty(ERROR, ex.toString());
    }

    log.callAppenders(event);

    audit(user, ts, srcProject, from, ex);
  }

  private void audit(IdentifiedUser user, long ts, Project.NameKey project,
      String from, Exception ex) {
    Multimap<String, Object> params = HashMultimap.create();
    params.put("class", ImportLog.class);
    params.put("project", project.get());
    params.put("from", from);

    auditService.dispatch(
        new AuditEvent(
            null, // sessionId
            user, // who
            ex == null // what
                ? "ProjectImport"
                : "ProjectImportFailure",
            ts, // when
            params, // params
            ex != null // result
                ? ex.toString()
                : "OK"
        ));
  }

  @Override
  public void start() {
    if (!started) {
      Logger importLogger = LogManager.getLogger(IMPORT_LOG_NAME);
      importLogger.removeAllAppenders();
      importLogger.addAppender(systemLog.createAsyncAppender(
          importLogger.getName(), new ImportLogLayout()));
      importLogger.setAdditivity(false);
      started = true;
    }
  }

  @Override
  public void stop() {
    // stop() is called when the plugin is unloaded or when the server is
    // shutdown. Only clean up when the server is shutting down to prevent
    // issues when the plugin is reloaded. Otherwise when Gerrit loads the new
    // plugin and then unloads the old one, the unload of the old plugin would
    // remove the appenders that were just created by the new plugin. This is
    // because the logger is static.
    if (serverInfo.getState() == ServerInformation.State.SHUTDOWN) {
      LogManager.getLogger(log.getName()).removeAllAppenders();
    }
  }
}
