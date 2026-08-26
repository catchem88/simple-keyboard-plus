/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.compat.MenuItemIconColorCompat;

/*
    "Auto-text" settings sub screen. Lists the configured keyword to expanded text pairs and lets
    them be added, edited and removed. The whole table is stored in a single string preference, so
    every change rewrites that preference and rebuilds the list.
*/
public final class AutoTextSettingsFragment extends SubScreenFragment {
    private View mView;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.empty_settings);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,final ViewGroup container,
            final Bundle savedInstanceState) {
        mView = super.onCreateView(inflater,container,savedInstanceState);
        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        buildContent();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu,final MenuInflater inflater) {
        inflater.inflate(R.menu.add_auto_text,menu);

        final MenuItem addAutoTextMenuItem = menu.findItem(R.id.action_add_auto_text);
        MenuItemIconColorCompat.matchMenuIconColor(mView,addAutoTextMenuItem,
                getActivity().getActionBar());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if(item.getItemId() == R.id.action_add_auto_text) {
            showEntryDialog(null,null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
        Build a row for every entry in the stored table.
    */
    private void buildContent() {
        final Context context = getActivity();
        final PreferenceGroup group = getPreferenceScreen();
        group.removeAll();

        final AutoText autoText = Settings.readAutoText(getSharedPreferences());
        if(autoText.isEmpty()) {
            final Preference emptyPreference = new Preference(context);
            emptyPreference.setTitle(R.string.auto_text_empty);
            emptyPreference.setSummary(R.string.auto_text_empty_summary);
            emptyPreference.setSelectable(false);
            group.addPreference(emptyPreference);
            return;
        }

        for(int index = 0; index < autoText.size(); index++) {
            group.addPreference(buildEntryPreference(context,autoText.getKeyword(index),
                    autoText.getExpansion(index)));
        }
    }

    private Preference buildEntryPreference(final Context context,final String keyword,
            final String expansion) {
        final Preference preference = new Preference(context);
        preference.setTitle(keyword);
        preference.setSummary(expansion);
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference clicked) {
                showEntryDialog(keyword,expansion);
                return true;
            }
        });
        return preference;
    }

    /*
        Show the dialog used to add, edit and remove an entry. Pass a null keyword to add a new one.
    */
    private void showEntryDialog(final String keyword,final String expansion) {
        final Context context = getActivity();
        final View view = LayoutInflater.from(context).inflate(R.layout.auto_text_dialog,null);
        final EditText keywordView = (EditText)view.findViewById(R.id.auto_text_dialog_keyword);
        final EditText expansionView =
                (EditText)view.findViewById(R.id.auto_text_dialog_expansion);
        keywordView.setText(keyword);
        expansionView.setText(expansion);

        final int titleRes;
        if(keyword == null) {
            titleRes = R.string.auto_text_add;
        }
        else {
            titleRes = R.string.auto_text_edit;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleRes);
        builder.setView(view);
        builder.setPositiveButton(android.R.string.ok,new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog,final int which) {
                saveEntry(keyword,keywordView.getText().toString(),
                        expansionView.getText().toString());
            }
        });
        builder.setNegativeButton(android.R.string.cancel,null);
        if(keyword != null) {
            builder.setNeutralButton(R.string.remove,new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog,final int which) {
                    removeEntry(keyword);
                }
            });
        }
        builder.show();
    }

    /*
        Store an entry. When the keyword of an existing entry was changed the old one is dropped, so
        that editing a keyword does not leave a duplicate behind.
    */
    private void saveEntry(final String previousKeyword,final String keyword,
            final String expansion) {
        if(TextUtils.isEmpty(keyword) || TextUtils.isEmpty(expansion)) {
            return;
        }

        AutoText autoText = Settings.readAutoText(getSharedPreferences());
        if(previousKeyword != null && !previousKeyword.equals(keyword)) {
            autoText = autoText.withoutEntry(previousKeyword);
        }
        Settings.writeAutoText(getSharedPreferences(),autoText.withEntry(keyword,expansion));
        buildContent();
    }

    private void removeEntry(final String keyword) {
        final AutoText autoText = Settings.readAutoText(getSharedPreferences());
        Settings.writeAutoText(getSharedPreferences(),autoText.withoutEntry(keyword));
        buildContent();
    }
}
