package com.devbrackets.android.exomedia.ui.widget.subtitle;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.support.v7.widget.AppCompatTextView;
import android.text.Html;
import android.text.Spanned;
import android.util.AttributeSet;

import com.devbrackets.android.exomedia.ui.widget.VideoView;
import com.devbrackets.android.exomedia.util.regexp.Matcher;
import com.devbrackets.android.exomedia.util.regexp.Pattern;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.TreeMap;

public class CaptionsView extends AppCompatTextView implements Runnable {
    private static final String LINE_BREAK = "<br/>";
    private static final int UPDATE_INTERVAL = 50;
    private VideoView player;
    private TreeMap<Long, Line> track;
    private CMime mimeType;

    private CaptionsViewLoadListener captionsViewLoadListener;

    public interface CaptionsViewLoadListener {
        void onCaptionLoadSuccess(@Nullable String path, int resId);

        void onCaptionLoadFailed(Throwable error, @Nullable String path, int resId);
    }

    public CaptionsView(Context context) {
        super(context);
    }

    public CaptionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CaptionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }



    public static boolean isRemotePath(Uri path) {
        return (path.getScheme().equals("http") || path.getScheme().equals("https"));
    }

    public static String secondsToDuration(long seconds) {
        return String.format("%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                (seconds % 60)
        );
    }

    @Override
    public void run() {
        if (player != null && track != null) {
            long seconds = player.getCurrentPosition() / 1000;
            Spanned text = Html.fromHtml(getTimedText(player.getCurrentPosition()));
            setText(text);
            if(text.length()==0&&getAlpha()!=0)
                setAlpha(0f);
            if(text.length()>0&&getAlpha()!=1)
                setAlpha(1f);
        }
        postDelayed(this, UPDATE_INTERVAL);
    }

    private String getTimedText(long currentPosition) {
        String result = "";
        for (Map.Entry<Long, Line> entry : track.entrySet()) {
            if (currentPosition < entry.getKey()) break;
            if (currentPosition < entry.getValue().to) result = entry.getValue().text;
        }
        return result;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(this, 300);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(this);
    }

    public void setPlayer(VideoView player) {
        this.player = player;
    }

    public void setCaptionsViewLoadListener(CaptionsViewLoadListener listener) {
        this.captionsViewLoadListener = listener;
    }

    public void setCaptionsSource(@RawRes int ResID, CMime mime) {
        this.mimeType = mime;
        track = getSubtitleFile(ResID);
    }

    public void setCaptionsSource(@Nullable Uri path, CMime mime) {
        this.mimeType = mime;
        if (path == null) {
            track = new TreeMap<>();
            return;
        }
        if (isRemotePath(path)) {
            try {
                URL url = new URL(path.toString());
                getSubtitleFile(url);
            } catch (MalformedURLException | NullPointerException e) {
                if (captionsViewLoadListener != null) {
                    captionsViewLoadListener.onCaptionLoadFailed(e, path.toString(), 0);
                }
                e.printStackTrace();
            }
        } else {
            track = getSubtitleFile(path.toString());
        }

    }

    /////////////Utility Methods:
    //Based on https://github.com/sannies/mp4parser/
    //Apache 2.0 Licence at: https://github.com/sannies/mp4parser/blob/master/LICENSE

    public static TreeMap<Long, Line> parse(InputStream in, CMime mime) throws IOException {
        if (mime == CMime.SUBRIP) {
            return parseSrt(in);
        } else if (mime == CMime.WEBVTT) {
            return parseVtt(in);
        }

        return parseSrt(in);
    }

    private enum TrackParseState {
        NEW_TRACK,
        PARSED_CUE,
        PARSED_TIME,
    }

    public static TreeMap<Long, Line> parseSrt(InputStream is) throws IOException {
        LineNumberReader r = new LineNumberReader(new InputStreamReader(is, "UTF-8"));
        TreeMap<Long, Line> track = new TreeMap<>();
        String lineEntry;
        StringBuilder textStringBuilder = new StringBuilder();
        Line line = null;
        TrackParseState state = TrackParseState.NEW_TRACK;
        int lineNumber = 0;
        while ((lineEntry = r.readLine()) != null) {
            lineNumber++;
            if (state == TrackParseState.NEW_TRACK) {
                // Try to parse the cue number.
                if (lineEntry.isEmpty()) {
                    // empty string, move along.
                    continue;
                } else if (isInteger(lineEntry)) {
                    // We've reach a new cue.
                    state = TrackParseState.PARSED_CUE;
                    if (line != null && textStringBuilder.length() > 0) {
                        // Add the previous track.
                        String lineText = textStringBuilder.toString();
                        line.setText(lineText.substring(0, lineText.length() - LINE_BREAK.length()));
                        addTrack(track, line);
                        line = null;
                        textStringBuilder.setLength(0);
                    }
                    continue;
                } else {
                    if (textStringBuilder.length() > 0) {
                        textStringBuilder.append(lineEntry).append(LINE_BREAK);
                        continue;
                    }
                }
            }

            if (state == TrackParseState.PARSED_CUE) {
                String[] times = lineEntry.split("-->");
                if (times.length == 2) {
                    long startTime = parseSrt(times[0]);
                    long endTime = parseSrt(times[1]);
                    line = new Line(startTime, endTime);
                    state = TrackParseState.PARSED_TIME;
                    continue;
                }

            }

            if (state == TrackParseState.PARSED_TIME) {
                if (!lineEntry.isEmpty()) {
                    textStringBuilder.append(lineEntry).append(LINE_BREAK);
                } else {
                    state = TrackParseState.NEW_TRACK;
                }
            }
        }
        if (line != null && textStringBuilder.length() > 0) {
            String lineText = textStringBuilder.toString();
            line.setText(lineText.substring(0, lineText.length() - LINE_BREAK.length()));
            addTrack(track, line);
        }

        return track;
    }

    private static void addTrack(TreeMap<Long, Line> track, Line line) {
        track.put(line.from, line);
    }

    private static boolean isInteger(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 && s.charAt(i) == '-') {
                if (s.length() == 1) return false;
                else continue;
            }
            if (Character.digit(s.charAt(i), 10) < 0) return false;
        }
        return true;
    }

    private static long parseSrt(String in) {
        String[] timeSections = in.split(":");
        String[] secondAndMillisecond = timeSections[2].split(",");
        long hours = Long.parseLong(timeSections[0].trim());
        long minutes = Long.parseLong(timeSections[1].trim());
        long seconds = Long.parseLong(secondAndMillisecond[0].trim());
        long millies = Long.parseLong(secondAndMillisecond[1].trim());

        return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millies;

    }

    private static boolean isInvalid(String s) {
        return s == null || s.length() == 0;
    }

    public static TreeMap<Long, Line> parseVtt(InputStream is) throws IOException {
        TreeMap<Long, Line> track = new TreeMap<>();
        String input = "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        input = sb
                .toString();
        if (!isInvalid(input)) {
            String vtt = input.replaceAll("\r+", "");
            vtt = vtt.trim();
            String[] cues = vtt.split("\n\n");
            for (int i = 0; i < cues.length; i++) {
                String cue = cues[i];
                if (cue.toLowerCase().startsWith("webvtt"))
                    continue;
                Line parsedLine = parseVttLine(cue);
                if (parsedLine != null)
                    track.put(parsedLine.from, parsedLine);
            }
        }
        return track;
    }

    private static Line parseVttLine(String vttCue){
        if (!isInvalid(vttCue)) {
            vttCue = vttCue.replaceAll("<[a-zA-Z/][^>]*>", "");
            Matcher durationMatcher = Pattern.compile("(?s)(?<from>(?<d1>\\d+):(?<d2>\\d+):(?<d3>\\d+)(?:\\.(?<d4>\\d+))?)\\s*--?>\\s*(?<to>(?<d5>\\d+):(?<d6>\\d+):(?<d7>\\d+)(?:\\.(?<d8>\\d+))?)[^\n]*?\n(?<sub>.+)").matcher(vttCue);
            if (durationMatcher.find()) {
                long from = parseVtt(durationMatcher.group("from"));
                long to  = parseVtt(durationMatcher.group("to"));
                String sub = durationMatcher.group("sub");
                return new Line(from,to,sub);
            }

        }
        return null;
    }

    private static long parseVtt(String in) {
        String[] timeUnits = in.split(":");
        boolean hoursAvailable = timeUnits.length == 3;
        if (hoursAvailable) {
            String[] secondAndMillisecond = timeUnits[2].split("\\.");
            long hours = Long.parseLong(timeUnits[0].trim());
            long minutes = Long.parseLong(timeUnits[1].trim());
            long seconds = Long.parseLong(secondAndMillisecond[0].trim());
            long millies = Long.parseLong(secondAndMillisecond[1].trim());
            return hours * 60 * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000 + millies;
        } else {
            String[] secondAndMillisecond = timeUnits[1].split("\\.");
            long minutes = Long.parseLong(timeUnits[0].trim());
            long seconds = Long.parseLong(secondAndMillisecond[0].trim());
            long millies = Long.parseLong(secondAndMillisecond[1].trim());
            return minutes * 60 * 1000 + seconds * 1000 + millies;
        }

    }

    private TreeMap<Long, Line> getSubtitleFile(String path) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File(path));
            TreeMap<Long, Line> tracks = parse(inputStream, mimeType);
            if (captionsViewLoadListener != null) {
                captionsViewLoadListener.onCaptionLoadSuccess(path, 0);
            }
            return tracks;
        } catch (Exception e) {
            if (captionsViewLoadListener != null) {
                captionsViewLoadListener.onCaptionLoadFailed(e, path, 0);
            }
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private TreeMap<Long, Line> getSubtitleFile(int resId) {
        InputStream inputStream = null;
        try {
            inputStream = getResources().openRawResource(resId);
            TreeMap<Long, Line> result = parse(inputStream, mimeType);
            if (captionsViewLoadListener != null) {
                captionsViewLoadListener.onCaptionLoadSuccess(null, resId);
            }
            return result;
        } catch (Exception e) {
            if (captionsViewLoadListener != null) {
                captionsViewLoadListener.onCaptionLoadFailed(e, null, resId);
            }
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private void getSubtitleFile(final URL url) {
        DownloadFile downloader = new DownloadFile(getContext(), new DownloadCallback() {
            @Override
            public void onDownload(File file) {
                try {
                    track = getSubtitleFile(file.getPath());
                } catch (Exception e) {
                    if (captionsViewLoadListener != null) {
                        captionsViewLoadListener.onCaptionLoadFailed(e, url.toString(), 0);
                    }
                }
            }

            @Override
            public void onFail(Exception e) {
                if (captionsViewLoadListener != null) {
                    captionsViewLoadListener.onCaptionLoadFailed(e, url.toString(), 0);
                }
            }
        });
        downloader.execute(url.toString(), "subtitle.srt");
    }

    public static class Line {
        long from;
        long to;
        String text;

        public Line(long from, long to, String text) {
            this.from = from;
            this.to = to;
            this.text = text;
        }

        public Line(long from, long to) {
            this.from = from;
            this.to = to;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public enum CMime {
        SUBRIP, WEBVTT
    }
}