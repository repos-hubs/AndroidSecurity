package com.kindroid.security.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import com.kindroid.security.R;
import com.kindroid.security.ui.AppItemAdapter;


import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

/**
 * Contains shared programming interfaces.
 * All iptables "communication" is handled by this class.
 */
public final class FirewallApi {
	/** application version string */
	public static final String VERSION = "1.4.3-dev";
	/** special application UID used to indicate "any application" */
	public static final int SPECIAL_UID_ANY	= -10;
	/** root script filename */
	private static final String SCRIPT_FILE = "droidwall.sh";
	
	// Preferences
	public static final String PREFS_NAME 		= "DroidWallPrefs";
	public static final String PREF_3G_UIDS		= "AllowedUids3G";
	public static final String PREF_WIFI_UIDS	= "AllowedUidsWifi";
	public static final String PREF_PASSWORD 	= "Password";
	public static final String PREF_MODE 		= "BlockMode";
	public static final String PREF_ENABLED		= "Enabled";
	public static final String PREF_LOGENABLED	= "LogEnabled";
	// Modes
	public static final String MODE_WHITELIST = "whitelist";
	public static final String MODE_BLACKLIST = "blacklist";
	// Messages
	public static final String STATUS_CHANGED_MSG 	= "com.googlecode.droidwall.intent.action.STATUS_CHANGED";
	public static final String TOGGLE_REQUEST_MSG	= "com.googlecode.droidwall.intent.action.TOGGLE_REQUEST";
	public static final String STATUS_EXTRA			= "com.googlecode.droidwall.intent.extra.STATUS";
	
	// Cached applications
	public static FirewallApp applications[] = null;
	// Do we have root access?
	private static boolean hasroot = false;
	
	private static String cmdDefault = null;
	private static String cmdIptables_g1 = null;
	private static String cmdIptables_n1 = null;
	private static String cmdIptables_armv5 = null;
	
	public static int sDefaultIptables = 0;
	public static final String DEFAULT_IPTABLES_APP = "default_app_for_iptables";
    /**
     * Display a simple alert box
     * @param ctx context
     * @param msg message
     */
	public static void alert(Context ctx, CharSequence msg) {
    	if (ctx != null) {
        	new AlertDialog.Builder(ctx)
        	.setNeutralButton(android.R.string.ok, null)
        	.setMessage(msg)
        	.show();
    	}
    }
	/**
	 * Create the generic shell script header used to determine which iptables binary to use.
	 * @param ctx context
	 * @return script header
	 */
	private static String scriptHeader(Context ctx) {
		if(cmdDefault != null){
			return cmdDefault;
		}
		final String dir = ctx.getCacheDir().getAbsolutePath();
		cmdDefault = "" +
			"IPTABLES=iptables\n" +
			"BUSYBOX=busybox\n" +
			"GREP=grep\n" +
			"ECHO=echo\n" +
			"# Try to find busybox\n" +
			"if " + dir + "/busybox_g1 --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX="+dir+"/busybox_g1\n" +
			"	GREP=\"$BUSYBOX grep\"\n" +
			"	ECHO=\"$BUSYBOX echo\"\n" +
			"elif busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=busybox\n" +
			"elif /system/xbin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/xbin/busybox\n" +
			"elif /system/bin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/bin/busybox\n" +
			"fi\n" +
			"# Try to find grep\n" +
			"if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"	if $ECHO 1 | $BUSYBOX grep -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		GREP=\"$BUSYBOX grep\"\n" +
			"	fi\n" +
			"	# Grep is absolutely required\n" +
			"	if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		$ECHO The grep command is required. DroidWall will not work.\n" +
			"		exit 1\n" +
			"	fi\n" +
			"fi\n" +
			"# Try to find iptables\n" +
			"if iptables --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES=iptables\n" +
			"elif " + dir + "/iptables_g1 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_g1\n" +
			"elif " + dir + "/iptables_n1 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_n1\n" +
			"fi\n" +
			"";
		return cmdDefault;
	}
	private static String scriptHeaderG1(Context ctx) {
		if(cmdIptables_g1 != null){
			return cmdIptables_g1;
		}
		final String dir = ctx.getCacheDir().getAbsolutePath();
		cmdIptables_g1 = "" +
			"BUSYBOX=busybox\n" +
			"GREP=grep\n" +
			"ECHO=echo\n" +
			"# Try to find busybox\n" +
			"if " + dir + "/busybox_g1 --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX="+dir+"/busybox_g1\n" +
			"	GREP=\"$BUSYBOX grep\"\n" +
			"	ECHO=\"$BUSYBOX echo\"\n" +
			"elif busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=busybox\n" +
			"elif /system/xbin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/xbin/busybox\n" +
			"elif /system/bin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/bin/busybox\n" +
			"fi\n" +
			"# Try to find grep\n" +
			"if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"	if $ECHO 1 | $BUSYBOX grep -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		GREP=\"$BUSYBOX grep\"\n" +
			"	fi\n" +
			"	# Grep is absolutely required\n" +
			"	if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		$ECHO The grep command is required. DroidWall will not work.\n" +
			"		exit 1\n" +
			"	fi\n" +
			"fi\n" +
			"# Try to find iptables\n" +
			"if " + dir + "/iptables_g1 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_g1\n" +
			"elif " + dir + "/iptables_n1 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_n1\n" +
			"fi\n" +
			"";
		return cmdIptables_g1;
	}
	private static String scriptHeaderN1(Context ctx) {
		if(cmdIptables_n1 != null){
			return cmdIptables_n1;
		}
		final String dir = ctx.getCacheDir().getAbsolutePath();
		cmdIptables_n1 = "" +
			"BUSYBOX=busybox\n" +
			"GREP=grep\n" +
			"ECHO=echo\n" +
			"# Try to find busybox\n" +
			"if " + dir + "/busybox_g1 --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX="+dir+"/busybox_g1\n" +
			"	GREP=\"$BUSYBOX grep\"\n" +
			"	ECHO=\"$BUSYBOX echo\"\n" +
			"elif busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=busybox\n" +
			"elif /system/xbin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/xbin/busybox\n" +
			"elif /system/bin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/bin/busybox\n" +
			"fi\n" +
			"# Try to find grep\n" +
			"if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"	if $ECHO 1 | $BUSYBOX grep -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		GREP=\"$BUSYBOX grep\"\n" +
			"	fi\n" +
			"	# Grep is absolutely required\n" +
			"	if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		$ECHO The grep command is required. DroidWall will not work.\n" +
			"		exit 1\n" +
			"	fi\n" +
			"fi\n" +
			"# Try to find iptables\n" +
			"if " + dir + "/iptables_n1 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_n1\n" +
			"elif " + dir + "/iptables_g1 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_g1\n" +
			"fi\n" +
			"";
		return cmdIptables_n1;
	}
	
