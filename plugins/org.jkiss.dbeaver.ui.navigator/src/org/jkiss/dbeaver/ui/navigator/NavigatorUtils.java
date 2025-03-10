/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.navigator;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.dnd.*;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.services.IServiceLocator;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionContextDefaults;
import org.jkiss.dbeaver.model.navigator.*;
import org.jkiss.dbeaver.model.navigator.meta.DBXTreeNodeHandler;
import org.jkiss.dbeaver.model.runtime.AbstractJob;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectFilter;
import org.jkiss.dbeaver.model.struct.DBSStructContainer;
import org.jkiss.dbeaver.model.struct.rdb.DBSCatalog;
import org.jkiss.dbeaver.model.struct.rdb.DBSSchema;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.ui.UIServiceSQL;
import org.jkiss.dbeaver.ui.ActionUtils;
import org.jkiss.dbeaver.ui.IActionConstants;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.controls.ViewerColumnController;
import org.jkiss.dbeaver.ui.dnd.DatabaseObjectTransfer;
import org.jkiss.dbeaver.ui.dnd.TreeNodeTransfer;
import org.jkiss.dbeaver.ui.editors.DatabaseEditorContext;
import org.jkiss.dbeaver.ui.editors.DatabaseEditorContextBase;
import org.jkiss.dbeaver.ui.editors.EditorUtils;
import org.jkiss.dbeaver.ui.editors.MultiPageDatabaseEditor;
import org.jkiss.dbeaver.ui.navigator.actions.NavigatorHandlerObjectOpen;
import org.jkiss.dbeaver.ui.navigator.actions.NavigatorHandlerRefresh;
import org.jkiss.dbeaver.ui.navigator.database.DatabaseNavigatorContent;
import org.jkiss.dbeaver.ui.navigator.database.DatabaseNavigatorView;
import org.jkiss.dbeaver.ui.navigator.database.NavigatorViewBase;
import org.jkiss.dbeaver.ui.navigator.project.ProjectNavigatorView;
import org.jkiss.dbeaver.utils.ContentUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

/**
 * Navigator utils
 */
public class NavigatorUtils {

    private static final Log log = Log.getLog(NavigatorUtils.class);

    public static DBNNode getSelectedNode(ISelectionProvider selectionProvider)
    {
        if (selectionProvider == null) {
            return null;
        }
        return getSelectedNode(selectionProvider.getSelection());
    }

    public static DBNNode getSelectedNode(ISelection selection)
    {
        if (selection.isEmpty()) {
            return null;
        }
        if (selection instanceof IStructuredSelection) {
            Object selectedObject = ((IStructuredSelection)selection).getFirstElement();
            if (selectedObject instanceof DBNNode) {
                return (DBNNode) selectedObject;
            } else if (selectedObject != null) {
                return RuntimeUtils.getObjectAdapter(selectedObject, DBNNode.class);
            }
        }
        return null;
    }

    @NotNull
    public static List<DBNNode> getSelectedNodes(@NotNull ISelection selection) {
        if (selection.isEmpty()) {
            return Collections.emptyList();
        }
        final List<DBNNode> nodes = new ArrayList<>();
        if (selection instanceof IStructuredSelection) {
            for (Object selectedObject : (IStructuredSelection) selection) {
                if (selectedObject instanceof DBNNode) {
                    nodes.add((DBNNode) selectedObject);
                } else {
                    DBNNode node = RuntimeUtils.getObjectAdapter(selectedObject, DBNNode.class);
                    if (node != null) {
                        nodes.add(node);
                    }
                }
            }
        }
        return Collections.unmodifiableList(nodes);
    }

    /**
     * Find selected node for specified UI element
     * @param element ui element
     * @return ndoe or null
     */
    public static DBNNode getSelectedNode(UIElement element)
    {
        ISelectionProvider selectionProvider = UIUtils.getSelectionProvider(element.getServiceLocator());
        if (selectionProvider != null) {
            return NavigatorUtils.getSelectedNode(selectionProvider);
        } else {
            return null;
        }
    }

