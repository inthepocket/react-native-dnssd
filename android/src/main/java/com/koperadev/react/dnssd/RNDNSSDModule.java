
package com.koperadev.react.dnssd;

import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import javax.annotation.Nullable;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.xiao.rxbonjour.RxBonjour;
import com.xiao.rxbonjour.model.NetworkServiceDiscoveryInfo;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class RNDNSSDModule extends ReactContextBaseJavaModule {

  private final ArrayList<Disposable> searches;

  static final String TAG = "RNDNSSD";

  public RNDNSSDModule(ReactApplicationContext reactContext) {
    super(reactContext);

    searches = new ArrayList<>();
  }

  @Override
  public void onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy();
    stopSearch();
  }

  @Override
  public String getName() {
    return "RNDNSSD";
  }

  @ReactMethod
  public void startSearch(final String type, final String protocol) {
    String serviceType = String.format("_%s._%s", type, protocol);

    Log.d(TAG, "Search starting for " + serviceType + " in domain: local.");
    Disposable search = RxBonjour.startDiscovery(getReactApplicationContext(), serviceType)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Consumer<NetworkServiceDiscoveryInfo>() {
              @Override
              public void accept(NetworkServiceDiscoveryInfo info) throws Exception {
                WritableMap service = new WritableNativeMap();
                service.putString("name", info.getServiceName());
                service.putString("type", type);
                service.putString("domain", "local.");
                final InetAddress address = info.getAddress();

                service.putString("hostName", address != null ? info.getAddress().toString() : null);
                service.putInt("port", info.getServicePort());

                WritableMap txt = new WritableNativeMap();
                for (Map.Entry<String, byte[]> value : info.getAttributes().entrySet()) {
                  txt.putString(value.getKey(), new String(value.getValue(), Charset.forName("UTF-8")));
                }

                service.putMap("txt", txt);
                if (info.isAdded()) {
                  Log.d(TAG, "Service Found: " + info.toString());
                  sendEvent("serviceFound", service);
                } else {
                  Log.d(TAG, "Service Lost: " + info.toString());
                  sendEvent("serviceLost", service);
                }
              }
            },
            new Consumer<Throwable>() {
              @Override
              public void accept(Throwable e) throws Exception {
                Log.e(TAG, "error", e);
              }
            });

    searches.add(search);
  }

  @ReactMethod
  public void stopSearch() {
    Log.d(TAG, "Stop all searches");
    for (Disposable search: searches) {
      search.dispose();
    }
    searches.clear();
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    getReactApplicationContext()
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }
}