	private static String scriptHeaderArmV5(Context ctx) {
		if(cmdIptables_armv5 != null){
			return cmdIptables_armv5;
		}
		final String dir = ctx.getCacheDir().getAbsolutePath();
		cmdIptables_armv5 = "" +
			"BUSYBOX=busybox\n" +
			"GREP=grep\n" +
			"ECHO=echo\n" +
			"# Try to find busybox\n" +
			"if " + dir + "/busybox_g1 --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX="+dir+"/busybox_g1\n" +
			"	GREP=\"$BUSYBOX grep\"\n" +
			"	ECHO=\"$BUSYBOX echo\"\n" +
			"elif busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=busybox\n" +
			"elif /system/xbin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/xbin/busybox\n" +
			"elif /system/bin/busybox --help >/dev/null 2>/dev/null ; then\n" +
			"	BUSYBOX=/system/bin/busybox\n" +
			"fi\n" +
			"# Try to find grep\n" +
			"if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"	if $ECHO 1 | $BUSYBOX grep -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		GREP=\"$BUSYBOX grep\"\n" +
			"	fi\n" +
			"	# Grep is absolutely required\n" +
			"	if ! $ECHO 1 | $GREP -q 1 >/dev/null 2>/dev/null ; then\n" +
			"		$ECHO The grep command is required. DroidWall will not work.\n" +
			"		exit 1\n" +
			"	fi\n" +
			"fi\n" +
			"# Try to find iptables\n" +
			"if " + dir + "/iptables_av5 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_av5\n" +
			"elif " + dir + "/iptables_g1 --version >/dev/null 2>/dev/null ; then\n" +
			"	IPTABLES="+dir+"/iptables_g1\n" +
			"fi\n" +
			"";
		return cmdIptables_n1;
	}
	/**
	 * Copies a raw resource file, given its ID to the given location
	 * @param ctx context
	 * @param resid resource id
	 * @param file destination file
	 * @param mode file permissions (E.g.: "755")
	 * @throws IOException on error
	 * @throws InterruptedException when interrupted
	 */
	private static void copyRawFile(Context ctx, int resid, File file, String mode) throws IOException, InterruptedException
	{
		final String abspath = file.getAbsolutePath();
		// Write the iptables binary
		final FileOutputStream out = new FileOutputStream(file);
		final InputStream is = ctx.getResources().openRawResource(resid);
		byte buf[] = new byte[1024];
		int len;
		while ((len = is.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		out.close();
		is.close();
		// Change the permissions
		Runtime.getRuntime().exec("chmod "+mode+" "+abspath).waitFor();
	}
    /**
     * Purge and re-add all rules (internal implementation).
     * @param ctx application context (mandatory)
     * @param uidsWifi list of selected UIDs for WIFI to allow or disallow (depending on the working mode)
     * @param uids3g list of selected UIDs for 2G/3G to allow or disallow (depending on the working mode)
     * @param showErrors indicates if errors should be alerted
     */
	private static boolean applyIptablesRulesImpl(Context ctx, List<Integer> uidsWifi, List<Integer> uids3g, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		assertBinaries(ctx, showErrors);
		final String ITFS_WIFI[] = {"tiwlan+", "wlan+", "eth+"};
		final String ITFS_3G[] = {"rmnet+","pdp+","ppp+","uwbr+"};
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final boolean whitelist = prefs.getString(PREF_MODE, MODE_WHITELIST).equals(MODE_WHITELIST);
		final boolean blacklist = !whitelist;
		final boolean logenabled = ctx.getSharedPreferences(PREFS_NAME, 0).getBoolean(PREF_LOGENABLED, false);

    	final StringBuilder script = new StringBuilder();
		try {
			int code;
//			script.append(scriptHeader(ctx));
			script.append("" +
				"$IPTABLES --version || exit 1\n" +
				"# Create the droidwall chains if necessary\n" +
				"$IPTABLES -L droidwall >/dev/null 2>/dev/null || $IPTABLES --new droidwall || exit 2\n" +
				"$IPTABLES -L droidwall-3g >/dev/null 2>/dev/null || $IPTABLES --new droidwall-3g || exit 3\n" +
				"$IPTABLES -L droidwall-wifi >/dev/null 2>/dev/null || $IPTABLES --new droidwall-wifi || exit 4\n" +
				"$IPTABLES -L droidwall-reject >/dev/null 2>/dev/null || $IPTABLES --new droidwall-reject || exit 5\n" +
				"# Add droidwall chain to OUTPUT chain if necessary\n" +
				"$IPTABLES -L OUTPUT | $GREP -q droidwall || $IPTABLES -A OUTPUT -j droidwall || exit 6\n" +
				"# Flush existing rules\n" +
				"$IPTABLES -F droidwall || exit 7\n" +
				"$IPTABLES -F droidwall-3g || exit 8\n" +
				"$IPTABLES -F droidwall-wifi || exit 9\n" +
				"$IPTABLES -F droidwall-reject || exit 10\n" +
			"");
			// Check if logging is enabled
			if (logenabled) {
				script.append("" +
					"# Create the log and reject rules (ignore errors on the LOG target just in case it is not available)\n" +
					"$IPTABLES -A droidwall-reject -j LOG --log-prefix \"[DROIDWALL] \" --log-uid\n" +
					"$IPTABLES -A droidwall-reject -j REJECT || exit 11\n" +
				"");
			} else {
				script.append("" +
					"# Create the reject rule (log disabled)\n" +
					"$IPTABLES -A droidwall-reject -j REJECT || exit 11\n" +
				"");
			}
			if (whitelist && logenabled) {
				script.append("# Allow DNS lookups on white-list for a better logging (ignore errors)\n");
				script.append("$IPTABLES -A droidwall -p udp --dport 53 -j RETURN\n");
			}
			script.append("# Main rules (per interface)\n");
			for (final String itf : ITFS_3G) {
				script.append("$IPTABLES -A droidwall -o ").append(itf).append(" -j droidwall-3g || exit\n");
			}
			for (final String itf : ITFS_WIFI) {
				script.append("$IPTABLES -A droidwall -o ").append(itf).append(" -j droidwall-wifi || exit\n");
			}
			
			script.append("# Filtering rules\n");
			final String targetRule = (whitelist ? "RETURN" : "droidwall-reject");
			final boolean any_3g = uids3g.indexOf(SPECIAL_UID_ANY) >= 0;
			final boolean any_wifi = uidsWifi.indexOf(SPECIAL_UID_ANY) >= 0;
			if (whitelist && !any_wifi) {
				// When "white listing" wifi, we need to ensure that the dhcp and wifi users are allowed
				int uid = android.os.Process.getUidForName("dhcp");
				if (uid != -1) {
					script.append("# dhcp user\n");
					script.append("$IPTABLES -A droidwall-wifi -m owner --uid-owner ").append(uid).append(" -j RETURN || exit\n");
				}
				uid = android.os.Process.getUidForName("wifi");
				if (uid != -1) {
					script.append("# wifi user\n");
					script.append("$IPTABLES -A droidwall-wifi -m owner --uid-owner ").append(uid).append(" -j RETURN || exit\n");
				}
			}
			if (any_3g) {
				if (blacklist) {
					/* block any application on this interface */
					script.append("$IPTABLES -A droidwall-3g -j ").append(targetRule).append(" || exit\n");
				}
			} else {
				/* release/block individual applications on this interface */
				for (final Integer uid : uids3g) {
					script.append("$IPTABLES -A droidwall-3g -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
				}
			}
			if (any_wifi) {
				if (blacklist) {
					/* block any application on this interface */
					script.append("$IPTABLES -A droidwall-wifi -j ").append(targetRule).append(" || exit\n");
				}
			} else {
				/* release/block individual applications on this interface */
				for (final Integer uid : uidsWifi) {
					script.append("$IPTABLES -A droidwall-wifi -m owner --uid-owner ").append(uid).append(" -j ").append(targetRule).append(" || exit\n");
				}
			}
			if (whitelist) {
				if (!any_3g) {
					script.append("$IPTABLES -A droidwall-3g -j droidwall-reject || exit\n");
				}
				if (!any_wifi) {
					script.append("$IPTABLES -A droidwall-wifi -j droidwall-reject || exit\n");
				}
			}
	    	final StringBuilder res = new StringBuilder();
			code = runScriptAsRoot(ctx, script.toString(), res);
			if (code != 0) {
				String msg = res.toString();
				Log.e("DroidWall", msg);
				// Remove unnecessary help message from output
				if (msg.indexOf("\nTry `iptables -h' or 'iptables --help' for more information.") != -1) {
					msg = msg.replace("\nTry `iptables -h' or 'iptables --help' for more information.", "");
				}
				if (showErrors) {
					alert(ctx, "Error applying iptables rules. Exit code: " + code + "\n\n" + msg.trim());
				}
			} else {
				
				return true;
			}
		} catch (Exception e) {
			if (showErrors) alert(ctx, "error refreshing iptables: " + e);
		}
		return false;
    }
    /**
     * Purge and re-add all saved rules (not in-memory ones).
     * This is much faster than just calling "applyIptablesRules", since it don't need to read installed applications.
     * @param ctx application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
	public static boolean applySavedIptablesRules(Context ctx, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		final String savedUids_wifi = prefs.getString(PREF_WIFI_UIDS, "");
		final String savedUids_3g = prefs.getString(PREF_3G_UIDS, "");
		final List<Integer> uids_wifi = new LinkedList<Integer>();
		if (savedUids_wifi.length() > 0) {
			// Check which applications are allowed on wifi
			final StringTokenizer tok = new StringTokenizer(savedUids_wifi, "|");
			while (tok.hasMoreTokens()) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						uids_wifi.add(Integer.parseInt(uid));
					} catch (Exception ex) {
					}
				}
			}
		}
		final List<Integer> uids_3g = new LinkedList<Integer>();
		if (savedUids_3g.length() > 0) {
			// Check which applications are allowed on 2G/3G
			final StringTokenizer tok = new StringTokenizer(savedUids_3g, "|");
			while (tok.hasMoreTokens()) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						uids_3g.add(Integer.parseInt(uid));
					} catch (Exception ex) {
					}
				}
			}
		}
		return applyIptablesRulesImpl(ctx, uids_wifi, uids_3g, showErrors);
	}
	
	public synchronized static boolean applingIptablesRules(Context ctx, AppItemAdapter adapter, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
//		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
//		final String savedUids_wifi = prefs.getString(PREF_WIFI_UIDS, "");
//		final String savedUids_3g = prefs.getString(PREF_3G_UIDS, "");
		final List<Integer> uids_wifi = new LinkedList<Integer>();
		/*if (savedUids_wifi.length() > 0) {
			// Check which applications are allowed on wifi
			final StringTokenizer tok = new StringTokenizer(savedUids_wifi, "|");
			while (tok.hasMoreTokens()) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						uids_wifi.add(Integer.parseInt(uid));
					} catch (Exception ex) {
					}
				}
			}
		}*/
		final List<Integer> uids_3g = new LinkedList<Integer>();
	/*	if (savedUids_3g.length() > 0) {
			// Check which applications are allowed on 2G/3G
			final StringTokenizer tok = new StringTokenizer(savedUids_3g, "|");
			while (tok.hasMoreTokens()) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						uids_3g.add(Integer.parseInt(uid));
					} catch (Exception ex) {
					}
				}
			}
		}*/
		
		if (adapter != null && adapter.getCount() > 0) {
			for (int i = 0; i < adapter.getCount(); i++) {
				FirewallApp app = (FirewallApp)adapter.getItem(i);
				if (app.selected_3g) {
					uids_3g.add(app.uid);
				}
				if (app.selected_wifi) {
					uids_wifi.add(app.uid);
				}
			}
		}
		
		return applyIptablesRulesImpl(ctx, uids_wifi, uids_3g, showErrors);
	}
	
    /**
     * Purge and re-add all rules.
     * @param ctx application context (mandatory)
     * @param showErrors indicates if errors should be alerted
     */
	public synchronized static boolean applyIptablesRules(Context ctx, AppItemAdapter adapter, boolean showErrors) {
		if (ctx == null) {
			return false;
		}
		saveRules(ctx, adapter);
		return applySavedIptablesRules(ctx, showErrors);
    }
	
	/**
	 * Save current rules using the preferences storage.
	 * @param ctx application context (mandatory)
	 */
	public static void saveRules(Context ctx, AppItemAdapter adapter) {
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		if(adapter == null || adapter.getCount() == 0){
			return;
		}
		final FirewallApp[] apps = adapter.getItems();
		// Builds a pipe-separated list of names
		final StringBuilder newuids_wifi = new StringBuilder();
		final StringBuilder newuids_3g = new StringBuilder();
		for (int i=0; i<apps.length; i++) {
			if (apps[i].selected_wifi) {
				if (newuids_wifi.length() != 0) newuids_wifi.append('|');
				newuids_wifi.append(apps[i].uid);
			}
			if (apps[i].selected_3g) {
				if (newuids_3g.length() != 0) newuids_3g.append('|');
				newuids_3g.append(apps[i].uid);
			}
		}
		// save the new list of UIDs
		final Editor edit = prefs.edit();
		edit.putString(PREF_WIFI_UIDS, newuids_wifi.toString());
		edit.putString(PREF_3G_UIDS, newuids_3g.toString());
		edit.commit();
    }
	
	public static boolean purgeIptablesOnceReinstall(Context ctx, boolean showErrors) {
    	StringBuilder res = new StringBuilder();
		try {
			assertBinaries(ctx, showErrors);
			/*
			int code = runScriptAsRoot(ctx, scriptHeader(ctx) +
					"$IPTABLES -F droidwall\n" +
					"$IPTABLES -F droidwall-reject\n" +
					"$IPTABLES -F droidwall-3g\n" +
					"$IPTABLES -F droidwall-wifi\n", res);
			*/
			int code = runScriptAsRoot(ctx, 
					"$IPTABLES -F droidwall\n" +
					"$IPTABLES -F droidwall-reject\n" +
					"$IPTABLES -F droidwall-3g\n" +
					"$IPTABLES -F droidwall-wifi\n", res);
			Log.d("KindroidSecurity", "purgeIptablesOnceReinstall return code :" + code);
			if (code != 0 && code != 1) {
				if (showErrors) alert(ctx, "error purging iptables. exit code: " + code + "\n" + res);
				return false;
			}
			hasroot = true;
			return true;
		} catch (Exception e) {
			if (showErrors) alert(ctx, "error purging iptables: " + e);
			return false;
		}
    }
    
    /**
     * Purge all iptables rules.
     * @param ctx mandatory context
     * @param showErrors indicates if errors should be alerted
     * @return true if the rules were purged
     */
	public static boolean purgeIptables(Context ctx, boolean showErrors) {
    	StringBuilder res = new StringBuilder();
		try {
			assertBinaries(ctx, showErrors);
			/*
			int code = runScriptAsRoot(ctx, scriptHeader(ctx) +
					"$IPTABLES -F droidwall\n" +
					"$IPTABLES -F droidwall-reject\n" +
					"$IPTABLES -F droidwall-3g\n" +
					"$IPTABLES -F droidwall-wifi\n", res);
			*/
			int code = runScriptAsRoot(ctx, 
					"$IPTABLES -F droidwall\n" +
					"$IPTABLES -F droidwall-reject\n" +
					"$IPTABLES -F droidwall-3g\n" +
					"$IPTABLES -F droidwall-wifi\n", res);
			if (code != 0) {
				if (showErrors) alert(ctx, "error purging iptables. exit code: " + code + "\n" + res);
				return false;
			}
			return true;
		} catch (Exception e) {
			if (showErrors) alert(ctx, "error purging iptables: " + e);
			return false;
		}
    }
	
	/**
	 * Display iptables rules output
	 * @param ctx application context
	 */
	public static void showIptablesRules(Context ctx) {
		try {
    		final StringBuilder res = new StringBuilder();
    		/*
			runScriptAsRoot(ctx, scriptHeader(ctx) +
								 "$ECHO $IPTABLES\n" +
								 "$IPTABLES -L -v\n", res);
			*/
			alert(ctx, res);
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
	}

	/**
	 * Display logs
	 * @param ctx application context
     * @return true if the clogs were cleared
	 */
	public static boolean clearLog(Context ctx) {
		try {
			final StringBuilder res = new StringBuilder();
			int code = runScriptAsRoot(ctx, "dmesg -c >/dev/null || exit\n", res);
			if (code != 0) {
				alert(ctx, res);
				return false;
			}
			return true;
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
		return false;
	}
	/**
	 * Display logs
	 * @param ctx application context
	 */
	public static void showLog(Context ctx) {
		try {
    		StringBuilder res = new StringBuilder();
    		/*
			int code = runScriptAsRoot(ctx, scriptHeader(ctx) +
					"dmesg | $GREP DROIDWALL\n", res);
			*/
    		int code = runScriptAsRoot(ctx, 
					"dmesg | $GREP DROIDWALL\n", res);
			if (code != 0) {
				if (res.length() == 0) {
					res.append("Log is empty");
				}
				alert(ctx, res);
				return;
			}
			final BufferedReader r = new BufferedReader(new StringReader(res.toString()));
			final Integer unknownUID = -99;
			res = new StringBuilder();
			String line;
			int start, end;
			Integer appid;
			final HashMap<Integer, LogInfo> map = new HashMap<Integer, LogInfo>();
			LogInfo loginfo = null;
			while ((line = r.readLine()) != null) {
				if (line.indexOf("[DROIDWALL]") == -1) continue;
				appid = unknownUID;
				if (((start=line.indexOf("UID=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					appid = Integer.parseInt(line.substring(start+4, end));
				}
				loginfo = map.get(appid);
				if (loginfo == null) {
					loginfo = new LogInfo();
					map.put(appid, loginfo);
				}
				loginfo.totalBlocked += 1;
				if (((start=line.indexOf("DST=")) != -1) && ((end=line.indexOf(" ", start)) != -1)) {
					String dst = line.substring(start+4, end);
					if (loginfo.dstBlocked.containsKey(dst)) {
						loginfo.dstBlocked.put(dst, loginfo.dstBlocked.get(dst) + 1);
					} else {
						loginfo.dstBlocked.put(dst, 1);
					}
				}
			}
			final FirewallApp[] apps = getApps(ctx);
			for (Integer id : map.keySet()) {
				res.append("App ID ");
				if (id != unknownUID) {
					res.append(id);
					for (FirewallApp app : apps) {
						if (app.uid == id) {
							res.append(" (").append(app.names[0]);
							if (app.names.length > 1) {
								res.append(", ...)");
							} else {
								res.append(")");
							}
							break;
						}
					}
				} else {
					res.append("(unknown)");
				}
				loginfo = map.get(id);
				res.append(" - Blocked ").append(loginfo.totalBlocked).append(" packets");
				if (loginfo.dstBlocked.size() > 0) {
					res.append(" (");
					boolean first = true;
					for (String dst : loginfo.dstBlocked.keySet()) {
						if (!first) {
							res.append(", ");
						}
						res.append(loginfo.dstBlocked.get(dst)).append(" packets for ").append(dst);
						first = false;
					}
					res.append(")");
				}
				res.append("\n\n");
			}
			if (res.length() == 0) {
				res.append("Log is empty");
			}
			alert(ctx, res);
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
	}
	public static FirewallApp[] getApps(Context ctx, Handler handler) {
		if (applications != null) {
			// return cached instance
			return applications;
		}
		
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		boolean mContainWifiUids = prefs.contains(PREF_WIFI_UIDS);
		boolean mContain3GUids = prefs.contains(PREF_3G_UIDS);
		// allowed application names separated by pipe '|' (persisted)
		final String savedUids_wifi = prefs.getString(PREF_WIFI_UIDS, "");
		final String savedUids_3g = prefs.getString(PREF_3G_UIDS, "");
		int selected_wifi[] = new int[0];
		int selected_3g[] = new int[0];
		if (savedUids_wifi.length() > 0) {
			// Check which applications are allowed
			final StringTokenizer tok = new StringTokenizer(savedUids_wifi, "|");
			selected_wifi = new int[tok.countTokens()];
			for (int i=0; i<selected_wifi.length; i++) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						selected_wifi[i] = Integer.parseInt(uid);
					} catch (Exception ex) {
						selected_wifi[i] = -1;
					}
				}
			}
			// Sort the array to allow using "Arrays.binarySearch" later
			Arrays.sort(selected_wifi);
		}
		if (savedUids_3g.length() > 0) {
			// Check which applications are allowed
			final StringTokenizer tok = new StringTokenizer(savedUids_3g, "|");
			selected_3g = new int[tok.countTokens()];
			for (int i=0; i<selected_3g.length; i++) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						selected_3g[i] = Integer.parseInt(uid);
					} catch (Exception ex) {
						selected_3g[i] = -1;
					}
				}
			}
			// Sort the array to allow using "Arrays.binarySearch" later
			Arrays.sort(selected_3g);
		}
		try {
			final PackageManager pkgmanager = ctx.getPackageManager();
			final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(0);
			final HashMap<Integer, FirewallApp> map = new HashMap<Integer, FirewallApp>();
			final Editor edit = prefs.edit();
			boolean changed = false;
			String name = null;
			String cachekey = null;
			FirewallApp app = null;
			int s = 0;
			for (final ApplicationInfo apinfo : installed) {
				app = map.get(apinfo.uid);
				// filter applications which are not allowed to access the Internet
				if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName)) {
					continue;
				}
				// try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
				cachekey = "cache.label."+apinfo.packageName;
				name = prefs.getString(cachekey, "");
				if (name.length() == 0) {
					// get label and put on cache
					name = pkgmanager.getApplicationLabel(apinfo).toString();
					edit.putString(cachekey, name);
					changed = true;
				}
				if (app == null) {
					app = new FirewallApp();
					app.uid = apinfo.uid;
					app.names = new String[] { name };
					app.mIcon = pkgmanager.getApplicationIcon(apinfo);
					//add package name
					app.packgeName = apinfo.packageName;
					map.put(apinfo.uid, app);
				} else {
					final String newnames[] = new String[app.names.length + 1];
					System.arraycopy(app.names, 0, newnames, 0, app.names.length);
					newnames[app.names.length] = name;
					app.names = newnames;
					app.packgeName = apinfo.packageName;
				}
				// check if this application is selected
				if (!app.selected_wifi && Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
					app.selected_wifi = true;
				}
				if (!mContainWifiUids) {
					app.selected_wifi = true;
				}
				if (!app.selected_3g && Arrays.binarySearch(selected_3g, app.uid) >= 0) {
					app.selected_3g = true;
				}
				if (!mContain3GUids) {
					app.selected_3g = true;
				}
				s++;
				Message msg = new Message();
				msg.what = 5;
				msg.arg1 = Double.valueOf((s * 1.0 / installed.size()) * 100).intValue();
				handler.sendMessage(msg);
			}
			if (changed) {
				edit.commit();
			}
			/* add special applications to the list */
			/*
			boolean wifi_special = false ;
			boolean g3_special = false;
			if (savedUids_wifi.length() <= 0) {
				wifi_special = true;
			}
			if (savedUids_3g.length() <= 0) {
				g3_special = true;
			}
			final FirewallApp special[] = {
				new FirewallApp(SPECIAL_UID_ANY, ctx.getResources().getString(R.string.any_applications), wifi_special, g3_special),
				new FirewallApp(android.os.Process.getUidForName("root"), ctx.getResources().getString(R.string.root_applications), wifi_special, g3_special),
				new FirewallApp(android.os.Process.getUidForName("media"), "Media server", wifi_special, g3_special),
				new FirewallApp(android.os.Process.getUidForName("vpn"), "VPN networking", wifi_special, g3_special),
			};
			for (int i=0; i<special.length; i++) {
				app = special[i];
				if (app.uid != -1 && !map.containsKey(app.uid)) {
					// check if this application is allowed
					if (Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
						app.selected_wifi = true;
					}
					if (Arrays.binarySearch(selected_3g, app.uid) >= 0) {
						app.selected_3g = true;
					}
					map.put(app.uid, app);
				}
			}
			*/
			applications = new FirewallApp[map.size()];
			int index = 0;
			for (FirewallApp application : map.values()) 
				applications[index++] = application;
			return applications;
		} catch (Exception e) {
			//alert(ctx, "error: " + e);
			e.printStackTrace();
		}
		return null;
	}
    /**
     * @param ctx application context (mandatory)
     * @return a list of applications
     */
	public static FirewallApp[] getApps(Context ctx) {
		if (applications != null) {
			// return cached instance
			return applications;
		}
		
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		boolean mContainWifiUids = prefs.contains(PREF_WIFI_UIDS);
		boolean mContain3GUids = prefs.contains(PREF_3G_UIDS);
		// allowed application names separated by pipe '|' (persisted)
		final String savedUids_wifi = prefs.getString(PREF_WIFI_UIDS, "");
		final String savedUids_3g = prefs.getString(PREF_3G_UIDS, "");
		int selected_wifi[] = new int[0];
		int selected_3g[] = new int[0];
		if (savedUids_wifi.length() > 0) {
			// Check which applications are allowed
			final StringTokenizer tok = new StringTokenizer(savedUids_wifi, "|");
			selected_wifi = new int[tok.countTokens()];
			for (int i=0; i<selected_wifi.length; i++) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						selected_wifi[i] = Integer.parseInt(uid);
					} catch (Exception ex) {
						selected_wifi[i] = -1;
					}
				}
			}
			// Sort the array to allow using "Arrays.binarySearch" later
			Arrays.sort(selected_wifi);
		}
		if (savedUids_3g.length() > 0) {
			// Check which applications are allowed
			final StringTokenizer tok = new StringTokenizer(savedUids_3g, "|");
			selected_3g = new int[tok.countTokens()];
			for (int i=0; i<selected_3g.length; i++) {
				final String uid = tok.nextToken();
				if (!uid.equals("")) {
					try {
						selected_3g[i] = Integer.parseInt(uid);
					} catch (Exception ex) {
						selected_3g[i] = -1;
					}
				}
			}
			// Sort the array to allow using "Arrays.binarySearch" later
			Arrays.sort(selected_3g);
		}
		try {
			final PackageManager pkgmanager = ctx.getPackageManager();
			final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(0);
			final HashMap<Integer, FirewallApp> map = new HashMap<Integer, FirewallApp>();
			final Editor edit = prefs.edit();
			boolean changed = false;
			String name = null;
			String cachekey = null;
			FirewallApp app = null;
			for (final ApplicationInfo apinfo : installed) {
				app = map.get(apinfo.uid);
				// filter applications which are not allowed to access the Internet
				if (app == null && PackageManager.PERMISSION_GRANTED != pkgmanager.checkPermission(Manifest.permission.INTERNET, apinfo.packageName)) {
					continue;
				}
				// try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
				cachekey = "cache.label."+apinfo.packageName;
				name = prefs.getString(cachekey, "");
				if (name.length() == 0) {
					// get label and put on cache
					name = pkgmanager.getApplicationLabel(apinfo).toString();
					edit.putString(cachekey, name);
					changed = true;
				}
				if (app == null) {
					app = new FirewallApp();
					app.uid = apinfo.uid;
					app.names = new String[] { name };
					app.mIcon = pkgmanager.getApplicationIcon(apinfo);
					map.put(apinfo.uid, app);
				} else {
					final String newnames[] = new String[app.names.length + 1];
					System.arraycopy(app.names, 0, newnames, 0, app.names.length);
					newnames[app.names.length] = name;
					app.names = newnames;
				}
				// check if this application is selected
				if (!app.selected_wifi && Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
					app.selected_wifi = true;
				}
				if (!app.selected_3g && Arrays.binarySearch(selected_3g, app.uid) >= 0) {
					app.selected_3g = true;
				}
				
				if (!mContainWifiUids) {
					app.selected_wifi = true;
				}
				if (!mContain3GUids) {
					app.selected_3g = true;
				}
			}
			if (changed) {
				edit.commit();
			}
			/* add special applications to the list */
			/*
			boolean wifi_special = false ;
			boolean g3_special = false;
			if (savedUids_wifi.length() <= 0) {
				wifi_special = true;
			}
			if (savedUids_3g.length() <= 0) {
				g3_special = true;
			}
			final FirewallApp special[] = {
				new FirewallApp(SPECIAL_UID_ANY, ctx.getResources().getString(R.string.any_applications), wifi_special, g3_special),
				new FirewallApp(android.os.Process.getUidForName("root"), ctx.getResources().getString(R.string.root_applications), wifi_special, g3_special),
				new FirewallApp(android.os.Process.getUidForName("media"), "Media server", wifi_special, g3_special),
				new FirewallApp(android.os.Process.getUidForName("vpn"), "VPN networking", wifi_special, g3_special),
			};
			for (int i=0; i<special.length; i++) {
				app = special[i];
				if (app.uid != -1 && !map.containsKey(app.uid)) {
					// check if this application is allowed
					if (Arrays.binarySearch(selected_wifi, app.uid) >= 0) {
						app.selected_wifi = true;
					}
					if (Arrays.binarySearch(selected_3g, app.uid) >= 0) {
						app.selected_3g = true;
					}
					map.put(app.uid, app);
				}
			}
			*/
			applications = new FirewallApp[map.size()];
			int index = 0;
			for (FirewallApp application : map.values()) 
				applications[index++] = application;
			return applications;
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
		return null;
	}
	/**
	 * Check if we have root access
	 * @param ctx mandatory context
     * @param showErrors indicates if errors should be alerted
	 * @return boolean true if we have root
	 */
	public static boolean hasRootAccess(Context ctx, boolean showErrors) {
		if (hasroot) return true;
		final StringBuilder res = new StringBuilder();
		try {
			int ret = runScriptAsRoot(ctx, "su\nexit 0", res);

			// Run an empty script just to check root access
			if (ret == 0 || ret == 1) {
				hasroot = true;
				return true;
			}
		} catch (Exception e) {
		}
		if (showErrors) {
			alert(ctx, ctx.getString(R.string.permissionRequired) + res.toString());
		}
		return false;
	}
    /**
     * Runs a script, wither as root or as a regular user (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScript(Context ctx, String script, StringBuilder res, long timeout, boolean asroot) {
		final File file = new File(ctx.getCacheDir(), SCRIPT_FILE);
		final ScriptRunner runner = new ScriptRunner(ctx, file, script, res, asroot);
		runner.start();
		try {
			if (timeout > 0) {
				runner.join(timeout);
			} else {
				runner.join();
			}
			if (runner.isAlive()) {
				// Timed-out
				runner.interrupt();
				runner.join(150);
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex) {}
		return runner.exitcode;
	}
    /**
     * Runs a script as root (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res, long timeout) {
		return runScript(ctx, script, res, timeout, true);
    }
    /**
     * Runs a script as root (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res) throws IOException {
		return runScriptAsRoot(ctx, script, res, 40000);
	}
    /**
     * Runs a script as a regular user (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
	public static int runScript(Context ctx, String script, StringBuilder res) throws IOException {
		return runScript(ctx, script, res, 40000, false);
	}
	/**
	 * Asserts that the binary files are installed in the cache directory.
	 * @param ctx context
     * @param showErrors indicates if errors should be alerted
	 * @return false if the binary files could not be installed
	 */
	public static boolean assertBinaries(Context ctx, boolean showErrors) {
		boolean changed = false;
		try {
			// Check iptables_g1
			File file = new File(ctx.getCacheDir(), "iptables_g1");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.iptables_g1, file, "755");
				changed = true;
			}
			// Check iptables_n1
			file = new File(ctx.getCacheDir(), "iptables_n1");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.iptables_n1, file, "755");
				changed = true;
			}
			// Check busybox
			file = new File(ctx.getCacheDir(), "busybox_g1");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.busybox_g1, file, "755");
				changed = true;
			}
			// Check iptables_av5
			file = new File(ctx.getCacheDir(), "iptables_av5");
			if (!file.exists()) {
				copyRawFile(ctx, R.raw.iptables_av5, file, "755");
				changed = true;
			}
		} catch (Exception e) {
			if (showErrors) alert(ctx, "Error installing binary files: " + e);
			return false;
		}
		
		return true;
	}
	/**
	 * Check if the firewall is enabled
	 * @param ctx mandatory context
	 * @return boolean
	 */
	public static boolean isEnabled(Context ctx) {
		if (ctx == null) return false;
		return ctx.getSharedPreferences(PREFS_NAME, 0).getBoolean(PREF_ENABLED, false);
	}
	
	/**
	 * Defines if the firewall is enabled and broadcasts the new status
	 * @param ctx mandatory context
	 * @param enabled enabled flag
	 */
	public static void setEnabled(Context ctx, boolean enabled) {
		if (ctx == null) return;
		final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
		if (prefs.getBoolean(PREF_ENABLED, false) == enabled) {
			return;
		}
		final Editor edit = prefs.edit();
		edit.putBoolean(PREF_ENABLED, enabled);
		edit.commit();
		/* notify */
		final Intent message = new Intent(FirewallApi.STATUS_CHANGED_MSG);
        message.putExtra(FirewallApi.STATUS_EXTRA, enabled);
        ctx.sendBroadcast(message);
	}

    /**
     * Small structure to hold an application info
     */
	public static final class FirewallApp {
		/** linux user id */
    	int uid;
    	/** application names belonging to this user id */
    	public String names[];
    	/** indicates if this application is selected for wifi */
    	public boolean selected_wifi;
    	/** indicates if this application is selected for 3g */
    	public boolean selected_3g;
    	/** toString cache */
    	String tostr;
    	//package name
    	public String packgeName;
    	//total day
    	public long total_day;
    	// total month
    	public long total_month;
    	
    	public Drawable mIcon;
    	
    	public FirewallApp() {
    	}
    	public FirewallApp(int uid, String name, boolean selected_wifi, boolean selected_3g) {
    		this.uid = uid;
    		this.names = new String[] {name};
    		this.selected_wifi = selected_wifi;
    		this.selected_3g = selected_3g;  		
    	}
    	/**
    	 * Screen representation of this application
    	 */
    	@Override
    	public String toString() {
    		if (tostr == null) {
        		final StringBuilder s = new StringBuilder();
        		//if (uid > 0) s.append(uid + ": ");
//        		for (int i=0; i<names.length; i++) {
//        			if (i != 0) s.append(", ");
//        			s.append(names[i]);
//        		}
        		if(names!=null&&names.length>0){
        			s.append(names[0]);
        		}
        		
        		tostr = s.toString();
    		}
    		return tostr;
    	}
    	
    	public Drawable getIcon() {
    		return mIcon;
    	}
    	
    }
    /**
     * Small internal structure used to hold log information
     */
	private static final class LogInfo {
		private int totalBlocked; // Total number of packets blocked
		private HashMap<String, Integer> dstBlocked; // Number of packets blocked per destination IP address
		private LogInfo() {
			this.dstBlocked = new HashMap<String, Integer>();
		}
	}
	/**
	 * Internal thread used to execute scripts (as root or not).
	 */
	private static final class ScriptRunner extends Thread {
		private final File file;
		private final String script;
		private final StringBuilder res;
		private final boolean asroot;
		public int exitcode = -1;
		private Process exec;
		private Context mContext;
		
		/**
		 * Creates a new script runner.
		 * @param file temporary script file
		 * @param script script to run
		 * @param res response output
		 * @param asroot if true, executes the script as root
		 */
		public ScriptRunner(Context context, File file, String script, StringBuilder res, boolean asroot) {
			this.file = file;
			this.script = script;
			this.res = res;
			this.asroot = asroot;
			this.mContext = context;
		}
		private void runDefaultIptabls(){
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				out.write("#!/system/bin/sh\n");
				out.write(scriptHeader(mContext));
				out.write(script);
				if (!script.endsWith("\n")) out.write("\n");
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath + " root");
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				InputStreamReader r = new InputStreamReader(exec.getInputStream());
				final char buf[] = new char[1024];
				int read = 0;
				// Consume the "stdout"
				while ((read=r.read(buf)) != -1) {
					if (res != null) res.append(buf, 0, read);
				}
				// Consume the "stderr"
				r = new InputStreamReader(exec.getErrorStream());
				read=0;
				while ((read=r.read(buf)) != -1) {
					if (res != null) res.append(buf, 0, read);
				}
				// get the process exit code
				if (exec != null) this.exitcode = exec.waitFor();
			} catch (InterruptedException ex) {
				if (res != null) res.append("\nOperation timed-out");
				this.exitcode = -1;
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
				this.exitcode = -1;
			} finally {
				Log.e("KindroidSecurity", "exitcode :" + exitcode);
				destroy();
			}
		}
		private void runIptablesG1(){
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				out.write("#!/system/bin/sh\n");
				out.write(scriptHeaderG1(mContext));
				out.write(script);
				if (!script.endsWith("\n")) out.write("\n");
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath + " root");
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				
				// get the process exit code
				if (exec != null) this.exitcode = exec.waitFor();
			} catch (InterruptedException ex) {
				if (res != null) res.append("\nOperation timed-out");
				this.exitcode = -1;
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
				this.exitcode = -1;
			} finally {
				Log.e("KindroidSecurity", "exitcode :" + exitcode);
				destroy();
			}
		}
		private void runIptablesN1(){
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				out.write("#!/system/bin/sh\n");
				out.write(scriptHeaderN1(mContext));
				out.write(script);
				if (!script.endsWith("\n")) out.write("\n");
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath + " root");
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				
				// get the process exit code
				if (exec != null) this.exitcode = exec.waitFor();
			} catch (InterruptedException ex) {
				if (res != null) res.append("\nOperation timed-out");
				this.exitcode = -1;
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
				this.exitcode = -1;
			} finally {
				Log.e("KindroidSecurity", "exitcode :" + exitcode);
				destroy();
			}
		}
		private void runIptablesArmV5(){
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				out.write("#!/system/bin/sh\n");
				out.write(scriptHeaderArmV5(mContext));
				out.write(script);
				if (!script.endsWith("\n")) out.write("\n");
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath + " root");
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				
				// get the process exit code
				if (exec != null) this.exitcode = exec.waitFor();
			} catch (InterruptedException ex) {
				if (res != null) res.append("\nOperation timed-out");
				this.exitcode = -1;
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
				this.exitcode = -1;
			} finally {
				Log.e("KindroidSecurity", "exitcode :" + exitcode);
				destroy();
			}
		}
		@Override
		public void run() {
			int flag = sDefaultIptables;
			switch(flag){
			case 0:
				runDefaultIptabls();
				if(this.exitcode != 0){
					runIptablesG1();
					if(this.exitcode == 0){
						SharedPreferences sp = KindroidSecurityApplication.sh;
						Editor editor = sp.edit();
						editor.putInt(DEFAULT_IPTABLES_APP, 1);
						editor.commit();
						sDefaultIptables = 1;
					}else{
						runIptablesN1();
						if(this.exitcode == 0){
							SharedPreferences sp = KindroidSecurityApplication.sh;
							Editor editor = sp.edit();
							editor.putInt(DEFAULT_IPTABLES_APP, 2);
							editor.commit();
							sDefaultIptables = 2;
						}else{
							runIptablesArmV5();
							if(this.exitcode == 0){
								SharedPreferences sp = KindroidSecurityApplication.sh;
								Editor editor = sp.edit();
								editor.putInt(DEFAULT_IPTABLES_APP, 3);
								editor.commit();
								sDefaultIptables = 3;
							}
						}
					}
				}
				break;
			case 1:
				runIptablesG1();
				if(this.exitcode != 0){
					runIptablesN1();
					if(this.exitcode == 0){
						SharedPreferences sp = KindroidSecurityApplication.sh;
						Editor editor = sp.edit();
						editor.putInt(DEFAULT_IPTABLES_APP, 2);
						editor.commit();
						sDefaultIptables = 2;
					}else{
						runIptablesArmV5();
						if(this.exitcode == 0){
							SharedPreferences sp = KindroidSecurityApplication.sh;
							Editor editor = sp.edit();
							editor.putInt(DEFAULT_IPTABLES_APP, 3);
							editor.commit();
							sDefaultIptables = 3;
						}else{
							runDefaultIptabls();
							if(this.exitcode == 0){
								SharedPreferences sp = KindroidSecurityApplication.sh;
								Editor editor = sp.edit();
								editor.putInt(DEFAULT_IPTABLES_APP, 0);
								editor.commit();
								sDefaultIptables = 0;
							}
						}
					}
				}
				break;
			case 2:
				runIptablesN1();
				if(this.exitcode != 0){
					runIptablesArmV5();
					if(this.exitcode == 0){
						SharedPreferences sp = KindroidSecurityApplication.sh;
						Editor editor = sp.edit();
						editor.putInt(DEFAULT_IPTABLES_APP, 3);
						editor.commit();
						sDefaultIptables = 3;
					}else{
						runDefaultIptabls();
						if(this.exitcode == 0){
							SharedPreferences sp = KindroidSecurityApplication.sh;
							Editor editor = sp.edit();
							editor.putInt(DEFAULT_IPTABLES_APP, 0);
							editor.commit();
							sDefaultIptables = 0;
						}else{
							runIptablesG1();
							if(this.exitcode == 0){
								SharedPreferences sp = KindroidSecurityApplication.sh;
								Editor editor = sp.edit();
								editor.putInt(DEFAULT_IPTABLES_APP, 1);
								editor.commit();
								sDefaultIptables = 1;
							}
						}
					}
				}
				break;
			case 3:
				runIptablesArmV5();
				if(this.exitcode != 0){
					runDefaultIptabls();
					if(this.exitcode == 0){
						SharedPreferences sp = KindroidSecurityApplication.sh;
						Editor editor = sp.edit();
						editor.putInt(DEFAULT_IPTABLES_APP, 0);
						editor.commit();
						sDefaultIptables = 0;
					}else{
						runIptablesG1();
						if(this.exitcode == 0){
							SharedPreferences sp = KindroidSecurityApplication.sh;
							Editor editor = sp.edit();
							editor.putInt(DEFAULT_IPTABLES_APP, 1);
							editor.commit();
							sDefaultIptables = 1;
						}else{
							runIptablesN1();
							if(this.exitcode == 0){
								SharedPreferences sp = KindroidSecurityApplication.sh;
								Editor editor = sp.edit();
								editor.putInt(DEFAULT_IPTABLES_APP, 2);
								editor.commit();
								sDefaultIptables = 2;
							}
						}
					}
				}
				break;
			}
		}
		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy() {
			if (exec != null) exec.destroy();
			exec = null;
		}
	}
}
