/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 * Copyright (C) 2011-2012 Eugene Fradkin (eugene.fradkin@gmail.com)
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
package org.jkiss.dbeaver.ui.preferences;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.ControlEnableState;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.PlatformUI;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.DBeaverPreferences;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.core.CoreMessages;
import org.jkiss.dbeaver.core.DesktopPlatform;
import org.jkiss.dbeaver.model.app.DBPPlatformLanguage;
import org.jkiss.dbeaver.model.app.DBPPlatformLanguageManager;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.registry.language.PlatformLanguageDescriptor;
import org.jkiss.dbeaver.registry.language.PlatformLanguageRegistry;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.ShellUtils;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.internal.UIMessages;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.HelpUtils;
import org.jkiss.dbeaver.utils.PrefUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;

import java.util.List;

/**
 * PrefPageDatabaseUserInterface
 */
public class PrefPageDatabaseUserInterface extends AbstractPrefPage implements IWorkbenchPreferencePage, IWorkbenchPropertyPage
{
    public static final String PAGE_ID = "org.jkiss.dbeaver.preferences.main"; //$NON-NLS-1$

    private Button automaticUpdateCheck;
    private Combo workspaceLanguage;

    private Button longOperationsCheck;
    private Spinner longOperationsTimeout;

    private Button notificationsEnabled;
    private Spinner notificationsCloseDelay;

    private boolean isStandalone = DesktopPlatform.isStandalone();

    public PrefPageDatabaseUserInterface()
    {
        super();
        setPreferenceStore(new PreferenceStoreDelegate(DBWorkbench.getPlatform().getPreferenceStore()));
    }

    @Override
    public void init(IWorkbench workbench)
    {

    }

