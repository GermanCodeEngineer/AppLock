package android.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.LocusId;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.ParceledListSlice;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.WorkSource;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import java.util.List;

public interface IActivityManager {

    // WARNING: when these transactions are updated, check if they are any callers on the native
    // side. If so, make sure they are using the correct transaction ids and arguments.
    // If a transaction which will also be used on the native side is being inserted, add it to
    // below block of transactions.

    // Since these transactions are also called from native code, these must be kept in sync with
    // the ones in frameworks/native/libs/binder/include_activitymanager/binder/ActivityManager.h
    // =============== Beginning of transactions used on native side as well ======================
    ParcelFileDescriptor openContentUri(String uriString);

    void registerUidObserver(IUidObserver observer, int which, int cutpoint,
                             String callingPackage);

    void unregisterUidObserver(IUidObserver observer);

    /**
     * Registers a UidObserver with a uid filter.
     *
     * @param observer       The UidObserver implementation to register.
     * @param which          A bitmask of events to observe. See ActivityManager.UID_OBSERVER_*.
     * @param cutpoint       The cutpoint for onUidStateChanged events. When the state crosses this
     *                       threshold in either direction, onUidStateChanged will be called.
     * @param callingPackage The name of the calling package.
     * @param uids           A list of uids to watch. If all uids are to be watched, use
     *                       registerUidObserver instead.
     * @return Returns A binder token identifying the UidObserver registration.
     * @throws RemoteException
     */
    @RequiresPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    IBinder registerUidObserverForUids(IUidObserver observer, int which, int cutpoint,
                                       String callingPackage, int[] uids);

    /**
     * Adds a uid to the list of uids that a UidObserver will receive updates about.
     *
     * @param observerToken  The binder token identifying the UidObserver registration.
     * @param callingPackage The name of the calling package.
     * @param uid            The uid to watch.
     * @throws RemoteException
     */
    void addUidToObserver(IBinder observerToken, String callingPackage, int uid);

    /**
     * Removes a uid from the list of uids that a UidObserver will receive updates about.
     *
     * @param observerToken  The binder token identifying the UidObserver registration.
     * @param callingPackage The name of the calling package.
     * @param uid            The uid to stop watching.
     * @throws RemoteException
     */
    void removeUidFromObserver(IBinder observerToken, String callingPackage, int uid);

    boolean isUidActive(int uid, String callingPackage);

    @RequiresPermission(allOf = {android.Manifest.permission.PACKAGE_USAGE_STATS, Manifest.permission.INTERACT_ACROSS_PROFILES}, conditional = true)
    int getUidProcessState(int uid, String callingPackage);

    int checkPermission(String permission, int pid, int uid);

    /**
     * Logs start of an API call to associate with an FGS, used for FGS Type Metrics
     */
    void logFgsApiBegin(int apiType, int appUid, int appPid);

    /**
     * Logs stop of an API call to associate with an FGS, used for FGS Type Metrics
     */
    void logFgsApiEnd(int apiType, int appUid, int appPid);

    /**
     * Logs API state change to associate with an FGS, used for FGS Type Metrics
     */
    void logFgsApiStateChanged(int apiType, int state, int appUid, int appPid);
    // =============== End of transactions used on native side as well ============================

    // Special low-level communication with activity manager.
    void handleApplicationCrash(IBinder app,
                                ApplicationErrorReport crashInfo);

    /**
     * @deprecated Use {@link #startActivityWithFeature} instead
     */
    @Deprecated
    int startActivity(IApplicationThread caller, String callingPackage, Intent intent,
                      String resolvedType, IBinder resultTo, String resultWho, int requestCode,
                      int flags, ProfilerInfo profilerInfo, Bundle options);

    int startActivityWithFeature(IApplicationThread caller, String callingPackage,
                                 String callingFeatureId, Intent intent, String resolvedType,
                                 IBinder resultTo, String resultWho, int requestCode, int flags,
                                 ProfilerInfo profilerInfo, Bundle options);

    void unhandledBack();

    boolean finishActivity(IBinder token, int code, Intent data, int finishTask);

    Intent registerReceiver(IApplicationThread caller, String callerPackage,
                            IIntentReceiver receiver, IntentFilter filter,
                            String requiredPermission, int userId, int flags);

    Intent registerReceiverWithFeature(IApplicationThread caller, String callerPackage,
                                       String callingFeatureId, String receiverId, IIntentReceiver receiver,
                                       IntentFilter filter, String requiredPermission, int userId, int flags);

    void unregisterReceiver(IIntentReceiver receiver);

