package de.measite.contactmerger;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.content.pm.PackageManager;
import android.provider.ContactsContract;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import de.measite.contactmerger.graph.GraphIO;
import de.measite.contactmerger.graph.UndirectedGraph;
import de.measite.contactmerger.ui.model.ModelIO;
import de.measite.contactmerger.ui.model.MergeContact;
import de.measite.contactmerger.ui.GraphConverter;

public class AnalyzerService extends Service {

    private static final String TAG = "ContactMerger/AnalyzerService";
    private static final String CHANNEL_ID = "contact_analysis";
    private static final int NOTIFICATION_ID = 1;

    private static AnalyzerThread analyzer;

    private SharedPreferences scanPreferences;
    private LocalBroadcastManager broadcastManager;

    private final Random rnd = new Random();
    private NotificationManager notificationManager;

    public static void start(Context context, Intent intent) {
        // All callers are user-facing activity actions. Starting the service normally
        // avoids the Android O foreground-service deadline when no scan is needed.
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        broadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding service");
        return new AnalyzerBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        scanPreferences = getSharedPreferences("scan_preferences", 0);
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (intent != null) {
            if (intent.getBooleanExtra("stop", false)) {
                stopAnalysis(notificationManager);
                return START_NOT_STICKY;
            }

            if (intent.getBooleanExtra("forceRunning", false)) {
                startAnalysisThread();
            } else {
                startIfNeeded();
            }
        }

        return START_NOT_STICKY;
    }

    private void stopAnalysis(final NotificationManager notificationManager) {
        new Thread(() -> {
            // Stop the running analysis thread
            while (analyzer != null && analyzer.isAlive()) {
                try {
                    analyzer.doStop();
                    analyzer.interrupt();
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
            stopForeground(true);
            notificationManager.cancel(NOTIFICATION_ID);
            stopSelf();
        }).start();
    }

    private void startIfNeeded() {
        // Check battery status and decide whether to start the analysis
        double batteryLevel = getBatteryLevel();
        boolean onBattery = isOnBattery();

        // File paths
        File path = getDatabasePath("contactsgraph");
        if (!path.exists() && !path.mkdirs() && !path.isDirectory()) {
            return; // Early exit if the app isn't ready
        }

        File graphFile = new File(path, "graph.kryo.gz");
        File modelFile = new File(path, "model.kryo.gz");

        boolean graphFileExists = graphFile.exists();
        boolean modelFileExists = modelFile.exists() && modelFile.lastModified() > graphFile.lastModified();

        long lastScan = scanPreferences.getLong("start_scan", 0L);

        if (!modelFileExists && lastScan == 0L) {
            if (!graphFileExists) {
                Log.d(TAG, "Starting thread due to missing data");
                startAnalysisThread();
                return;
            }
            convertGraphToModel(graphFile, modelFile);
        }

        if (batteryLevel <= 0.25) {
            return; // Don't run analysis if battery level is low
        }

        double factor = 1d + rnd.nextDouble(); // Introduce randomness to avoid constant checks

        lastScan = Math.max(lastScan, graphFile.lastModified());

        // If battery is good and data is old, start the thread
        if (!onBattery && batteryLevel > 0.75) {
            long scanThreshold = getScanThreshold(factor);
            if (lastScan + scanThreshold < System.currentTimeMillis()) {
                Log.d(TAG, "Starting thread due to good battery and old data");
                startAnalysisThread();
            }
        }
    }

    private void convertGraphToModel(File graphFile, File modelFile) {
        try {
            UndirectedGraph<Long, Double> graph = GraphIO.load(graphFile);
            java.util.ArrayList<MergeContact> model = GraphConverter.convert(
                    graph,
                    getContentResolver().acquireContentProviderClient(ContactsContract.AUTHORITY_URI));
            ModelIO.store(model, modelFile);
        } catch (IOException e) {
            Log.e(TAG, "Error converting graph to model", e);
        }
    }

    private double getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        assert batteryIntent != null;
        int rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return (rawLevel >= 0 && scale > 0) ? (rawLevel / (double) scale) : -1;
    }

    private boolean isOnBattery() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        assert batteryIntent != null;
        return batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0;
    }

    private long getScanThreshold(double factor) {
        // Return scan threshold in milliseconds based on the factor (randomized)
        return (long) (7L * 24L * 60L * 60L * 1000L * factor); // Example threshold (7 days * factor)
    }

    private synchronized void startAnalysisThread() {
        if (analyzer != null && analyzer.isAlive()) return; // Avoid starting multiple threads

        analyzer = new AnalyzerThread(getApplicationContext());
        setupNotification();
        Intent intent = new Intent("de.measite.contactmerger.ANALYSE");
        intent.putExtra("event", "start");
        broadcastManager.sendBroadcast(intent);

        scanPreferences.edit().putLong("start_scan", System.currentTimeMillis()).apply();
        analyzer.start();
    }

    private synchronized void setupNotification() {
        final PendingIntent startPending = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                new Intent(getApplicationContext(), MergeActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setAction(Intent.ACTION_MAIN),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        final Notification.Builder builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(startPending)
                .setProgress(1000, 0, false)
                .setContentTitle("Analyzing your contacts")
                .setWhen(System.currentTimeMillis())
                .setContentText("0% done");

        startForeground(NOTIFICATION_ID, builder.build());

        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        analyzer.addListener(new ProgressListener() {
            long lastUpdate = System.currentTimeMillis();

            @Override
            public void abort() {
                stopForeground(true);
                notificationManager.cancel(NOTIFICATION_ID);
                broadcastManager.sendBroadcast(new Intent("de.measite.contactmerger.ANALYSE").putExtra("event", "abort"));
            }

            @Override
            public void update(float progress) {
                builder.setProgress(1000, (int) (1000 * progress), false);
                builder.setContentText(((int) (100 * progress)) + "% done");
                boolean completed = progress >= 1f;
                if (completed || System.currentTimeMillis() - lastUpdate > 200) {
                    notifyIfAllowed(notificationManager, builder.build());
                    lastUpdate = System.currentTimeMillis();

                    broadcastManager.sendBroadcast(new Intent("de.measite.contactmerger.ANALYSE")
                            .putExtra("event", "progress")
                            .putExtra("progress", progress));
                }

                if (completed) {
                    stopForeground(true);
                    handleCompletion(notificationManager);
                    broadcastManager.sendBroadcast(new Intent("de.measite.contactmerger.ANALYSE")
                            .putExtra("event", "finish"));
                    stopSelf();
                }
            }
        });
    }

    private void handleCompletion(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
        File modelFile = new File(getDatabasePath("contactsgraph"), "model.kryo.gz");
        int contactCount = 0;
        try {
            contactCount = ModelIO.load(modelFile).size();
        } catch (IOException e) {
            Log.e(TAG, "Error reading model file", e);
        }

        final Intent notificationIntent = new Intent(this, MergeActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("Contact analysis completed")
                .setContentText(contactCount + " contacts found")
                .setSmallIcon(R.drawable.notification_icon)
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .build();

        notifyIfAllowed(notificationManager, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Contact analysis",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows the progress of contact analysis.");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    private void notifyIfAllowed(NotificationManager manager, Notification notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    public class AnalyzerBinder extends android.os.Binder {
        public AnalyzerService getService() {
            return AnalyzerService.this;
        }
    }
}