    @Override
    protected Control createContents(Composite parent)
    {
        Composite composite = UIUtils.createPlaceholder(parent, 1, 5);

        if (isStandalone) {
            Group groupObjects = UIUtils.createControlGroup(composite, CoreMessages.pref_page_ui_general_group_general, 2, GridData.VERTICAL_ALIGN_BEGINNING, 0);
            automaticUpdateCheck = UIUtils.createCheckbox(groupObjects, CoreMessages.pref_page_ui_general_checkbox_automatic_updates, null, false, 2);
            //automaticUpdateCheck.setLayoutData(new GridData(GridData.BEGINNING, GridData.BEGINNING, true, false, 2, 1));
        }
        if (isStandalone) {
            Group groupLanguage = UIUtils.createControlGroup(composite, CoreMessages.pref_page_ui_general_group_language, 2, GridData.VERTICAL_ALIGN_BEGINNING, 0);

            workspaceLanguage = UIUtils.createLabelCombo(groupLanguage, CoreMessages.pref_page_ui_general_combo_language, CoreMessages.pref_page_ui_general_combo_language_tip, SWT.READ_ONLY | SWT.DROP_DOWN);
            workspaceLanguage.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
            List<PlatformLanguageDescriptor> languages = PlatformLanguageRegistry.getInstance().getLanguages();
            DBPPlatformLanguage pLanguage = DBWorkbench.getPlatform().getLanguage();
            for (int i = 0; i < languages.size(); i++) {
                PlatformLanguageDescriptor lang = languages.get(i);
                workspaceLanguage.add(lang.getLabel());
                if (CommonUtils.equalObjects(pLanguage, lang)) {
                    workspaceLanguage.select(i);
                }
            }
            if (workspaceLanguage.getSelectionIndex() < 0) {
                workspaceLanguage.select(0);
            }

            if (!((DBPPlatformLanguageManager)DBWorkbench.getPlatform()).isLanguageChangeEnabled()) {
                workspaceLanguage.setEnabled(false);

                Label tipLabel = UIUtils.createLabel(groupLanguage,
                    "File '" + DBWorkbench.getPlatform().getApplicationConfiguration().getAbsolutePath() + "' is read-only.\nLanguage can't be changed from preferences.");
                tipLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, GridData.VERTICAL_ALIGN_BEGINNING, false, false, 2, 1));

                Link languageChangeLink = new Link(groupLanguage, SWT.NONE);
                languageChangeLink.setText("See <a>instructions</a> how to change language manually.");
                languageChangeLink.addSelectionListener(new SelectionAdapter() {
                    @Override
                    public void widgetSelected(SelectionEvent e) {
                        ShellUtils.launchProgram(HelpUtils.getHelpExternalReference("UI-Language"));
                    }
                });
                languageChangeLink.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, GridData.VERTICAL_ALIGN_BEGINNING, false, false, 2, 1));

            } else {
                Label tipLabel = UIUtils.createLabel(groupLanguage, CoreMessages.pref_page_ui_general_label_options_take_effect_after_restart);
                tipLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING, GridData.VERTICAL_ALIGN_BEGINNING, false, false, 2, 1));
            }
        }

        // Notifications settings
        {
            Group notificationsGroup = UIUtils.createControlGroup(composite, CoreMessages.pref_page_ui_general_group_notifications, 2, GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING, 0);

            notificationsEnabled = UIUtils.createCheckbox(notificationsGroup,
                CoreMessages.pref_page_ui_general_label_enable_notifications,
                CoreMessages.pref_page_ui_general_label_enable_notifications_tip, false, 2);

            notificationsCloseDelay = UIUtils.createLabelSpinner(notificationsGroup, CoreMessages.pref_page_ui_general_label_notifications_close_delay, 0, 0, Integer.MAX_VALUE);
        }

        // Agent settings
        {
            Group agentGroup = UIUtils.createControlGroup(composite, CoreMessages.pref_page_ui_general_group_task_bar, 2, GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING, 0);

            longOperationsCheck = UIUtils.createCheckbox(agentGroup,
                    CoreMessages.pref_page_ui_general_label_enable_long_operations,
                    CoreMessages.pref_page_ui_general_label_enable_long_operations_tip, false, 2);

            longOperationsTimeout = UIUtils.createLabelSpinner(agentGroup, CoreMessages.pref_page_ui_general_label_long_operation_timeout + UIMessages.label_sec, 0, 0, Integer.MAX_VALUE);

            if (RuntimeUtils.isMacOS()) {
                ControlEnableState.disable(agentGroup);
            }
        }

        performDefaults();

        return composite;
    }

    @Override
    protected void performDefaults()
    {
        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();

        if (isStandalone) {
            automaticUpdateCheck.setSelection(store.getBoolean(DBeaverPreferences.UI_AUTO_UPDATE_CHECK));
        }

        notificationsEnabled.setSelection(store.getBoolean(ModelPreferences.NOTIFICATIONS_ENABLED));
        notificationsCloseDelay.setSelection(store.getInt(ModelPreferences.NOTIFICATIONS_CLOSE_DELAY_TIMEOUT));

        longOperationsCheck.setSelection(store.getBoolean(DBeaverPreferences.AGENT_LONG_OPERATION_NOTIFY));
        longOperationsTimeout.setSelection(store.getInt(DBeaverPreferences.AGENT_LONG_OPERATION_TIMEOUT));
    }

    @Override
    public boolean performOk()
    {
        DBPPreferenceStore store = DBWorkbench.getPlatform().getPreferenceStore();

        if (isStandalone) {
            store.setValue(DBeaverPreferences.UI_AUTO_UPDATE_CHECK, automaticUpdateCheck.getSelection());
        }


        store.setValue(ModelPreferences.NOTIFICATIONS_ENABLED, notificationsEnabled.getSelection());
        store.setValue(ModelPreferences.NOTIFICATIONS_CLOSE_DELAY_TIMEOUT, notificationsCloseDelay.getSelection());

        store.setValue(DBeaverPreferences.AGENT_LONG_OPERATION_NOTIFY, longOperationsCheck.getSelection());
        store.setValue(DBeaverPreferences.AGENT_LONG_OPERATION_TIMEOUT, longOperationsTimeout.getSelection());

        PrefUtils.savePreferenceStore(store);

        if (workspaceLanguage.getSelectionIndex() >= 0) {
            PlatformLanguageDescriptor language = PlatformLanguageRegistry.getInstance().getLanguages().get(workspaceLanguage.getSelectionIndex());
            DBPPlatformLanguage curLanguage = DBWorkbench.getPlatform().getLanguage();

            try {
                if (curLanguage != language) {
                    if (!((DBPPlatformLanguageManager)DBWorkbench.getPlatform()).isLanguageChangeEnabled()) {
                        UIUtils.showMessageBox(getShell(), "Can't change language", "Language cannot be changed from preferences", SWT.ICON_ERROR);
                    } else {
                        ((DBPPlatformLanguageManager) DBWorkbench.getPlatform()).setPlatformLanguage(language);
                        if (UIUtils.confirmAction(
                            getShell(),
                            "Restart " + GeneralUtils.getProductName(),
                            "You need to restart " + GeneralUtils.getProductName() + " to perform actual language change.\nDo you want to restart?")) {
                            UIUtils.asyncExec(() -> PlatformUI.getWorkbench().restart());
                        }
                    }
                }
            } catch (DBException e) {
                DBWorkbench.getPlatformUI().showError("Change language", "Can't switch language to " + language, e);
            }
        }

        return true;
    }

    @Nullable
    @Override
    public IAdaptable getElement()
    {
        return null;
    }

    @Override
    public void setElement(IAdaptable element)
    {
    }
}