    List<IntentFilter> getRegisteredIntentFilters(IIntentReceiver receiver);

    /**
     * @deprecated Use {@link #broadcastIntentWithFeature} instead
     */
    @Deprecated
    int broadcastIntent(IApplicationThread caller, Intent intent,
                        String resolvedType, IIntentReceiver resultTo, int resultCode,
                        String resultData, Bundle map, String[] requiredPermissions,
                        int appOp, Bundle options, boolean serialized, boolean sticky, int userId);

    int broadcastIntentWithFeature(IApplicationThread caller, String callingFeatureId,
                                   Intent intent, String resolvedType, IIntentReceiver resultTo, int resultCode,
                                   String resultData, Bundle map, String[] requiredPermissions, String[] excludePermissions,
                                   String[] excludePackages, int appOp, Bundle options, boolean serialized, boolean sticky, int userId);

    void unbroadcastIntent(IApplicationThread caller, Intent intent, int userId);

    void finishReceiver(IBinder who, int resultCode, String resultData, Bundle map,
                        boolean abortBroadcast, int flags);

    void attachApplication(IApplicationThread app, long startSeq);

    void finishAttachApplication(long startSeq, long timestampApplicationOnCreateNs);

    List<ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    void moveTaskToFront(IApplicationThread caller, String callingPackage, int task,
                         int flags, Bundle options);

    int getTaskForActivity(IBinder token, boolean onlyRoot);

    ContentProviderHolder getContentProvider(IApplicationThread caller, String callingPackage,
                                             String name, int userId, boolean stable);

    void publishContentProviders(IApplicationThread caller,
                                 List<ContentProviderHolder> providers);

    boolean refContentProvider(IBinder connection, int stableDelta, int unstableDelta);

    PendingIntent getRunningServiceControlPanel(ComponentName service);

    ComponentName startService(IApplicationThread caller, Intent service,
                               String resolvedType, boolean requireForeground, String callingPackage,
                               String callingFeatureId, int userId);

    int stopService(IApplicationThread caller, Intent service,
                    String resolvedType, int userId);
    // Currently keeping old bindService because it is on the greylist

    void publishService(IBinder token, Intent intent, IBinder service);

    void setDebugApp(String packageName, boolean waitForDebugger, boolean persistent);

    void setAgentApp(String packageName, String agent);

    void setAlwaysFinish(boolean enabled);

    void addInstrumentationResults(IApplicationThread target, Bundle results);

    void finishInstrumentation(IApplicationThread target, int resultCode,
                               Bundle results);

    /**
     * Updates mcc mnc configuration and applies changes to the entire system.
     *
     * @param mcc mcc configuration to update.
     * @param mnc mnc configuration to update.
     * @return Returns {@code true} if the configuration was updated;
     * {@code false} otherwise.
     * @throws RemoteException; IllegalArgumentException if mcc or mnc is null.
     */
    boolean updateMccMncConfiguration(String mcc, String mnc);

    boolean stopServiceToken(ComponentName className, IBinder token, int startId);

    int getProcessLimit();

    void setProcessLimit(int max);

    int checkUriPermission(Uri uri, int pid, int uid, int mode, int userId,
                           IBinder callerToken);

    int checkContentUriPermissionFull(Uri uri, int pid, int uid, int mode, int userId);

    int[] checkUriPermissions(List<Uri> uris, int pid, int uid, int mode, int userId,
                              IBinder callerToken);

    void grantUriPermission(IApplicationThread caller, String targetPkg, Uri uri,
                            int mode, int userId);

    void revokeUriPermission(IApplicationThread caller, String targetPkg, Uri uri,
                             int mode, int userId);

    void setActivityController(IActivityController watcher, boolean imAMonkey);

    void showWaitingForDebugger(IApplicationThread who, boolean waiting);

    /*
     * This will deliver the specified signal to all the persistent processes. Currently only
     * SIGUSR1 is delivered. All others are ignored.
     */
    void signalPersistentProcesses(int signal);


    ParceledListSlice getRecentTasks(int maxNum, int flags, int userId);

    void serviceDoneExecuting(IBinder token, int type, int startId, int res,
                              Intent intent);

    /**
     * @deprecated Use {@link #getIntentSenderWithFeature} instead
     */
    @Deprecated
    IIntentSender getIntentSender(int type, String packageName, IBinder token,
                                  String resultWho, int requestCode, Intent[] intents, String[] resolvedTypes,
                                  int flags, Bundle options, int userId);

