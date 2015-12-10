package com.ai.tools.audiohog;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AudioHogService extends Service {
    public AudioHogService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
