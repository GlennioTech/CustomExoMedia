package com.devbrackets.android.exomedia.core.source;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;

import com.devbrackets.android.exomedia.ExoMedia;
import com.devbrackets.android.exomedia.core.source.builder.DefaultMediaSourceBuilder;
import com.devbrackets.android.exomedia.core.source.builder.MediaSourceBuilder;
import com.devbrackets.android.exomedia.util.MediaSourceUtil;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.util.List;

/**
 * Provides the functionality to determine which {@link MediaSource} should be used
 * to play a particular URL.
 */
public class MediaSourceProvider {

    protected String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36";
    @NonNull
    public MediaSource generate(@NonNull Context context, @NonNull Handler handler, @NonNull Uri videoUri, @Nullable Uri audioUri, @Nullable List<Pair<String,String>> headers, @Nullable TransferListener<? super DataSource> transferListener ) {
        String extension = MediaSourceUtil.getExtension(videoUri);

        // Searches for a registered builder
        SourceTypeBuilder sourceTypeBuilder = findByExtension(extension);
        if (sourceTypeBuilder == null) {
            sourceTypeBuilder = findByLooseComparison(videoUri);
        }

        // If a registered builder wasn't found then use the default
        MediaSourceBuilder builder = sourceTypeBuilder != null ? sourceTypeBuilder.builder : new DefaultMediaSourceBuilder();
        return builder.build(context, videoUri,audioUri,headers, userAgent, handler, transferListener);
    }

    @Nullable
    protected static SourceTypeBuilder findByExtension(@Nullable String extension) {
        if (extension == null || extension.isEmpty()) {
            return null;
        }

        for (SourceTypeBuilder sourceTypeBuilder : ExoMedia.Data.sourceTypeBuilders) {
            if (sourceTypeBuilder.extension.equalsIgnoreCase(extension)) {
                return sourceTypeBuilder;
            }
        }

        return null;
    }

    @Nullable
    protected static SourceTypeBuilder findByLooseComparison(@NonNull Uri uri) {
        for (SourceTypeBuilder sourceTypeBuilder : ExoMedia.Data.sourceTypeBuilders) {
            if (sourceTypeBuilder.looseComparisonRegex != null && uri.toString().matches(sourceTypeBuilder.looseComparisonRegex)) {
                return sourceTypeBuilder;
            }
        }

        return null;
    }

    public static class SourceTypeBuilder {
        @NonNull
        public final MediaSourceBuilder builder;
        @NonNull
        public final String extension;
        @Nullable
        public final String looseComparisonRegex;

        public SourceTypeBuilder(@NonNull MediaSourceBuilder builder, @NonNull String extension, @Nullable String looseComparisonRegex) {
            this.builder = builder;
            this.extension = extension;
            this.looseComparisonRegex = looseComparisonRegex;
        }
    }
}