    IIntentSender getIntentSenderWithFeature(int type, String packageName, String featureId,
                                             IBinder token, String resultWho, int requestCode, Intent[] intents,
                                             String[] resolvedTypes, int flags, Bundle options, int userId);

    void cancelIntentSender(IIntentSender sender);

    void enterSafeMode();

    void noteWakeupAlarm(IIntentSender sender, WorkSource workSource, int sourceUid,
                         String sourcePkg, String tag);

    void removeContentProvider(IBinder connection, boolean stable);

    void setRequestedOrientation(IBinder token, int requestedOrientation);

    void unbindFinished(IBinder token, Intent service);

    void setProcessImportant(IBinder token, int pid, boolean isForeground, String reason);

    void setServiceForeground(ComponentName className, IBinder token,
                              int id, Notification notification, int flags, int foregroundServiceType);

    int getForegroundServiceType(ComponentName className, IBinder token);

    boolean moveActivityTaskToBack(IBinder token, boolean nonRoot);

    void getMemoryInfo(ActivityManager.MemoryInfo outInfo);

    List<ActivityManager.ProcessErrorStateInfo> getProcessesInErrorState();

    boolean clearApplicationUserData(String packageName, boolean keepState,
                                     IPackageDataObserver observer, int userId);

    void stopAppForUser(String packageName, int userId);

    /**
     * Returns {@code false} if the callback could not be registered, {@true} otherwise.
     */
    boolean registerForegroundServiceObserver(IForegroundServiceObserver callback);

    void forceStopPackage(String packageName, int userId);

    void forceStopPackageEvenWhenStopping(String packageName, int userId);

    boolean killPids(int[] pids, String reason, boolean secure);

    List<ActivityManager.RunningServiceInfo> getServices(int maxNum, int flags);
    // Retrieve running application processes in the system

    List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses();

    IBinder peekService(Intent service, String resolvedType, String callingPackage);
    // Turn on/off profiling in a particular process.

    boolean profileControl(String process, int userId, boolean start,
                           ProfilerInfo profilerInfo, int profileType);

    boolean shutdown(int timeout);

    void stopAppSwitches();

    void resumeAppSwitches();

    boolean bindBackupAgent(String packageName, int backupRestoreMode, int targetUserId,
                            int backupDestination, boolean useRestrictedMode);

    void backupAgentCreated(String packageName, IBinder agent, int userId);

    void unbindBackupAgent(ApplicationInfo appInfo);

    int handleIncomingUser(int callingPid, int callingUid, int userId, boolean allowAll,
                           boolean requireFull, String name, String callerPackage);

    void addPackageDependency(String packageName);

    void killApplication(String pkg, int appId, int userId, String reason,
                         int exitInfoReason);