    public static DBSObject getSelectedObject(ISelection selection)
    {
        if (selection.isEmpty() || !(selection instanceof IStructuredSelection)) {
            return null;
        }
        return DBUtils.getFromObject(((IStructuredSelection)selection).getFirstElement());
    }

    public static List<DBSObject> getSelectedObjects(ISelection selection)
    {
        if (selection.isEmpty()) {
            return Collections.emptyList();
        }
        List<DBSObject> result = new ArrayList<>();
        if (selection instanceof IStructuredSelection) {
            for (Iterator iter = ((IStructuredSelection)selection).iterator(); iter.hasNext(); ) {
                DBSObject selectedObject = DBUtils.getFromObject(iter.next());
                if (selectedObject != null) {
                    result.add(selectedObject);
                }
            }
        }
        return result;
    }

    public static void addContextMenu(final IWorkbenchSite workbenchSite, final Viewer viewer) {
        addContextMenu(workbenchSite, viewer, viewer);
    }

    public static void addContextMenu(
        @Nullable final IWorkbenchSite workbenchSite,
        @NotNull final Viewer viewer,
        @NotNull ISelectionProvider selectionProvider)
    {
        MenuManager menuMgr = createContextMenu(workbenchSite, viewer, selectionProvider, null);
        if (workbenchSite instanceof IWorkbenchPartSite) {
            ((IWorkbenchPartSite)workbenchSite).registerContextMenu(menuMgr, viewer);
        } else if (workbenchSite instanceof IPageSite) {
            ((IPageSite)workbenchSite).registerContextMenu("navigatorMenu", menuMgr, viewer);
        }
    }

    public static MenuManager createContextMenu(
        @Nullable final IWorkbenchSite workbenchSite,
        @NotNull final Viewer viewer,
        @NotNull final IMenuListener menuListener)
    {
        return createContextMenu(workbenchSite, viewer, viewer, menuListener);
    }

