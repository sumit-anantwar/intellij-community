/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.SvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

public interface PropertyClient extends SvnClient {

  @Nullable
  PropertyValue getProperty(@NotNull SvnTarget target, @NotNull String property, boolean revisionProperty, @Nullable SVNRevision revision)
    throws SvnBindException;

  void getProperty(@NotNull SvnTarget target,
                   @NotNull String property,
                   @Nullable SVNRevision revision,
                   @Nullable Depth depth,
                   @Nullable PropertyConsumer handler) throws SvnBindException;

  void list(@NotNull SvnTarget target, @Nullable SVNRevision revision, @Nullable Depth depth, @Nullable PropertyConsumer handler)
    throws SvnBindException;

  void setProperty(@NotNull File file, @NotNull String property, @Nullable PropertyValue value, @Nullable Depth depth, boolean force)
    throws SvnBindException;

  void setProperties(@NotNull File file, @NotNull PropertiesMap properties) throws SvnBindException;

  void setRevisionProperty(@NotNull SvnTarget target,
                           @NotNull String property,
                           @NotNull SVNRevision revision,
                           @Nullable PropertyValue value,
                           boolean force) throws SvnBindException;
}
