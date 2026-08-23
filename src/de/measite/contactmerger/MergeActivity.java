package de.measite.contactmerger;

import java.io.File;
import android.app.Activity;
import android.app.ActionBar;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import de.measite.contactmerger.ui.MergeListAdapter;

public class MergeActivity extends Activity implements View.OnClickListener {

    private static final int CONTACTS_PERMISSION_REQUEST = 100;
    private static final int NOTIFICATIONS_PERMISSION_REQUEST = 101;
    private boolean receiverRegistered;

    protected ProgressBar progressBar;
    protected TextView loadText;
    protected View progressContainer;
    protected Button startScan;

    protected BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String event = intent.getStringExtra("event");
            if (event == null) {
                return;
            }
            switch (event) {
                case "start":
                    progressBar.setProgress(0);
                    progressBar.setMax(1000);
                    progressContainer.setVisibility(View.VISIBLE);
                    startScan.setVisibility(View.GONE);
                    return;
                case "progress":
                    float f = intent.getFloatExtra("progress", 0f);
                    progressBar.setProgress((int) (1000 * f));
                    progressBar.setMax(1000);
                    progressContainer.setVisibility(View.VISIBLE);
                    progressBar.postInvalidate();
                    loadText.setText("Analyzing your contacts.\nThis can take a few minutes.\n" +
                            ((int) (f * 100)) + "%"
                    );
                    return;
                case "finish":
                    progressBar.setProgress(1000);
                    progressBar.setMax(1000);
                    progressContainer.setVisibility(View.GONE);
                    updateList();
                    return;
                case "abort":
                    progressBar.setProgress(0);
                    progressBar.setMax(1);
                    progressContainer.setVisibility(View.GONE);
                    startScan.setVisibility(View.VISIBLE);
                    break;
            }
        }
    };

    protected MergeListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.merge);
        applyWindowInsets(findViewById(R.id.merge_root));
        configureApplicationTitle();

        if (hasContactsPermission()) {
            requestNotificationsPermission();
            initializeContactList();
        } else {
            showContactsPermissionRequired();
            requestContactsPermission();
        }
    }

    public void updateList() {
        if (adapter == null) {
            return;
        }
        progressContainer = findViewById(R.id.progress_bar_container);
        progressBar = findViewById(R.id.analyze_progress);
        progressContainer.setVisibility(View.GONE);

        loadText = findViewById(R.id.load_text);

        TextView stopScan = findViewById(R.id.stop_scan);
        Typeface font = Typeface.createFromAsset(getAssets(), "fontawesome-webfont.ttf");
        stopScan.setTypeface(font);
        stopScan.setClickable(true);
        stopScan.setOnClickListener(this);

        startScan = findViewById(R.id.start_scan);
        startScan.setOnClickListener(this);

        ViewSwitcher switcher = findViewById(R.id.switcher);
        ViewSwitcher switcher_list = findViewById(R.id.switcher_list);

        Context context = getApplicationContext();
        File path = context.getDatabasePath("contactsgraph");
        File modelFile = new File(path, "model.kryo.gz");

        if (path.exists() && modelFile.exists()) {
            startScan.setVisibility(View.VISIBLE);
            this.adapter.update();
            while (switcher.getCurrentView().getId() != R.id.switcher_list) {
                switcher.showNext();
            }
            if (adapter.getCount() == 0) {
                while (switcher_list.getCurrentView().getId() != R.id.all_done) {
                    switcher_list.showNext();
                }
            } else {
                while (switcher_list.getCurrentView().getId() != R.id.contact_merge_list) {
                    switcher_list.showPrevious();
                }
            }
            switcher_list.postInvalidate();
        } else {
            if (switcher.getCurrentView().getId() == R.id.contact_merge_list) {
                switcher.showPrevious();
            }
            loadText.setText("Tap Start analysis to scan your contacts.");
            loadText.setVisibility(View.VISIBLE);
            startScan.setVisibility(View.VISIBLE);
        }
        switcher.postInvalidate();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.merge, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean hasAnalysis = hasCompletedAnalysis();
        menu.findItem(R.id.analyze_now).setVisible(hasAnalysis);
        menu.findItem(R.id.action_log).setVisible(hasAnalysis);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            showProjectAbout();
            return true;
        }
        if (id == R.id.analyze_now) {
            Intent intent = new Intent(getApplicationContext(), AnalyzerService.class);
            intent.putExtra("forceRunning", true);
            startAnalyzer(intent);
            return true;
        }
        if (id == R.id.action_log) {
            Intent intent = new Intent(getApplicationContext(), LogActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState.containsKey("MERGELIST")) {
            ListView list = findViewById(R.id.contact_merge_list);
            list.onRestoreInstanceState(savedInstanceState.getParcelable("MERGELIST"));
        }
        if (hasContactsPermission()) {
            initializeContactList();
        } else {
            showContactsPermissionRequired();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ListView list = findViewById(R.id.contact_merge_list);
        outState.putParcelable("MERGELIST", list.onSaveInstanceState());
    }

    @Override
    protected void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(receiver, new IntentFilter("de.measite.contactmerger.ANALYSE"));
        receiverRegistered = true;
        if (hasContactsPermission()) {
            initializeContactList();
        }
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(receiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.stop_scan) {
            Intent intent = new Intent(getApplicationContext(), AnalyzerService.class);
            intent.putExtra("stop", true);
            startAnalyzer(intent);
            progressContainer.setVisibility(View.GONE);
            loadText.setVisibility(View.GONE);
            startScan.setVisibility(View.VISIBLE);
        }
        if (v.getId() == R.id.start_scan) {
            Intent intent = new Intent(getApplicationContext(), AnalyzerService.class);
            intent.putExtra("forceRunning", true);
            startAnalyzer(intent);
            loadText.setVisibility(View.VISIBLE);
            startScan.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CONTACTS_PERMISSION_REQUEST && hasContactsPermission()) {
            requestNotificationsPermission();
            initializeContactList();
        } else if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            showContactsPermissionRequired();
        }
    }

    private boolean hasContactsPermission() {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED;
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

    private void configureApplicationTitle() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            TextView title = new TextView(this);
            title.setText(R.string.app_name);
            title.setTextAppearance(this, android.R.style.TextAppearance_Holo_Widget_ActionBar_Title);
            title.setTextColor(getColor(R.color.action_bar_text));
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setContentDescription(getString(R.string.about));
            title.setOnClickListener(view -> showProjectAbout());

            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setCustomView(title, new ActionBar.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.START | Gravity.CENTER_VERTICAL));
        }
    }

    private boolean hasCompletedAnalysis() {
        File path = getApplicationContext().getDatabasePath("contactsgraph");
        return path.exists() && new File(path, "model.kryo.gz").exists();
    }

    private void showProjectAbout() {
        startActivity(new Intent(this, AboutActivity.class));
    }

    private void initializeContactList() {
        if (adapter == null) {
            ListView list = findViewById(R.id.contact_merge_list);
            adapter = new MergeListAdapter(this);
            list.setAdapter(adapter);
            list.postInvalidate();
        }
        updateList();
    }

    private void showContactsPermissionRequired() {
        TextView message = findViewById(R.id.load_text);
        Button retry = findViewById(R.id.start_scan);
        message.setText("Contact permission is required to analyze and merge contacts.");
        retry.setVisibility(View.VISIBLE);
        retry.setOnClickListener(this);
    }

    private void requestContactsPermission() {
        requestPermissions(new String[]{
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
        }, CONTACTS_PERMISSION_REQUEST);
    }

    private void requestNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATIONS_PERMISSION_REQUEST);
        }
    }

    private void startAnalyzer(Intent intent) {
        if (hasContactsPermission()) {
            AnalyzerService.start(getApplicationContext(), intent);
        } else {
            requestContactsPermission();
        }
    }
}