    public static MenuManager createContextMenu(
        @Nullable final IWorkbenchSite workbenchSite,
        @NotNull final Viewer viewer,
        @NotNull final ISelectionProvider selectionProvider,
        @Nullable final IMenuListener menuListener)
    {
        final Control control = viewer.getControl();
        final MenuManager menuMgr = new MenuManager();
        Menu menu = menuMgr.createContextMenu(control);
        menu.addMenuListener(new MenuListener()
        {
            @Override
            public void menuHidden(MenuEvent e)
            {
            }

            @Override
            public void menuShown(MenuEvent e)
            {
                Menu m = (Menu)e.widget;
                DBNNode node = getSelectedNode(viewer.getSelection());
                if (node != null && !node.isLocked() && node.allowsOpen()) {
                    String commandID = NavigatorUtils.getNodeActionCommand(DBXTreeNodeHandler.Action.open, node, NavigatorCommands.CMD_OBJECT_OPEN);
                    // Dirty hack
                    // Get contribution item from menu item and check it's ID
                    try {
                        for (MenuItem item : m.getItems()) {
                            Object itemData = item.getData();
                            if (itemData instanceof IContributionItem) {
                                String contribId = ((IContributionItem)itemData).getId();
                                if (contribId != null && contribId.equals(commandID)) {
                                    m.setDefaultItem(item);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.debug(ex);
                    }
                }
            }
        });
        menuMgr.addMenuListener(manager -> {
            ViewerColumnController columnController = ViewerColumnController.getFromControl(control);
            if (columnController != null && columnController.isClickOnHeader()) {
                columnController.fillConfigMenu(manager);
                manager.add(new Separator());
                return;
            }

            manager.add(new Separator());

            addStandardMenuItem(workbenchSite, manager, selectionProvider);

            if (menuListener != null) {
                menuListener.menuAboutToShow(manager);
            }
        });

        menuMgr.setRemoveAllWhenShown(true);
        control.setMenu(menu);

        return menuMgr;
    }

    public static void addStandardMenuItem(@Nullable IWorkbenchSite workbenchSite, @NotNull IMenuManager manager, @NotNull ISelectionProvider selectionProvider) {
        final ISelection selection = selectionProvider.getSelection();
        final DBNNode selectedNode = getSelectedNode(selectionProvider);
        if (selectedNode != null && !selectedNode.isLocked() && workbenchSite != null) {
            addSetDefaultObjectAction(workbenchSite, manager, selectedNode);
        }

        manager.add(new GroupMarker(NavigatorCommands.GROUP_NAVIGATOR_ADDITIONS));

        manager.add(new GroupMarker(NavigatorCommands.GROUP_TOOLS));
        manager.add(new GroupMarker(NavigatorCommands.GROUP_TOOLS_END));

        manager.add(new GroupMarker(NavigatorCommands.GROUP_NAVIGATOR_ADDITIONS_END));
        manager.add(new GroupMarker(IActionConstants.MB_ADDITIONS_END));

        if (selectedNode != null && !selectedNode.isLocked() && workbenchSite != null) {
            manager.add(new Separator());
            // Add properties button
            if (selection instanceof IStructuredSelection) {
                Object firstElement = ((IStructuredSelection) selection).getFirstElement();
                if (PreferencesUtil.hasPropertiesContributors(firstElement) && firstElement instanceof DBNResource) {
                    manager.add(ActionUtils.makeCommandContribution(workbenchSite, IWorkbenchCommandConstants.FILE_PROPERTIES));
                }
            }

            if (selectedNode.isPersisted()) {
                // Add refresh button
                manager.add(ActionUtils.makeCommandContribution(workbenchSite, IWorkbenchCommandConstants.FILE_REFRESH));
            }
        }
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

    private static void addSetDefaultObjectAction(IWorkbenchSite workbenchSite, IMenuManager manager, DBNNode selectedNode) {
        // Add "Set active object" menu
        boolean addSetActive = false;
        if (selectedNode.isPersisted() && selectedNode instanceof DBNDatabaseNode && !(selectedNode instanceof DBNDatabaseFolder) && ((DBNDatabaseNode)selectedNode).getObject() != null) {
            DBSObject selectedObject = ((DBNDatabaseNode) selectedNode).getObject();
            DBPDataSource dataSource = ((DBNDatabaseNode) selectedNode).getDataSource();
            if (dataSource != null) {
                DBCExecutionContext defaultContext = dataSource.getDefaultInstance().getDefaultContext(new VoidProgressMonitor(), false);
                DBCExecutionContextDefaults contextDefaults = defaultContext.getContextDefaults();
                if (contextDefaults != null) {
                    if ((selectedObject instanceof DBSCatalog && contextDefaults.supportsCatalogChange() && contextDefaults.getDefaultCatalog() != selectedObject) ||
                        (selectedObject instanceof DBSSchema && contextDefaults.supportsSchemaChange() && contextDefaults.getDefaultSchema() != selectedObject))
                    {
                        addSetActive = true;
                    }
                }
            }
        }

        if (addSetActive) {
            manager.add(ActionUtils.makeCommandContribution(workbenchSite, NavigatorCommands.CMD_OBJECT_SET_DEFAULT));
        }

        manager.add(new Separator());
    }

    public static void executeNodeAction(DBXTreeNodeHandler.Action action, Object node, IServiceLocator serviceLocator) {
        executeNodeAction(action, node, null, serviceLocator);
    }

    public static void executeNodeAction(DBXTreeNodeHandler.Action action, Object node, Map<String, Object> parameters, IServiceLocator serviceLocator) {
        String defCommandId = null;
        if (action == DBXTreeNodeHandler.Action.open) {
            defCommandId = NavigatorCommands.CMD_OBJECT_OPEN;
        }
        String actionCommand = getNodeActionCommand(action, node, defCommandId);
        if (actionCommand != null) {
            ActionUtils.runCommand(actionCommand, new StructuredSelection(node), parameters, serviceLocator);
        } else {
            // do nothing
            // TODO: implement some other behavior
        }

    }

    public static String getNodeActionCommand(DBXTreeNodeHandler.Action action, Object node, String defCommand) {
        if (node instanceof DBNDatabaseNode) {
            DBXTreeNodeHandler handler = ((DBNDatabaseNode) node).getMeta().getHandler(action);
            if (handler != null && handler.getPerform() == DBXTreeNodeHandler.Perform.command && !CommonUtils.isEmpty(handler.getCommand())) {
                return handler.getCommand();
            }
        }
        return defCommand;
    }

    public static void addDragAndDropSupport(final Viewer viewer) {
        addDragAndDropSupport(viewer, true, true);
    }

    public static void addDragAndDropSupport(final Viewer viewer, boolean enableDrag, boolean enableDrop) {
        if (enableDrag) {
            Transfer[] dragTransferTypes = new Transfer[] {
                TextTransfer.getInstance(),
                TreeNodeTransfer.getInstance(),
                DatabaseObjectTransfer.getInstance(),
                FileTransfer.getInstance()
            };
            int operations = DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK;

            final DragSource source = new DragSource(viewer.getControl(), operations);
            source.setTransfer(dragTransferTypes);
            source.addDragListener(new DragSourceListener() {
                private final List<File> tmpFiles = new ArrayList<>();

                private IStructuredSelection selection;

                @Override
                public void dragStart(DragSourceEvent event) {
                    selection = (IStructuredSelection) viewer.getSelection();
                }

                @Override
                public void dragSetData(DragSourceEvent event) {
                    if (!selection.isEmpty()) {
                        List<DBNNode> nodes = new ArrayList<>();
                        List<DBPNamedObject> objects = new ArrayList<>();
                        List<String> names = new ArrayList<>();
                        tmpFiles.clear();

                        String lineSeparator = CommonUtils.getLineSeparator();
                        StringBuilder buf = new StringBuilder();
                        for (Iterator<?> i = selection.iterator(); i.hasNext(); ) {
                            Object nextSelected = i.next();
                            if (!(nextSelected instanceof DBNNode)) {
                                continue;
                            }
                            DBNNode node = (DBNNode) nextSelected;
                            nodes.add(node);
                            String nodeName;
                            if (nextSelected instanceof DBNDatabaseNode && !(nextSelected instanceof DBNDataSource)) {
                                DBSObject object = ((DBNDatabaseNode) nextSelected).getObject();
                                if (object == null) {
                                    continue;
                                }
                                nodeName = DBUtils.getObjectFullName(object, DBPEvaluationContext.UI);
                                objects.add(object);
                            } else if (nextSelected instanceof DBNDataSource) {
                                DBPDataSourceContainer object = ((DBNDataSource) nextSelected).getDataSourceContainer();
                                nodeName = object.getName();
                                objects.add(object);
                            } else if (FileTransfer.getInstance().isSupportedType(event.dataType) &&
                                nextSelected instanceof DBNStreamData &&
                                ((DBNStreamData) nextSelected).supportsStreamData())
                            {
                                String fileName = node.getNodeName();
                                try {
                                    File tmpFile = new File(
                                        DBWorkbench.getPlatform().getTempFolder(new VoidProgressMonitor(), "dnd-files"),
                                        fileName);
                                    if (!tmpFile.exists()) {
                                        if (!tmpFile.createNewFile()) {
                                            log.error("Can't create new file" + tmpFile.getAbsolutePath());
                                            continue;
                                        }
                                        new AbstractJob("Dump stream data '" + fileName + "' on disk " + tmpFile.getAbsolutePath()) {
                                            {
                                                setUser(true);
                                            }
                                            @Override
                                            protected IStatus run(DBRProgressMonitor monitor) {
                                                log.debug(getName());
                                                try {
                                                    long streamSize = ((DBNStreamData) nextSelected).getStreamSize();
                                                    try (InputStream is = ((DBNStreamData) nextSelected).openInputStream()) {
                                                        try (OutputStream out = Files.newOutputStream(tmpFile.toPath())) {
                                                            ContentUtils.copyStreams(is, streamSize, out, monitor);
                                                        }
                                                        tmpFiles.add(tmpFile);
                                                    }
                                                } catch (Exception e) {
                                                    if (!tmpFile.delete()) {
                                                        log.error("Error deleting temp file " + tmpFile.getAbsolutePath());
                                                    }
                                                }
                                                return Status.OK_STATUS;
                                            }

                                            @Override
                                            public String toString() {
                                                return getName();
                                            }
                                        }.schedule();
                                    }
                                    nodeName = tmpFile.getAbsolutePath();
                                } catch (Exception e) {
                                    log.error(e);
                                    continue;
                                }
                            } else {
                                nodeName = node.getNodeTargetName();
                            }
                            if (buf.length() > 0) {
                                buf.append(lineSeparator);
                            }
                            buf.append(nodeName);
                            names.add(nodeName);
                        }
                        if (TreeNodeTransfer.getInstance().isSupportedType(event.dataType)) {
                            event.data = nodes;
                        } else if (DatabaseObjectTransfer.getInstance().isSupportedType(event.dataType)) {
                            event.data = objects;
                        } else if (TextTransfer.getInstance().isSupportedType(event.dataType)) {
                            event.data = buf.toString();
                        } else if (FileTransfer.getInstance().isSupportedType(event.dataType)) {
                            names.removeIf(s -> !Files.exists(Path.of(s)));
                            event.data = names.toArray(new String[0]);
                        }
                    } else {
                        if (TreeNodeTransfer.getInstance().isSupportedType(event.dataType)) {
                            event.data = Collections.emptyList();
                        } else if (DatabaseObjectTransfer.getInstance().isSupportedType(event.dataType)) {
                            event.data = Collections.emptyList();
                        } else if (TextTransfer.getInstance().isSupportedType(event.dataType)) {
                            event.data = "";
                        } else if (FileTransfer.getInstance().isSupportedType(event.dataType)) {
                            event.data = new String[0];
                        }
                    }
                }

                @Override
                public void dragFinished(DragSourceEvent event) {
                    if (!tmpFiles.isEmpty()) {
                        for (File tmpFile : tmpFiles) {
                            if (!tmpFile.delete()) {
                                log.error("Error deleting temp file " + tmpFile.getAbsolutePath());
                            }
                        }
                    }
                }
            });
        }

        if (enableDrop) {
            DropTarget dropTarget = new DropTarget(viewer.getControl(), DND.DROP_MOVE);
            dropTarget.setTransfer(TreeNodeTransfer.getInstance(), FileTransfer.getInstance());
            dropTarget.addDropListener(new DropTargetListener() {
                @Override
                public void dragEnter(DropTargetEvent event) {
                    handleDragEvent(event);
                }

                @Override
                public void dragLeave(DropTargetEvent event) {
                    handleDragEvent(event);
                }

                @Override
                public void dragOperationChanged(DropTargetEvent event) {
                    handleDragEvent(event);
                }

                @Override
                public void dragOver(DropTargetEvent event) {
                    handleDragEvent(event);
                }

                @Override
                public void drop(DropTargetEvent event) {
                    handleDragEvent(event);
                    if (event.detail == DND.DROP_MOVE) {
                        moveNodes(event);
                    }
                }

                @Override
                public void dropAccept(DropTargetEvent event) {
                    handleDragEvent(event);
                }

                private void handleDragEvent(DropTargetEvent event) {
                    event.detail = isDropSupported(event) ? DND.DROP_MOVE : DND.DROP_NONE;
                    event.feedback = DND.FEEDBACK_SELECT;
                }

                private boolean isDropSupported(DropTargetEvent event) {
                    Object curObject;
                    if (event.item instanceof Item) {
                        curObject = event.item.getData();
                    } else {
                        curObject = null;
                    }

                    if (TreeNodeTransfer.getInstance().isSupportedType(event.currentDataType)) {
                        @SuppressWarnings("unchecked")
                        Collection<DBNNode> nodesToDrop = (Collection<DBNNode>) event.data;
                        if (curObject instanceof DBNNode) {
                            if (!CommonUtils.isEmpty(nodesToDrop)) {
                                for (DBNNode node : nodesToDrop) {
                                    if (!((DBNNode) curObject).supportsDrop(node)) {
                                        return false;
                                    }
                                }
                                return true;
                            } else {
                                return ((DBNNode) curObject).supportsDrop(null);
                            }
                        } else if (curObject == null) {
                            // Drop to empty area
                            if (!CommonUtils.isEmpty(nodesToDrop)) {
                                for (DBNNode node : nodesToDrop) {
                                    if (!(node instanceof DBNDataSource)) {
                                        return false;
                                    }
                                }
                                return true;
                            } else {
                                Widget widget = event.widget;
                                if (widget instanceof DropTarget) {
                                    widget = ((DropTarget) widget).getControl();
                                }
                                return widget == viewer.getControl();
                            }
                        }
                    }
                    // Drop file - over resources
                    if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
                        if (curObject instanceof IAdaptable) {
                            IResource curResource = ((IAdaptable) curObject).getAdapter(IResource.class);
                            return curResource != null;
                        }
                    }

                    return false;
                }

                private void moveNodes(DropTargetEvent event) {
                    Object curObject;
                    if (event.item instanceof Item) {
                        curObject = event.item.getData();
                    } else {
                        curObject = null;
                    }
                    if (TreeNodeTransfer.getInstance().isSupportedType(event.currentDataType)) {
                        if (curObject instanceof DBNNode) {
                            Collection<DBNNode> nodesToDrop = TreeNodeTransfer.getInstance().getObject();
                            try {
                                ((DBNNode) curObject).dropNodes(nodesToDrop);
                            } catch (DBException e) {
                                DBWorkbench.getPlatformUI().showError("Drop error", "Can't drop node", e);
                            }
                        } else if (curObject == null) {
                            for (DBNNode node : TreeNodeTransfer.getInstance().getObject()) {
                                if (node instanceof DBNDataSource) {
                                    // Drop datasource on a view
                                    // We need target project
                                    if (viewer.getInput() instanceof DatabaseNavigatorContent) {
                                        DBNNode rootNode = ((DatabaseNavigatorContent) viewer.getInput()).getRootNode();
                                        if (rootNode != null && rootNode.getOwnerProject() != null) {
                                            ((DBNDataSource) node).moveToFolder(rootNode.getOwnerProject(), null);
                                        }
                                    }
                                } else if (node instanceof DBNLocalFolder) {
                                    ((DBNLocalFolder) node).getFolder().setParent(null);
                                } else {
                                    continue;
                                }
                                DBNModel.updateConfigAndRefreshDatabases(node);
                            }
                        }
                    }
                    if (FileTransfer.getInstance().isSupportedType(event.currentDataType)) {
                        if (curObject instanceof IAdaptable) {
                            IResource curResource = ((IAdaptable) curObject).getAdapter(IResource.class);
                            if (curResource != null) {
                                if (curResource instanceof IFile) {
                                    curResource = curResource.getParent();
                                }
                                if (curResource instanceof IFolder) {
                                    IFolder toFolder = (IFolder) curResource;
                                    new AbstractJob("Copy files to workspace") {
                                        @Override
                                        protected IStatus run(DBRProgressMonitor monitor) {
                                            dropFilesIntoFolder(monitor, toFolder, (String[])event.data);
                                            return Status.OK_STATUS;
                                        }
                                    }.schedule();
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    private static void dropFilesIntoFolder(DBRProgressMonitor monitor, IFolder toFolder, String[] data) {
        for (String extFileName : data) {
            File extFile = new File(extFileName);
            if (extFile.exists()) {
                IFile targetFile = toFolder.getFile(extFile.getName());
                try (InputStream is = Files.newInputStream(extFile.toPath())) {
                    ContentUtils.copyStreamToFile(monitor, is, extFile.length(), targetFile);
                } catch (IOException e) {
                    log.error(e);
                }
            }
        }
    }

    public static NavigatorViewBase getActiveNavigatorView(ExecutionEvent event) {
        IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
        if (activePart instanceof NavigatorViewBase) {
            return (NavigatorViewBase) activePart;
        }
        final IWorkbenchPage activePage = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        activePart = activePage.findView(DatabaseNavigatorView.VIEW_ID);
        if (activePart instanceof NavigatorViewBase && activePage.isPartVisible(activePart)) {
            return (NavigatorViewBase) activePart;
        }
        activePart = activePage.findView(ProjectNavigatorView.VIEW_ID);
        if (activePart instanceof NavigatorViewBase && activePage.isPartVisible(activePart)) {
            return (NavigatorViewBase) activePart;
        }
        return null;
    }

    public static void filterSelection(final ISelection selection, boolean exclude)
    {
        if (selection instanceof IStructuredSelection) {
            Map<DBNDatabaseFolder, DBSObjectFilter> folders = new HashMap<>();
            for (Object item : ((IStructuredSelection)selection).toArray()) {
                if (item instanceof DBNNode) {
                    final DBNNode node = (DBNNode) item;
                    DBNDatabaseFolder folder = (DBNDatabaseFolder) node.getParentNode();
                    DBSObjectFilter nodeFilter = folders.get(folder);
                    if (nodeFilter == null) {
                        nodeFilter = folder.getNodeFilter(folder.getItemsMeta(), true);
                        if (nodeFilter == null) {
                            nodeFilter = new DBSObjectFilter();
                        }
                        folders.put(folder, nodeFilter);
                    }
                    if (exclude) {
                        nodeFilter.addExclude(node.getNodeName());
                    } else {
                        nodeFilter.addInclude(node.getNodeName());
                    }
                    nodeFilter.setEnabled(true);
                }
            }
            // Save folders
            for (Map.Entry<DBNDatabaseFolder, DBSObjectFilter> entry : folders.entrySet()) {
                entry.getKey().setNodeFilter(entry.getKey().getItemsMeta(), entry.getValue());
            }
            // Refresh all folders
            NavigatorHandlerRefresh.refreshNavigator(folders.keySet());
        }
    }

    public static boolean syncEditorWithNavigator(INavigatorModelView navigatorView, IEditorPart activeEditor) {
        if (!(activeEditor instanceof IDataSourceContainerProviderEx)) {
            return false;
        }
        IDataSourceContainerProviderEx dsProvider = (IDataSourceContainerProviderEx) activeEditor;
        Viewer navigatorViewer = navigatorView.getNavigatorViewer();
        if (navigatorViewer == null) {
            return false;
        }
        DBNNode selectedNode = getSelectedNode(navigatorViewer.getSelection());
        if (!(selectedNode instanceof DBNDatabaseNode)) {
            return false;
        }
        DBNDatabaseNode databaseNode = (DBNDatabaseNode) selectedNode;
        DBSObject dbsObject = databaseNode.getObject();
        if (!(dbsObject instanceof DBSStructContainer)) {
            dbsObject = DBUtils.getParentOfType(DBSStructContainer.class, dbsObject);
        }
        DBPDataSourceContainer ds = databaseNode.getDataSourceContainer();
        if (dsProvider.getDataSourceContainer() != ds) {
            dsProvider.setDataSourceContainer(ds);
            DatabaseEditorContext editorContext = new DatabaseEditorContextBase(ds, dbsObject);
            EditorUtils.setInputDataSource(activeEditor.getEditorInput(), editorContext);
        }

        if (activeEditor instanceof DBPContextProvider && dbsObject != null) {
            DBCExecutionContext navExecutionContext = null;
            try {
                navExecutionContext = DBUtils.getOrOpenDefaultContext(dbsObject, false);
            } catch (DBCException ignored) {
            }
            DBCExecutionContext editorExecutionContext = ((DBPContextProvider) activeEditor).getExecutionContext();
            if (navExecutionContext != null && editorExecutionContext != null) {
                DBCExecutionContextDefaults editorContextDefaults = editorExecutionContext.getContextDefaults();
                if (editorContextDefaults != null) {
                    final DBSObject dbObject = dbsObject;
                    RuntimeUtils.runTask(monitor -> {
                            try {
                                monitor.beginTask("Change default object", 1);
                                if (dbObject instanceof DBSCatalog && dbObject != editorContextDefaults.getDefaultCatalog()) {
                                    monitor.subTask("Change default catalog");
                                    editorContextDefaults.setDefaultCatalog(monitor, (DBSCatalog) dbObject, null);
                                } else if (dbObject instanceof DBSSchema && dbObject != editorContextDefaults.getDefaultSchema()) {
                                    monitor.subTask("Change default schema");
                                    editorContextDefaults.setDefaultSchema(monitor, (DBSSchema) dbObject);
                                }
                                monitor.worked(1);
                                monitor.done();
                            } catch (DBCException e) {
                                throw new InvocationTargetException(e);
                            }
                        }, "Set active object",
                        dbObject.getDataSource().getContainer().getPreferenceStore().getInt(ModelPreferences.CONNECTION_OPEN_TIMEOUT));
                }
            }
        }

        return true;
    }

    public static void openNavigatorNode(Object node, IWorkbenchWindow window) {
        openNavigatorNode(node, window, null);
    }

    public static void openNavigatorNode(Object node, IWorkbenchWindow window, Map<?, ?> parameters) {
        IResource resource = node instanceof IAdaptable ? ((IAdaptable) node).getAdapter(IResource.class) : null;
        if (resource != null) {
            UIServiceSQL serviceSQL = DBWorkbench.getService(UIServiceSQL.class);
            if (serviceSQL != null) {
                serviceSQL.openResource(resource);
            }
        } else if (node instanceof DBNNode && ((DBNNode) node).allowsOpen()) {
            Object activePage = parameters == null ? null : parameters.get(MultiPageDatabaseEditor.PARAMETER_ACTIVE_PAGE);
            NavigatorHandlerObjectOpen.openEntityEditor(
                (DBNNode) node,
                CommonUtils.toString(activePage, null),
                window);
        }
    }

    @Nullable
    public static IStructuredSelection getSelectionFromPart(IWorkbenchPart part)
    {
        if (part == null) {
            return null;
        }
        ISelectionProvider selectionProvider = part.getSite().getSelectionProvider();
        if (selectionProvider == null) {
            return null;
        }
        ISelection selection = selectionProvider.getSelection();
        if (selection.isEmpty() || !(selection instanceof IStructuredSelection)) {
            return null;
        }
        return (IStructuredSelection)selection;
    }

    public static DBPProject getSelectedProject() {
        IWorkbenchPart activePart = UIUtils.getActiveWorkbenchWindow().getActivePage().getActivePart();
        ISelection selection;
        if (activePart == null) {
            selection = null;
        } else {
            ISelectionProvider selectionProvider = activePart.getSite().getSelectionProvider();
            selection = selectionProvider == null ? null : selectionProvider.getSelection();
        }
        return NavigatorUtils.getSelectedProject(selection, activePart);

    }

    public static DBPProject getSelectedProject(ISelection currentSelection, IWorkbenchPart activePart) {
        DBPProject activeProject = null;
        if (currentSelection instanceof IStructuredSelection && !currentSelection.isEmpty()) {
            Object selItem = ((IStructuredSelection) currentSelection).getFirstElement();
            if (selItem instanceof DBNNode) {
                activeProject = ((DBNNode) selItem).getOwnerProject();
            }
        }
        if (activeProject == null) {
            if (activePart instanceof DBPContextProvider) {
                DBCExecutionContext executionContext = ((DBPContextProvider) activePart).getExecutionContext();
                if (executionContext != null) {
                    activeProject = executionContext.getDataSource().getContainer().getRegistry().getProject();
                }
            }
        }
        if (activeProject == null) {
            activeProject = DBWorkbench.getPlatform().getWorkspace().getActiveProject();
        }
        return activeProject;
    }
}
