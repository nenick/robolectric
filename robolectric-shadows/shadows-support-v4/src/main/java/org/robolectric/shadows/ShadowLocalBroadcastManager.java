package org.robolectric.shadows;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Implements(LocalBroadcastManager.class)
public class ShadowLocalBroadcastManager {

  final List<Intent> sentBroadcastIntents = new ArrayList<Intent>();
  final List<Wrapper> registeredReceivers = new ArrayList<Wrapper>();

  @Implementation
  public static LocalBroadcastManager getInstance(final Context context) {
    return shadowOf(context).getShadowApplication().getSingleton(LocalBroadcastManager.class, new Provider<LocalBroadcastManager>() {
      @Override
      public LocalBroadcastManager get() {
        return ReflectionHelpers.callConstructor(LocalBroadcastManager.class, new ReflectionHelpers.ClassParameter(Context.class, context));
      }
    });
  }

  private static ShadowContext shadowOf(Context context) {
    return (ShadowContext) ShadowExtractor.extract(context);
  }

  @Implementation
  public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    registeredReceivers.add(new Wrapper(receiver, filter));
  }

  @Implementation
  public void unregisterReceiver(BroadcastReceiver receiver) {
    Iterator<Wrapper> iterator = registeredReceivers.iterator();
    while (iterator.hasNext()) {
      Wrapper wrapper = iterator.next();
      if (wrapper.broadcastReceiver == receiver) {
        iterator.remove();
      }
    }
  }

  @Implementation
  public boolean sendBroadcast(Intent intent) {
    boolean sent = false;
    sentBroadcastIntents.add(intent);
    List<Wrapper> copy = new ArrayList<Wrapper>();
    copy.addAll(registeredReceivers);
    for (Wrapper wrapper : copy) {
      if (wrapper.intentFilter.matchAction(intent.getAction())) {
        final int match = wrapper.intentFilter.matchData(intent.getType(), intent.getScheme(), intent.getData());
        if (match != IntentFilter.NO_MATCH_DATA && match != IntentFilter.NO_MATCH_TYPE) {
          sent = true;
          final BroadcastReceiver receiver = wrapper.broadcastReceiver;
          final Intent broadcastIntent = intent;
          ShadowLooper.getUiThreadScheduler().post(new Runnable() {
            @Override
            public void run() {
              receiver.onReceive(RuntimeEnvironment.application, broadcastIntent);
            }
          });
        }
      }
    }
    return sent;
  }

  public List<Intent> getSentBroadcastIntents() {
    return sentBroadcastIntents;
  }

  public List<Wrapper> getRegisteredBroadcastReceivers() {
    return registeredReceivers;
  }

  public static class Wrapper {
    public final BroadcastReceiver broadcastReceiver;
    public final IntentFilter intentFilter;

    public Wrapper(BroadcastReceiver broadcastReceiver, IntentFilter intentFilter) {
      this.broadcastReceiver = broadcastReceiver;
      this.intentFilter = intentFilter;
    }
  }
}