    void closeSystemDialogs(String reason);

    Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids);

    void killApplicationProcess(String processName, int uid);

    void killBackgroundProcesses(String packageName, int userId);

    boolean isUserAMonkey();

    // Retrieve info of applications installed on external media that are currently
    // running.
    List<ApplicationInfo> getRunningExternalApplications();

    void finishHeavyWeightApp();
    // A StrictMode violation to be handled.

    void registerStrictModeCallback(IBinder binder);

    boolean isTopActivityImmersive();

    void crashApplicationWithType(int uid, int initialPid, String packageName, int userId,
                                  String message, boolean force, int exceptionTypeId);

    void crashApplicationWithTypeWithExtras(int uid, int initialPid, String packageName,
                                            int userId, String message, boolean force, int exceptionTypeId, Bundle extras);

    boolean isUserRunning(int userid, int flags);

    void setPackageScreenCompatMode(String packageName, int mode);

    boolean switchUser(int userid);

    String getSwitchingFromUserMessage();

    String getSwitchingToUserMessage();

    void setStopUserOnSwitch(int value);

    boolean removeTask(int taskId);

    void registerProcessObserver(IProcessObserver observer);

    void unregisterProcessObserver(IProcessObserver observer);

    boolean isIntentSenderTargetedToPackage(IIntentSender sender);

    long[] getProcessPss(int[] pids);

    void showBootMessage(CharSequence msg, boolean always);

    void killAllBackgroundProcesses();

    ContentProviderHolder getContentProviderExternal(String name, int userId,
                                                     IBinder token, String tag);

    /**
     * @deprecated - Use {@link #removeContentProviderExternalAsUser} which takes a user ID.
     */
    @Deprecated
    void removeContentProviderExternal(String name, IBinder token);

    void removeContentProviderExternalAsUser(String name, IBinder token, int userId);

    // Get memory information about the calling process.
    void getMyMemoryState(ActivityManager.RunningAppProcessInfo outInfo);

    boolean killProcessesBelowForeground(String reason);

    int getCurrentUserId();
    // This is not public because you need to be very careful in how you
    // manage your activity to make sure it is always the uid you expect.

    int getLaunchedFromUid(IBinder activityToken);

    void unstableProviderDied(IBinder connection);

    boolean isIntentSenderAnActivity(IIntentSender sender);

    /**
     * @deprecated Use {startActivityAsUserWithFeature} instead
     */
    @Deprecated
    int startActivityAsUser(IApplicationThread caller, String callingPackage,
                            Intent intent, String resolvedType, IBinder resultTo, String resultWho,
                            int requestCode, int flags, ProfilerInfo profilerInfo,
                            Bundle options, int userId);

    int startActivityAsUserWithFeature(IApplicationThread caller, String callingPackage,
                                       String callingFeatureId, Intent intent, String resolvedType,
                                       IBinder resultTo, String resultWho, int requestCode, int flags,
                                       ProfilerInfo profilerInfo, Bundle options, int userId);

    int[] getRunningUserIds();

    // Request a heap dump for the system server.
    void requestSystemServerHeapDump();

    void requestBugReport(int bugreportType);

    void requestBugReportWithDescription(String shareTitle,
                                         String shareDescription, int bugreportType);

    /**
     * Takes a telephony bug report and notifies the user with the title and description
     * that are passed to this API as parameters
     *
     * @param shareTitle       should be a valid legible string less than 50 chars long
     * @param shareDescription should be less than 150 chars long
     * @throws IllegalArgumentException if shareTitle or shareDescription is too big or if the
     *                                  paremeters cannot be encoding to an UTF-8 charset.
     */
    void requestTelephonyBugReport(String shareTitle, String shareDescription);

    /**
     * This method is only used by Wifi.
     * <p>
     * Takes a minimal bugreport of Wifi-related state.
     *
     * @param shareTitle       should be a valid legible string less than 50 chars long
     * @param shareDescription should be less than 150 chars long
     * @throws IllegalArgumentException if shareTitle or shareDescription is too big or if the
     *                                  parameters cannot be encoding to an UTF-8 charset.
     */
    void requestWifiBugReport(String shareTitle, String shareDescription);

    void requestInteractiveBugReportWithDescription(String shareTitle,
                                                    String shareDescription);

    void requestInteractiveBugReport();

    void requestBugReportWithExtraAttachments(List<Uri> extraAttachment);

    void requestFullBugReport();

    void requestRemoteBugReport(long nonce);

    boolean launchBugReportHandlerApp();

    List<String> getBugreportWhitelistedPackages();


    Intent getIntentForIntentSender(IIntentSender sender);
    // This is not public because you need to be very careful in how you
    // manage your activity to make sure it is always the uid you expect.

    String getLaunchedFromPackage(IBinder activityToken);

    void killUid(int appId, int userId, String reason);

    void setUserIsMonkey(boolean monkey);

    void hang(IBinder who, boolean allowRestart);

    List<ActivityTaskManager.RootTaskInfo> getAllRootTaskInfos();

    void moveTaskToRootTask(int taskId, int rootTaskId, boolean toTop);

    void setFocusedRootTask(int taskId);

    ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo();

    void restart();

    void performIdleMaintenance();

    void appNotRespondingViaProvider(IBinder connection);

    Rect getTaskBounds(int taskId);

    boolean setProcessMemoryTrimLevel(String process, int userId, int level);


    // Start of L transactions
    String getTagForIntentSender(IIntentSender sender, String prefix);

    /**
     * Starts a user in the background (i.e., while another user is running in the foreground).
     * <p>
     * Notice that a background user is "invisible" and cannot launch activities. Starting on
     * Android U, all users started with this method are invisible, even profiles (prior to Android
     * U, profiles started with this method would be visible if its parent was the current user) -
     * if you want to start a profile visible, you should call {@code startProfile()} instead.
     */

    boolean startUserInBackground(int userid);


    boolean isInLockTaskMode();

    int startActivityFromRecents(int taskId, Bundle options);

    void startSystemLockTaskMode(int taskId);

    boolean isTopOfTask(IBinder token);

    void bootAnimationComplete();

    /**
     * Used by { com.android.systemui.theme.ThemeOverlayController} to notify when color
     * palette is ready.
     *
     * @param userId The ID of the user where ThemeOverlayController is ready.
     * @throws RemoteException
     */
    void setThemeOverlayReady(int userId);


    void registerTaskStackListener(ITaskStackListener listener);

    void unregisterTaskStackListener(ITaskStackListener listener);

    void notifyCleartextNetwork(int uid, byte[] firstPacket);

    void setTaskResizeable(int taskId, int resizeableMode);

    void resizeTask(int taskId, Rect bounds, int resizeMode);

    int getLockTaskModeState();

    void setDumpHeapDebugLimit(String processName, int uid, long maxMemSize,
                               String reportPackage);

    void dumpHeapFinished(String path);

    void updateLockTaskPackages(int userId, String[] packages);

    void noteAlarmStart(IIntentSender sender, WorkSource workSource, int sourceUid, String tag);

    void noteAlarmFinish(IIntentSender sender, WorkSource workSource, int sourceUid, String tag);

    int getPackageProcessState(String packageName, String callingPackage);

    // Start of N transactions
    // Start Binder transaction tracking for all applications.

    boolean startBinderTracking();
    // Stop Binder transaction tracking for all applications and dump trace data to the given file
    // descriptor.

    boolean stopBinderTrackingAndDump(ParcelFileDescriptor fd);


    void suppressResizeConfigChanges(boolean suppress);

    /**
     * @deprecated Use {@link #unlockUser2(int, IProgressListener)} instead, since the token and
     * secret arguments no longer do anything.  This method still exists only because it is marked
     * with {@code
     * signature.
     */
    @Deprecated
    boolean unlockUser(int userid, byte[] token, byte[] secret,
                       IProgressListener listener);

    /**
     * Tries to unlock the given user.
     * <p>
     * This will succeed only if the user's CE storage key is already unlocked or if the user
     * doesn't have a lockscreen credential set.
     *
     * @param userId   The ID of the user to unlock.
     * @param listener An optional progress listener.
     * @return true if the user was successfully unlocked, otherwise false.
     */
    boolean unlockUser2(int userId, IProgressListener listener);

    void killPackageDependents(String packageName, int userId);

    void makePackageIdle(String packageName, int userId);

    void setDeterministicUidIdle(boolean deterministic);

    int getMemoryTrimLevel();

    boolean isVrModePackageEnabled(ComponentName packageName);

    void notifyLockedProfile(int userId);

    void startConfirmDeviceCredentialIntent(Intent intent, Bundle options);

    void sendIdleJobTrigger();

    int sendIntentSender(IApplicationThread caller, IIntentSender target,
                         IBinder whitelistToken, int code,
                         Intent intent, String resolvedType, IIntentReceiver finishedReceiver,
                         String requiredPermission, Bundle options);

    boolean isBackgroundRestricted(String packageName);

    // Start of N MR1 transactions
    void setRenderThread(int tid);

    /**
     * Lets activity manager know whether the calling process is currently showing "top-level" UI
     * that is not an activity, i.e. windows on the screen the user is currently interacting with.
     *
     * <p>This flag can only be set for persistent processes.
     *
     * @param hasTopUi Whether the calling process has "top-level" UI.
     */
    void setHasTopUi(boolean hasTopUi);

    // Start of O transactions

    /**
     * Cancels the window transitions for the given task.
     */

    void cancelTaskWindowTransition(int taskId);

    void scheduleApplicationInfoChanged(List<String> packageNames, int userId);

    void setPersistentVrThread(int tid);

    void waitForNetworkStateUpdate(long procStateSeq);

    /**
     * Add a bare uid to the background restrictions whitelist.  Only the system uid may call this.
     */
    void backgroundAllowlistUid(int uid);

    // Start of P transactions

    /**
     * Similar to {@link #startUserInBackground(int userId), but with a listener to report
     * user unlock progress.
     */
    boolean startUserInBackgroundWithListener(int userid, IProgressListener unlockProgressListener);

    /**
     * Method for the shell UID to start deletating its permission identity to an
     * active instrumenation. The shell can delegate permissions only to one active
     * instrumentation at a time. An active instrumentation is one running and
     * started from the shell.
     */
    void startDelegateShellPermissionIdentity(int uid, String[] permissions);

    /**
     * Method for the shell UID to stop deletating its permission identity to an
     * active instrumenation. An active instrumentation is one running and
     * started from the shell.
     */
    void stopDelegateShellPermissionIdentity();

    /**
     * Method for the shell UID to get currently adopted permissions for an active instrumentation.
     * An active instrumentation is one running and started from the shell.
     */
    List<String> getDelegatedShellPermissions();

    /**
     * Returns a file descriptor that'll be closed when the system server process dies.
     */
    ParcelFileDescriptor getLifeMonitor();

    /**
     * Start user, if it us not already running, and bring it to foreground.
     * unlockProgressListener can be null if monitoring progress is not necessary.
     */
    boolean startUserInForegroundWithListener(int userid, IProgressListener unlockProgressListener);

    /**
     * Method for the app to tell system that it's wedged and would like to trigger an ANR.
     */
    void appNotResponding(String reason);

    /**
     * Return a list of {@link ApplicationStartInfo} records.
     *
     * <p class="note"> Note: System stores historical information in a ring buffer, older
     * records would be overwritten by newer records. </p>
     *
     * @param packageName Optional, an empty value means match all packages belonging to the
     *                    caller's UID. If this package belongs to another UID, you must hold
     *                    {@link android.Manifest.permission#DUMP} in order to retrieve it.
     * @param maxNum      Optional, the maximum number of results should be returned; A value of 0
     *                    means to ignore this parameter and return all matching records
     * @param userId      The userId in the multi-user environment.
     * @return a list of {@link ApplicationStartInfo} records with the matching criteria, sorted in
     * the order from most recent to least recent.
     */
    ParceledListSlice<ApplicationStartInfo> getHistoricalProcessStartReasons(String packageName,
                                                                             int maxNum, int userId);


    /**
     * Sets a callback for {@link ApplicationStartInfo} upon completion of collecting startup data.
     *
     * <p class="note"> Note: completion of startup is no guaranteed and as such this callback may not occur.</p>
     *
     * @param listener A listener to for the callback upon completion of startup data collection.
     * @param userId   The userId in the multi-user environment.
     */
    void addApplicationStartInfoCompleteListener(IApplicationStartInfoCompleteListener listener,
                                                 int userId);


    /**
     * Removes callback for {@link ApplicationStartInfo} upon completion of collecting startup data.
     *
     * @param userId The userId in the multi-user environment.
     */
    void removeApplicationStartInfoCompleteListener(IApplicationStartInfoCompleteListener listener,
                                                    int userId);


    /**
     * Adds a timestamp of the moment called to the calling apps most recent
     * {@link ApplicationStartInfo}.
     *
     * @param key         Unique key for timestamp.
     * @param timestampNs Clock monotonic time in nanoseconds of event to be
     *                    recorded.
     * @param userId      The userId in the multi-user environment.
     */
    void addStartInfoTimestamp(int key, long timestampNs, int userId);

    /**
     * Reports view related timestamps to be added to the calling apps most
     * recent {@link ApplicationStartInfo}.
     *
     * @param renderThreadDrawStartTimeNs Clock monotonic time in nanoseconds of RenderThread draw start
     * @param framePresentedTimeNs        Clock monotonic time in nanoseconds of frame presented
     */
    void reportStartInfoViewTimestamps(long renderThreadDrawStartTimeNs, long framePresentedTimeNs);

    /**
     * Return a list of {@link ApplicationExitInfo} records.
     *
     * <p class="note"> Note: System stores these historical information in a ring buffer, older
     * records would be overwritten by newer records. </p>
     *
     * <p class="note"> Note: In the case that this application bound to an external service with
     * flag {@link android.content.Context#BIND_EXTERNAL_SERVICE}, the process of that external
     * service will be included in this package's exit info. </p>
     *
     * @param packageName Optional, an empty value means match all packages belonging to the
     *                    caller's UID. If this package belongs to another UID, you must hold
     *                    {@link android.Manifest.permission#DUMP} in order to retrieve it.
     * @param pid         Optional, it could be a process ID that used to belong to this package but
     *                    died later; A value of 0 means to ignore this parameter and return all
     *                    matching records.
     * @param maxNum      Optional, the maximum number of results should be returned; A value of 0
     *                    means to ignore this parameter and return all matching records
     * @param userId      The userId in the multi-user environment.
     * @return a list of {@link ApplicationExitInfo} records with the matching criteria, sorted in
     * the order from most recent to least recent.
     */
    ParceledListSlice<ApplicationExitInfo> getHistoricalProcessExitReasons(String packageName,
                                                                           int pid, int maxNum, int userId);

    /*
     * Kill the given PIDs, but the killing will be delayed until the device is idle
     * and the given process is imperceptible.
     */
    void killProcessesWhenImperceptible(int[] pids, String reason);

    /**
     * Set locus context for a given activity.
     *
     * @param activity
     * @param locusId  a unique, stable id that identifies this activity instance from others.
     * @param appToken ActivityRecord's appToken.
     */
    void setActivityLocusContext(ComponentName activity, LocusId locusId,
                                 IBinder appToken);

    /**
     * Set custom state data for this process. It will be included in the record of
     * {@link ApplicationExitInfo} on the death of the current calling process; the new process
     * of the app can retrieve this state data by calling
     * {@link ApplicationExitInfo#getProcessStateSummary} on the record returned by
     * {@link #getHistoricalProcessExitReasons}.
     *
     * <p> This would be useful for the calling app to save its stateful data: if it's
     * killed later for any reason, the new process of the app can know what the
     * previous process of the app was doing. For instance, you could use this to encode
     * the current level in a game, or a set of features/experiments that were enabled. Later you
     * could analyze under what circumstances the app tends to crash or use too much memory.
     * However, it's not suggested to rely on this to restore the applications previous UI state
     * or so, it's only meant for analyzing application healthy status.</p>
     *
     * <p> System might decide to throttle the calls to this API; so call this API in a reasonable
     * manner, excessive calls to this API could result a {@link java.lang.RuntimeException}.
     * </p>
     *
     * @param state The customized state data
     */
    void setProcessStateSummary(byte[] state);

    /**
     * Return whether the app freezer is supported (true) or not (false) by this system.
     */
    boolean isAppFreezerSupported();

    /**
     * Return whether the app freezer is enabled (true) or not (false) by this system.
     */
    boolean isAppFreezerEnabled();

    /**
     * Kills uid with the reason of permission change.
     */
    void killUidForPermissionChange(int appId, int userId, String reason);

    /**
     * Resets the state of the { com.android.server.am.AppErrors} instance.
     * This is intended for testing within the CTS only and is protected by
     * android.permission.RESET_APP_ERRORS.
     */
    void resetAppErrors();

    /**
     * Control the app freezer state. Returns true in case of success, false if the operation
     * didn't succeed (for example, when the app freezer isn't supported).
     * Handling the freezer state via this method is reentrant, that is it can be
     * disabled and re-enabled multiple times in parallel. As long as there's a 1:1 disable to
     * enable match, the freezer is re-enabled at last enable only.
     *
     * @param enable set it to true to enable the app freezer, false to disable it.
     */
    boolean enableAppFreezer(boolean enable);

    /**
     * Suppress or reenable the rate limit on foreground service notification deferral.
     * This is for use within CTS and is protected by android.permission.WRITE_DEVICE_CONFIG
     * and WRITE_ALLOWLISTED_DEVICE_CONFIG.
     *
     * @param enable false to suppress rate-limit policy; true to reenable it.
     */
    boolean enableFgsNotificationRateLimit(boolean enable);

    /**
     * Holds the AM lock for the specified amount of milliseconds.
     * This is intended for use by the tests that need to imitate lock contention.
     * The token should be obtained by
     * { @android.content.pm.PackageManager#getHoldLockToken()}.
     */
    void holdLock(IBinder token, int durationMs);

    /**
     * Starts a profile.
     *
     * @param userId the user id of the profile.
     * @return true if the profile has been successfully started or if the profile is already
     * running, false if profile failed to start.
     * @throws IllegalArgumentException if the user is not a profile.
     */
    boolean startProfile(int userId);

    /**
     * Stops a profile.
     *
     * @param userId the user id of the profile.
     * @return true if the profile has been successfully stopped or is already stopped. Otherwise
     * the exceptions listed below are thrown.
     * @throws IllegalArgumentException if the user is not a profile.
     */
    boolean stopProfile(int userId);

    /**
     * Called by PendingIntent.queryIntentComponents()
     */
    ParceledListSlice queryIntentComponentsForIntentSender(IIntentSender sender, int matchFlags);

    @RequiresPermission(allOf = {android.Manifest.permission.PACKAGE_USAGE_STATS}, conditional = true)
    int getUidProcessCapabilities(int uid, String callingPackage);

    /**
     * Blocks until all broadcast queues become idle.
     */
    void waitForBroadcastIdle();

    void waitForBroadcastBarrier();

    /**
     * Delays delivering broadcasts to the specified package.
     */
    @RequiresPermission(android.Manifest.permission.DUMP)
    void forceDelayBroadcastDelivery(String targetPackage, long delayedDurationMs);

    /**
     * Checks if the process represented by the given pid is frozen.
     */
    @RequiresPermission(android.Manifest.permission.DUMP)
    boolean isProcessFrozen(int pid);

    /**
     * @return The reason code of whether or not the given UID should be exempted from background
     * restrictions here.
     *
     * <p>
     * Note: Call it with caution as it'll try to acquire locks in other services.
     * </p>
     */
    int getBackgroundRestrictionExemptionReason(int uid);

    // Start (?) of T transactions

    /**
     * Similar to {@link #startUserInBackgroundWithListener(int userId, IProgressListener unlockProgressListener)},
     * but setting the user as the visible user of that display (i.e., allowing the user and its
     * running profiles to launch activities on that display).
     *
     * <p>Typically used only by automotive builds when the vehicle has multiple displays.
     */
    boolean startUserInBackgroundVisibleOnDisplay(int userid, int displayId, IProgressListener unlockProgressListener);

    /**
     * Similar to {@link #startProfile(int userId)}, but with a listener to report user unlock
     * progress.
     */
    boolean startProfileWithListener(int userid, IProgressListener unlockProgressListener);

    int restartUserInBackground(int userId, int userStartMode);

    /**
     * Gets the ids of displays that can be used on startUserInBackgroundVisibleOnDisplay(int userId, int displayId).
     *
     * <p>Typically used only by automotive builds when the vehicle has multiple displays.
     */
    @Nullable
    int[] getDisplayIdsForStartingVisibleBackgroundUsers();

    /**
     * Returns if the service is a short-service is still "alive" and past the timeout.
     */
    boolean shouldServiceTimeOut(ComponentName className, IBinder token);

    /**
     * Returns if the service has a time-limit restricted type and is past the time limit.
     */
    boolean hasServiceTimeLimitExceeded(ComponentName className, IBinder token);

    void registerUidFrozenStateChangedCallback(IUidFrozenStateChangedCallback callback);

    @RequiresPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    void unregisterUidFrozenStateChangedCallback(IUidFrozenStateChangedCallback callback);

    @RequiresPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
    int[] getUidFrozenState(int[] uids);

    int checkPermissionForDevice(String permission, int pid, int uid, int deviceId);

    /**
     * Notify AMS about binder transactions to frozen apps.
     *
     * @param debugPid The binder transaction sender
     * @param code     The binder transaction code
     * @param flags    The binder transaction flags
     * @param err      The binder transaction error
     */
    void frozenBinderTransactionDetected(int debugPid, int code, int flags, int err);

    int getBindingUidProcessState(int uid, String callingPackage);

    /**
     * Return the timestampe (the elapsed timebase) when the UID became idle from active
     * last time (regardless of if the UID is still idle, or became active again).
     * This is useful when trying to detect whether an UID has ever became idle since a certain
     * time in the past.
     */
    long getUidLastIdleElapsedTime(int uid, String callingPackage);

    /**
     * Adds permission to be overridden to the given state. Must be called from root user.
     *
     * @param originatingUid The UID of the instrumented app that initialized the override
     * @param uid            The UID of the app whose permission will be overridden
     * @param permission     The permission whose state will be overridden
     * @param result         The state to override the permission to
     */
    void addOverridePermissionState(int originatingUid, int uid, String permission, int result);

    /**
     * Removes overridden permission. Must be called from root user.
     *
     * @param originatingUid The UID of the instrumented app that initialized the override
     * @param uid            The UID of the app whose permission is overridden
     * @param permission     The permission whose state will no longer be overridden
     */
    void removeOverridePermissionState(int originatingUid, int uid, String permission);

    /**
     * Clears all overridden permissions for the given UID. Must be called from root user.
     *
     * @param originatingUid The UID of the instrumented app that initialized the override
     * @param uid            The UID of the app whose permissions will no longer be overridden
     */
    void clearOverridePermissionStates(int originatingUid, int uid);

    /**
     * Clears all overridden permissions on the device. Must be called from root user.
     *
     * @param originatingUid The UID of the instrumented app that initialized the override
     */
    void clearAllOverridePermissionStates(int originatingUid);

    /**
     * Request the system to log the reason for restricting / unrestricting an app.
     *
     * @see ActivityManager
     */
    void noteAppRestrictionEnabled(String packageName, int uid, int restrictionType,
                                   boolean enabled, int reason, String subReason, int source, long threshold);

    /**
     * Creates and returns a new IntentCreatorToken that keeps the creatorUid and refreshes key
     * fields of the intent passed in.
     *
     * @param intent The intent with key fields out of sync of the IntentCreatorToken it contains.
     * @hide
     */
    IBinder refreshIntentCreatorToken(Intent intent);

    abstract class Stub extends Binder implements IActivityManager {

        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("STUB");
        }
    }
}
