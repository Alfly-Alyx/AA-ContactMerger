package com.alyx.contactmerger;

import android.app.Activity;
import android.app.ActionBar;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;

import com.alyx.contactmerger.R;
import com.alyx.contactmerger.ui.LogListAdapter;

public class LogActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log);
        applyWindowInsets(findViewById(R.id.log_root));
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            configureBackNavigation(actionBar);
        }

        ListView list = (ListView)findViewById(R.id.action_list);
        list.setAdapter(new LogListAdapter(getBaseContext()));
        list.setEmptyView(findViewById(R.id.no_actions));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void applyWindowInsets(final View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop() + getActionBarHeight(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    private int getActionBarHeight() {
        TypedValue value = new TypedValue();
        return getTheme().resolveAttribute(android.R.attr.actionBarSize, value, true)
                ? TypedValue.complexToDimensionPixelSize(value.data, getResources().getDisplayMetrics())
                : 0;
    }

    private void configureBackNavigation(ActionBar actionBar) {
        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        navigation.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = createActionBarText(getString(R.string.back_to_main));
        back.setContentDescription(getString(R.string.back_to_main));
        back.setPadding(padding, 0, padding, 0);
        back.setOnClickListener(view -> finish());
        navigation.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = createActionBarText(getString(R.string.title_activity_log));
        title.setPadding(0, 0, padding, 0);
        navigation.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(navigation, new ActionBar.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START | Gravity.CENTER_VERTICAL));
    }

    private TextView createActionBarText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(this, android.R.style.TextAppearance_Holo_Widget_ActionBar_Title);
        view.setTextColor(getColor(R.color.action_bar_text));
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

}
