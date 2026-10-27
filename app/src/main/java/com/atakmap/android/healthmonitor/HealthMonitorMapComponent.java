
package com.atakmap.android.healthmonitor;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.android.healthmonitor.plugin.HealthMonitorLifecycle;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.healthmonitor.plugin.R;

import java.util.HashMap;
import java.util.Map;

public class HealthMonitorMapComponent extends DropDownMapComponent {

    private static final String TAG = "HealthMonitorMapComponent";

    private static final String PREF_KEY = "healthMonitorPreference";

    // Meta keys on the self marker, written by HealthMonitorDropDownReceiver
    public static final String META_BPM = "bpm";
    public static final String META_BPM_TIME = "bpm_time";

    // A reading older than this is not sent anymore (strap removed or disconnected)
    public static final long MAX_BPM_AGE_MS = 60_000;

    private Context pluginContext;

    private HealthMonitorDropDownReceiver ddr;

    private CotDetailHandler healthDetailHandler;

    // Rows of the "other people's heart rate" table, keyed by uid. Only touched on the UI thread.
    private final Map<String, TextView[]> rows = new HashMap<>();

    public void onCreate(final Context context, Intent intent, final MapView view) {

        context.setTheme(R.style.ATAKPluginTheme);
        super.onCreate(context, intent, view);
        pluginContext = context;

        ddr = new HealthMonitorDropDownReceiver(view, context);

        Log.d(TAG, "registering the plugin filter");
        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(HealthMonitorDropDownReceiver.SHOW_PLUGIN);
        registerDropDownReceiver(ddr, ddFilter);

        DeviceList devicesList = new DeviceList(view.getContext());
        ToolsPreferenceFragment
                .register(
                        new ToolsPreferenceFragment.ToolPreference(
                                "Health Monitor Preferences",
                                "Preferences for the Health Monitor Plugin",
                                PREF_KEY,
                                context.getResources().getDrawable(R.drawable.ic_launcher, null),
                                new HealthMonitorPreferenceFragment(context, devicesList)));


        CotDetailManager.getInstance().registerHandler(
            healthDetailHandler = new CotDetailHandler("__health") {
                private final String TAG = "HealthCotDetailHandler";
                private int getInt(String val, int fallback) {
                    try {
                        return Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                }
                @Override
                public CommsMapComponent.ImportResult toItemMetadata(MapItem mapItem, CotEvent event, CotDetail detail) {
                    int bpm = getInt(detail.getAttribute("bpm"), 0);
                    mapItem.setMetaInteger(META_BPM, bpm);

                    CotDetail contact = event.findDetail("contact");
                    String callsign = contact != null ? contact.getAttribute("callsign") : null;
                    if (callsign == null)
                        callsign = event.getUID();
                    showRemoteBpm(event.getUID(), callsign, bpm);
                    return CommsMapComponent.ImportResult.SUCCESS;
                }
                @Override
                public boolean toCotDetail(MapItem item, CotEvent event, CotDetail root) {
                    // Only our own position report carries a heart rate, and only a fresh one
                    if (!MapView.getDeviceUid().equals(item.getUID()))
                        return false;
                    if (!item.hasMetaValue(META_BPM))
                        return false;
                    long age = SystemClock.elapsedRealtime() - item.getMetaLong(META_BPM_TIME, 0);
                    if (age > MAX_BPM_AGE_MS)
                        return false;
                    CotDetail health = new CotDetail("__health");
                    health.setAttribute("bpm", String.valueOf(item.getMetaInteger(META_BPM, 0)));
                    root.addChild(health);
                    return true;
                }
            });
    }

    private void showRemoteBpm(final String uid, final String callsign, final int bpm) {
        HealthMonitorLifecycle.getActivity().runOnUiThread(() -> {
            TextView[] views = rows.get(uid);
            if (views == null) {
                Log.d(TAG, "Row not found for: " + callsign);
                TableRow row = new TableRow(pluginContext);
                row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));

                TextView callsignTV = new TextView(pluginContext);
                row.addView(callsignTV);

                TextView space = new TextView(pluginContext);
                space.setText("\t");
                row.addView(space);

                TextView bpmTV = new TextView(pluginContext);
                bpmTV.setGravity(Gravity.RIGHT);
                row.addView(bpmTV);

                ddr.healthTable.addView(row, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
                views = new TextView[] {callsignTV, bpmTV};
                rows.put(uid, views);
            }
            // The callsign can change, so it is refreshed on every update
            views[0].setText("Callsign: " + callsign);
            views[1].setText("BPM: " + bpm);
        });
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
        CotDetailManager.getInstance().unregisterHandler(healthDetailHandler);
        ToolsPreferenceFragment.unregister(PREF_KEY);
    }

}
