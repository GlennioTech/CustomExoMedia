package com.devbrackets.android.exomedia.core.source.builder;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.util.List;

public class SsMediaSourceBuilder extends MediaSourceBuilder {
    @NonNull
    @Override
    protected MediaSource buildInternal(@NonNull Context context, @NonNull Uri uri, @Nullable Uri audioUri, @Nullable List<Pair<String, String>> headers, @NonNull String userAgent, @NonNull Handler handler, @Nullable TransferListener<? super DataSource> transferListener) {
        DataSource.Factory dataSourceFactory = buildDataSourceFactory(context, headers, userAgent, null);
        DataSource.Factory meteredDataSourceFactory = buildDataSourceFactory(context, headers, userAgent, transferListener);

        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(meteredDataSourceFactory), dataSourceFactory)
                .createMediaSource(uri, handler, null);
    }
}
