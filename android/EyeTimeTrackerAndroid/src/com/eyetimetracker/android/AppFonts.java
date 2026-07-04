package com.eyetimetracker.android;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class AppFonts {
    private static final String FONT_FILE_NAME = "NotoSansSC-VF.ttf";

    private static Typeface regular;
    private static Typeface bold;

    private AppFonts() {
    }

    public static Typeface regular(Context context) {
        if (regular == null) {
            regular = createWeighted(context, 500);
        }
        return regular;
    }

    public static Typeface bold(Context context) {
        if (bold == null) {
            bold = createWeighted(context, 800);
        }
        return bold;
    }

    public static void apply(TextView view, boolean isBold) {
        view.setTypeface(isBold ? bold(view.getContext()) : regular(view.getContext()));
    }

    private static Typeface createWeighted(Context context, int weight) {
        try {
            return new Typeface.Builder(copyFontResource(context))
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
        } catch (RuntimeException | IOException ex) {
            Typeface base = context.getResources().getFont(R.font.noto_sans_sc);
            return weight >= 700 ? Typeface.create(base, Typeface.BOLD) : base;
        }
    }

    private static File copyFontResource(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), "fonts");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Could not create font cache directory.");
        }

        File file = new File(directory, FONT_FILE_NAME);
        if (file.exists() && file.length() > 0) {
            return file;
        }

        try (InputStream input = context.getResources().openRawResource(R.font.noto_sans_sc);
             OutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }

        return file;
    }
}
