package com.devbrackets.android.exomedia.core.source.builder;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.util.List;

public class HlsMediaSourceBuilder extends MediaSourceBuilder {
    @NonNull
    @Override
    protected MediaSource buildInternal(@NonNull Context context, @NonNull Uri uri, @Nullable Uri audioUri, @Nullable List<Pair<String, String>> headers, @NonNull String userAgent, @NonNull Handler handler, @Nullable TransferListener<? super DataSource> transferListener) {
        DataSource.Factory dataSourceFactory = buildDataSourceFactory(context, headers, userAgent, transferListener);
        if(audioUri!=null){
            return new MergingMediaSource(new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(uri, handler, null),
                    new HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(audioUri, handler, null));
        }
        return new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(uri, handler, null);
    }
}
