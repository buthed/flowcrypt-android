/*
 * © 2016-2018 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jobscheduler;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;
import android.util.LongSparseArray;

import com.flowcrypt.email.api.email.EmailUtil;
import com.flowcrypt.email.api.email.Folder;
import com.flowcrypt.email.api.email.FoldersManager;
import com.flowcrypt.email.api.email.model.GeneralMessageDetails;
import com.flowcrypt.email.api.email.protocol.OpenStoreHelper;
import com.flowcrypt.email.api.email.sync.SyncListener;
import com.flowcrypt.email.api.email.sync.tasks.SyncFolderSyncTask;
import com.flowcrypt.email.database.dao.source.AccountDao;
import com.flowcrypt.email.database.dao.source.AccountDaoSource;
import com.flowcrypt.email.database.dao.source.imap.MessageDaoSource;
import com.flowcrypt.email.service.MessagesNotificationManager;
import com.flowcrypt.email.util.GeneralUtil;
import com.flowcrypt.email.util.exception.ExceptionUtil;
import com.sun.mail.imap.IMAPFolder;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

/**
 * This is an implementation of {@link JobService}. Here we are going to do syncing INBOX folder of an active account.
 *
 * @author Denis Bondarenko
 * Date: 20.06.2018
 * Time: 12:40
 * E-mail: DenBond7@gmail.com
 */
public class SyncJobService extends JobService implements SyncListener {
  private static final long INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(15);
  private static final String TAG = SyncJobService.class.getSimpleName();
  private MessagesNotificationManager messagesNotificationManager;

  public static void schedule(Context context) {
    ComponentName serviceName = new ComponentName(context, SyncJobService.class);
    JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(JobIdManager.JOB_TYPE_SYNC, serviceName)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setPeriodic(INTERVAL_MILLIS)
        .setPersisted(true);

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      jobInfoBuilder.setRequiresBatteryNotLow(true);
    }

    JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    if (scheduler != null) {
      int result = scheduler.schedule(jobInfoBuilder.build());
      if (result == JobScheduler.RESULT_SUCCESS) {
        Log.d(TAG, "A job scheduled successfully");
      } else {
        String errorMessage = "Error. Can't schedule a job";
        Log.e(TAG, errorMessage);
        ExceptionUtil.handleError(new IllegalStateException(errorMessage));
      }
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "onCreate");
    this.messagesNotificationManager = new MessagesNotificationManager(this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "onDestroy");
  }

  @Override
  public boolean onStartJob(JobParameters jobParameters) {
    Log.d(TAG, "onStartJob");
    new CheckNewMessagesJobTask(this).execute(jobParameters);
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters jobParameters) {
    Log.d(TAG, "onStopJob");
    jobFinished(jobParameters, true);
    return false;
  }

  @Override
  public Context getContext() {
    return getApplicationContext();
  }

  @Override
  public void onMessageWithBackupToKeyOwnerSent(AccountDao account, String ownerKey, int requestCode,
                                                boolean isSent) {

  }

  @Override
  public void onPrivateKeyFound(AccountDao account, List<String> keys, String ownerKey, int requestCode) {

  }

  @Override
  public void onMessageSent(AccountDao account, String ownerKey, int requestCode, boolean isSent) {

  }

  @Override
  public void onMessagesMoved(AccountDao account, IMAPFolder sourceImapFolder, IMAPFolder destinationImapFolder,
                              Message[] messages, String ownerKey, int requestCode) {

  }

  @Override
  public void onMessageMoved(AccountDao account, IMAPFolder sourceImapFolder, IMAPFolder destinationImapFolder,
                             Message message, String ownerKey, int requestCode) {

  }

  @Override
  public void onMessageDetailsReceived(AccountDao account, Folder localFolder, IMAPFolder imapFolder, long uid,
                                       Message message, String rawMessageWithOutAttachments, String ownerKey,
                                       int requestCode) {

  }

  @Override
  public void onMessagesReceived(AccountDao account, Folder localFolder, IMAPFolder remoteFolder, Message[]
      messages, String ownerKey, int requestCode) {

  }

  @Override
  public void onSearchMessagesReceived(AccountDao account, Folder folder, IMAPFolder remoteFolder,
                                       Message[] messages, String ownerKey, int requestCode) {

  }

  @Override
  public void onRefreshMessagesReceived(AccountDao account, com.flowcrypt.email.api.email.Folder localFolder,
                                        IMAPFolder remoteFolder, Message[] newMessages,
                                        Message[] updateMessages, String ownerKey, int requestCode) {
    try {
      MessageDaoSource messageDaoSource = new MessageDaoSource();

      Map<Long, String> messagesUIDWithFlagsInLocalDatabase = messageDaoSource.getMapOfUIDAndMessagesFlags
          (getApplicationContext(), account.getEmail(), localFolder.getFolderAlias());

      Collection<Long> messagesUIDsInLocalDatabase = new HashSet<>(messagesUIDWithFlagsInLocalDatabase.keySet());

      Collection<Long> deleteCandidatesUIDList = EmailUtil.generateDeleteCandidates(messagesUIDsInLocalDatabase,
          remoteFolder, updateMessages);

      String folderAlias = localFolder.getFolderAlias();
      List<GeneralMessageDetails> generalMessageDetailsBeforeUpdate = messageDaoSource.getNewMessages
          (getApplicationContext(), account.getEmail(), folderAlias);

      messageDaoSource.deleteMessagesByUID(getApplicationContext(),
          account.getEmail(), localFolder.getFolderAlias(), deleteCandidatesUIDList);

      messageDaoSource.updateMessagesByUID(getApplicationContext(),
          account.getEmail(),
          localFolder.getFolderAlias(),
          remoteFolder,
          EmailUtil.generateUpdateCandidates(messagesUIDWithFlagsInLocalDatabase,
              remoteFolder, updateMessages));

      List<GeneralMessageDetails> generalMessageDetailsAfterUpdate = messageDaoSource.getNewMessages
          (getApplicationContext(), account.getEmail(), folderAlias);

      List<GeneralMessageDetails> generalMessageDetailsDeleteCandidate =
          new LinkedList<>(generalMessageDetailsBeforeUpdate);
      generalMessageDetailsDeleteCandidate.removeAll(generalMessageDetailsAfterUpdate);

      if (!GeneralUtil.isAppForegrounded() &&
          FoldersManager.getFolderTypeForImapFolder(localFolder) == FoldersManager.FolderType.INBOX) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          for (GeneralMessageDetails generalMessageDetails : generalMessageDetailsDeleteCandidate) {
            messagesNotificationManager.cancel(this, generalMessageDetails.getUid());
          }
        } else {
          if (!generalMessageDetailsDeleteCandidate.isEmpty()) {
            messagesNotificationManager.notify(this, account, localFolder,
                generalMessageDetailsAfterUpdate, messageDaoSource.getUIDOfUnseenMessages(this,
                    account.getEmail(), folderAlias), true);
          }
        }
      }
    } catch (RemoteException | MessagingException | OperationApplicationException e) {
      e.printStackTrace();
      ExceptionUtil.handleError(e);
    }
  }

  @Override
  public void onFolderInfoReceived(AccountDao account, javax.mail.Folder[] folders, String ownerKey,
                                   int requestCode) {

  }

  @Override
  public void onError(AccountDao account, int errorType, Exception e, String ownerKey, int requestCode) {

  }

  @Override
  public void onActionProgress(AccountDao account, String ownerKey, int requestCode, int resultCode) {

  }

  @Override
  public void onMessageChanged(AccountDao account, Folder localFolder, IMAPFolder remoteFolder, Message message,
                               String ownerKey, int requestCode) {

  }

  @Override
  public void onIdentificationToEncryptionCompleted(AccountDao account, Folder localFolder, IMAPFolder
      remoteFolder, String ownerKey, int requestCode) {

  }

  @Override
  public void onNewMessagesReceived(final AccountDao account, Folder localFolder, IMAPFolder remoteFolder,
                                    Message[] newMessages, LongSparseArray<Boolean> isMessageEncryptedInfo, String
                                        ownerKey, int requestCode) {
    try {
      boolean isShowOnlyEncryptedMessages =
          new AccountDaoSource().isShowOnlyEncryptedMessages(getApplicationContext(), account.getEmail());

      MessageDaoSource messageDaoSource = new MessageDaoSource();

      Map<Long, String> messagesUIDWithFlagsInLocalDatabase = messageDaoSource.getMapOfUIDAndMessagesFlags
          (getApplicationContext(), account.getEmail(), localFolder.getFolderAlias());

      Collection<Long> messagesUIDsInLocalDatabase = new HashSet<>(messagesUIDWithFlagsInLocalDatabase.keySet());

      javax.mail.Message[] messagesNewCandidates = EmailUtil.generateNewCandidates(messagesUIDsInLocalDatabase,
          remoteFolder, newMessages);

      messageDaoSource.addRows(getApplicationContext(),
          account.getEmail(),
          localFolder.getFolderAlias(),
          remoteFolder,
          messagesNewCandidates,
          isMessageEncryptedInfo,
          !GeneralUtil.isAppForegrounded(), isShowOnlyEncryptedMessages);

      if (!GeneralUtil.isAppForegrounded()) {
        String folderAlias = localFolder.getFolderAlias();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && messagesNewCandidates.length == 0) {
          return;
        }

        messagesNotificationManager.notify(this, account, localFolder,
            messageDaoSource.getNewMessages(getApplicationContext(), account.getEmail(), folderAlias),
            messageDaoSource.getUIDOfUnseenMessages(this, account.getEmail(), folderAlias), false);
      }
    } catch (MessagingException e) {
      e.printStackTrace();
      ExceptionUtil.handleError(e);
    }
  }

  /**
   * This is a worker. Here we will do sync in the background thread. If the sync will be failed we'll schedule it
   * again.
   */
  private static class CheckNewMessagesJobTask extends AsyncTask<JobParameters, Boolean, JobParameters> {
    private final WeakReference<SyncJobService> syncJobServiceWeakReference;

    private Session session;
    private Store store;
    private boolean isFailed;

    CheckNewMessagesJobTask(SyncJobService syncJobService) {
      this.syncJobServiceWeakReference = new WeakReference<>(syncJobService);
    }

    @Override
    protected JobParameters doInBackground(JobParameters... params) {
      Log.d(TAG, "doInBackground");

      try {
        if (syncJobServiceWeakReference.get() != null) {
          Context context = syncJobServiceWeakReference.get().getApplicationContext();
          AccountDao account = new AccountDaoSource().getActiveAccountInformation(context);

          if (account != null) {
            FoldersManager foldersManager = FoldersManager.fromDatabase(context, account.getEmail());
            Folder localFolder = foldersManager.findInboxFolder();

            if (localFolder != null) {
              session = OpenStoreHelper.getSessionForAccountDao(context, account);
              store = OpenStoreHelper.openAndConnectToStore(context, account, session);

              new SyncFolderSyncTask("", 0, localFolder)
                  .runIMAPAction(account, session, store, syncJobServiceWeakReference.get());

              if (store != null) {
                store.close();
              }
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
        publishProgress(true);
      }

      publishProgress(false);
      return params[0];
    }

    @Override
    protected void onPostExecute(JobParameters jobParameters) {
      Log.d(TAG, "onPostExecute");
      try {
        if (syncJobServiceWeakReference.get() != null) {
          syncJobServiceWeakReference.get().jobFinished(jobParameters, isFailed);
        }
      } catch (NullPointerException e) {
        e.printStackTrace();
      }
    }

    @Override
    protected void onProgressUpdate(Boolean... values) {
      super.onProgressUpdate(values);
      isFailed = values[0];
    }
  }
}
