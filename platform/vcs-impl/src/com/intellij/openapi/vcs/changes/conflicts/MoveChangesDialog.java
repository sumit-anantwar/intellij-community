/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesTree;
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder;
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class MoveChangesDialog extends DialogWrapper {
  private static final String MOVE_CHANGES_CURRENT_ONLY = "move.changes.current.only";
  private final ChangesTree myTreeList;
  private final Collection<Change> mySelected;
  private JBCheckBox myCheckBox;

  public MoveChangesDialog(final Project project, Collection<Change> selected, final Set<ChangeList> changeLists, VirtualFile current) {
    super(project, true);
    mySelected = selected;
    setTitle("Move Changes to Active Changelist");
    myTreeList = new ChangesTree(project, true, false) {
      @Override
      public void rebuildTree() {
        DefaultTreeModel model = TreeModelBuilder.buildFromChangeLists(project, isShowFlatten(), changeLists);
        updateTreeModel(model);
      }
    };
    myTreeList.rebuildTree();
    myTreeList.selectFile(current);

    myCheckBox = new JBCheckBox("Select current file only");
    myCheckBox.setMnemonic('c');
    myCheckBox.addActionListener(e -> setSelected(myCheckBox.isSelected()));

    boolean selectCurrent = PropertiesComponent.getInstance().getBoolean(MOVE_CHANGES_CURRENT_ONLY);
    myCheckBox.setSelected(selectCurrent);
    setSelected(selectCurrent);

    init();
  }

  private void setSelected(boolean selected) {
    myTreeList.excludeChanges(myTreeList.getIncludedSet());
    if (selected) {
      List<Change> selectedChanges = VcsTreeModelData.selected(myTreeList).userObjects(Change.class);
      myTreeList.includeChanges(selectedChanges);
    }
    else {
      myTreeList.includeChanges(mySelected);
    }
    PropertiesComponent.getInstance().setValue(MOVE_CHANGES_CURRENT_ONLY, selected);
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myTreeList), BorderLayout.CENTER);

    DefaultActionGroup actionGroup = new DefaultActionGroup(myTreeList.getTreeActions());
    panel.add(ActionManager.getInstance().createActionToolbar("MoveChangesDialog", actionGroup, true).getComponent(), BorderLayout.NORTH);
    myTreeList.expandAll();
    myTreeList.repaint();
    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTreeList;
  }

  public Collection<Change> getIncludedChanges() {
    return VcsTreeModelData.included(myTreeList).userObjects(Change.class);
  }

  @Override
  public boolean isOKActionEnabled() {
    return !getIncludedChanges().isEmpty();
  }

  @Nullable
  @Override
  protected JComponent createDoNotAskCheckbox() {
    return myCheckBox;
  }
  /*

  @NotNull
  @Override
  protected Action[] createLeftSideActions() {
    return new Action[] {
      new AbstractAction("Select &Current") {
        @Override
        public void actionPerformed(ActionEvent e) {
          ChangesBrowserNode<Change> component = (ChangesBrowserNode<Change>)myTreeList.getSelectionPath().getLastPathComponent();
          myTreeList.excludeChanges(myChanges);
        }
      },

      new AbstractAction("Select &All") {
        @Override
        public void actionPerformed(ActionEvent e) {
          myTreeList.includeChanges(myChanges);
        }
      }
    };
  }
  */
}
