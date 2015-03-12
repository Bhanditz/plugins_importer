//Copyright (C) 2015 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import static com.googlesource.gerrit.plugins.importer.ProgressMonitorUtil.updateAndEnd;
import static java.lang.String.format;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import com.googlesource.gerrit.plugins.importer.ImportProject.Input;

import org.apache.commons.validator.routines.UrlValidator;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

@RequiresCapability(ImportCapability.ID)
class ImportProject implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public String from;
    public String user;
    public String pass;
    public String parent;
  }

  interface Factory {
    ImportProject create(Project.NameKey project);
  }

  private static Logger log = LoggerFactory.getLogger(ImportProject.class);

  private final ProjectCache projectCache;
  private final OpenRepositoryStep openRepoStep;
  private final ConfigureRepositoryStep configRepoStep;
  private final GitFetchStep gitFetchStep;
  private final ConfigureProjectStep configProjectStep;
  private final ReplayChangesStep.Factory replayChangesFactory;
  private final Provider<CurrentUser> currentUser;
  private final ImportLog importLog;
  private final File lockRoot;

  private final Project.NameKey project;
  private Project.NameKey parent;
  private LockFile lockFile;

  private Writer err = null;

  @Inject
  ImportProject(
      ProjectCache projectCache,
      OpenRepositoryStep openRepoStep,
      ConfigureRepositoryStep configRepoStep,
      GitFetchStep gitFetchStep,
      ConfigureProjectStep configProjectStep,
      ReplayChangesStep.Factory replayChangesFactory,
      Provider<CurrentUser> currentUser,
      ImportLog importLog,
      @PluginData File data,
      @Assisted Project.NameKey project) {
    this.projectCache = projectCache;
    this.openRepoStep = openRepoStep;
    this.configRepoStep = configRepoStep;
    this.gitFetchStep = gitFetchStep;
    this.configProjectStep = configProjectStep;
    this.replayChangesFactory = replayChangesFactory;
    this.currentUser = currentUser;
    this.importLog = importLog;
    this.lockRoot = data;
    this.project = project;
  }

  void setErr(Writer err) {
    this.err = err;
  }
  @Override
  public Response<String> apply(ConfigResource rsrc, Input input)
      throws RestApiException, OrmException, IOException, ValidationException,
      GitAPIException, NoSuchChangeException, NoSuchAccountException {
    if (input == null) {
      input = new Input();
    }
    if (Strings.isNullOrEmpty(input.from)) {
      throw new BadRequestException("from is required");
    }
    if (!(new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS)).isValid(input.from)) {
      throw new BadRequestException("from must be a valid URL");
    }
    if (Strings.isNullOrEmpty(input.user)) {
      throw new BadRequestException("user is required");
    }
    if (Strings.isNullOrEmpty(input.pass)) {
      throw new BadRequestException("pass is required");
    }

    ProgressMonitor pm = err != null ? new TextProgressMonitor(err) :
        NullProgressMonitor.INSTANCE;

    lockFile = lockForImport(project);
    try {
      setParentProjectName(input, pm);
      checkPreconditions(pm);
      Repository repo = openRepoStep.open(project, pm);
      try {
        persistParams(input, pm);
        configRepoStep.configure(repo, project, input.from, pm);
        gitFetchStep.fetch(input.user, input.pass, repo, project, pm);
        configProjectStep.configure(project, parent, pm);
        replayChangesFactory.create(input.from, input.user, input.pass, repo,
            project, pm)
            .replay();
      } finally {
        repo.close();
      }
      importLog.onImport((IdentifiedUser) currentUser.get(), project,
          input.from);
    } catch (Exception e) {
      importLog.onImport((IdentifiedUser) currentUser.get(), project,
          input.from, e);
      log.error(format("Unable to transfer project '%s' from"
          + " source gerrit host '%s'.",
          project.get(), input.from), e);
      throw e;
    } finally {
      lockFile.unlock();
    }

    return Response.<String> ok("OK");
  }

  private void setParentProjectName(Input input, ProgressMonitor pm)
      throws IOException {
    pm.beginTask("Set parent project", 1);
    if (parent == null) {
      if (input.parent != null) {
        parent = new Project.NameKey(input.parent);
      } else {
        parent = new Project.NameKey(
            new RemoteApi(input.from, input.user, input.pass)
                .getProject(project.get()).parent);
      }
    }
    updateAndEnd(pm);
  }

  private void checkPreconditions(ProgressMonitor pm) throws ValidationException {
    pm.beginTask("Check preconditions", 1);
    ProjectState p = projectCache.get(parent);
    if (p == null) {
      throw new ValidationException(format(
          "Parent project %s does not exist in target,", parent.get()));
    }
    updateAndEnd(pm);
  }

  private void persistParams(Input input, ProgressMonitor pm) throws IOException {
    pm.beginTask("Persist parameters", 1);
    // copy input to persist it without password
    Input in = new Input();
    in.from = input.from;
    in.user = input.user;
    in.parent = input.parent;

    String s = OutputFormat.JSON_COMPACT.newGson().toJson(in);
    try (OutputStream out = lockFile.getOutputStream()) {
      out.write(s.getBytes(Charsets.UTF_8));
      out.write('\n');
    } finally {
      lockFile.commit();
    }
    updateAndEnd(pm);
  }

  private LockFile lockForImport(Project.NameKey project)
      throws ResourceConflictException {
    File importStatus = new File(lockRoot, project.get());
    LockFile lockFile = new LockFile(importStatus, FS.DETECTED);
    try {
      if (lockFile.lock()) {
        return lockFile;
      } else {
        throw new ResourceConflictException(
            "project is being imported from another session");
      }
    } catch (IOException e1) {
      throw new ResourceConflictException("failed to lock project for import");
    }
  }
}